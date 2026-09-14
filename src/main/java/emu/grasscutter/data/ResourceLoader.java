package emu.grasscutter.data;

import static emu.grasscutter.utils.FileUtils.*;
import static emu.grasscutter.utils.lang.Language.translate;

import com.google.gson.annotations.SerializedName;
import com.google.gson.reflect.TypeToken;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.binout.*;
import emu.grasscutter.data.binout.AbilityModifier.AbilityModifierAction;
import emu.grasscutter.data.binout.config.*;
import emu.grasscutter.data.binout.routes.*;
import emu.grasscutter.data.common.PointData;
import emu.grasscutter.data.custom.*;
import emu.grasscutter.game.dungeons.DungeonDrop;
import emu.grasscutter.data.excels.trial.TrialAvatarActivityDataData;
import emu.grasscutter.data.server.*;
import emu.grasscutter.game.activity.ActivityManager;
import emu.grasscutter.game.managers.blossom.BlossomConfig;
import emu.grasscutter.game.quest.*;
import emu.grasscutter.game.world.*;
import emu.grasscutter.game.world.SpawnDataEntry.*;
import emu.grasscutter.scripts.*;
import emu.grasscutter.utils.*;
import it.unimi.dsi.fastutil.Pair;
import it.unimi.dsi.fastutil.ints.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;
import java.util.Map.Entry;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;
import java.util.stream.*;
import javax.script.*;
import lombok.*;

public final class ResourceLoader {

    /** Timings of the loading stages, used to report the slowest ones after loading finishes. */
    private static final Map<String, Long> stageTimings = new ConcurrentHashMap<>();

    /** Timings of the individual excel resources, used to spot slow files. */
    private static final Map<String, Long> excelTimings = new ConcurrentHashMap<>();

    private static boolean loadedAll = false;

    /**
     * Pool used to load independent resource stages in parallel. Resource loading is mostly CPU
     * bound (JSON parsing), so the pool is sized to the CPU core count.
     */
    private static final ExecutorService resourceExecutor =
            Executors.newFixedThreadPool(
                    Math.max(4, Runtime.getRuntime().availableProcessors()),
                    newResourceThreadFactory());

    /**
     * The Lua engine (and {@link ScriptLoader#eval}) is not thread-safe - it shares global
     * bindings - so every script based loader runs on this single dedicated thread. This still
     * lets the script loaders overlap with all of the non-script loaders.
     */
    private static final ExecutorService luaExecutor =
            Executors.newSingleThreadExecutor(newResourceThreadFactory());

    private static final AtomicInteger resourceThreadCounter = new AtomicInteger();

    private static ThreadFactory newResourceThreadFactory() {
        return task -> {
            val thread =
                    new Thread(task, "Resource Loader-" + resourceThreadCounter.incrementAndGet());
            thread.setDaemon(true);
            return thread;
        };
    }

    /** Wraps a loading stage so a failure logs an error instead of breaking the parallel load. */
    private static Runnable safeStage(String name, Runnable task) {
        return () -> {
            long startTime = System.nanoTime();
            try {
                task.run();
            } catch (Throwable t) {
                Grasscutter.getLogger().error("Error while loading resources - stage: " + name, t);
            }

            long took = (System.nanoTime() - startTime) / 1_000_000;
            stageTimings.put(name, took);
            Grasscutter.getLogger().debug("Resource stage {} took {}ms", name, took);
        };
    }

    /** Logs the slowest entries of a timing map - this makes bottlenecks easy to spot. */
    private static void logSlowest(String label, Map<String, Long> timings, int limit) {
        if (timings.isEmpty()) return;

        val formatted =
                timings.entrySet().stream()
                        .sorted((a, b) -> Long.compare(b.getValue(), a.getValue()))
                        .limit(limit)
                        .map(entry -> entry.getKey() + "=" + entry.getValue() + "ms")
                        .collect(Collectors.joining(", "));
        Grasscutter.getLogger().info(label + " (top {}): {}", limit, formatted);
    }

    /** Runs a resource loading stage on the parallel loader pool. */
    private static CompletableFuture<Void> runStage(String name, Runnable task) {
        return CompletableFuture.runAsync(safeStage(name, task), resourceExecutor);
    }

    /** Runs a resource loading stage as soon as the given dependency stage has finished. */
    private static CompletableFuture<Void> afterStage(
            CompletableFuture<?> dependency, String name, Runnable task) {
        return dependency.thenRun(safeStage(name, task));
    }

    /** Collects the entries of a directory into an ordered list of paths. */
    private static List<Path> listDirectory(String folder, String glob) throws IOException {
        try (val stream = Files.newDirectoryStream(getResourcePath(folder), glob)) {
            return StreamSupport.stream(stream.spliterator(), false).toList();
        }
    }

    /** A parser that turns one resource file into a value (or null to skip the file). */
    @FunctionalInterface
    private interface FileParser<R> {
        R parse(Path path) throws Exception;
    }

    /**
     * Parses the given files in parallel on the common ForkJoinPool. The returned list preserves
     * the file order, so merging it serially afterwards is deterministic - this keeps every write
     * to GameData's (non thread-safe) maps on the calling thread.
     */
    private static <R> List<R> parseFilesInParallel(List<Path> files, FileParser<R> parser) {
        return files.stream()
                .parallel()
                .map(
                        path -> {
                            try {
                                return parser.parse(path);
                            } catch (Exception e) {
                                Grasscutter.getLogger()
                                        .error("Error parsing resource file " + path + ": ", e);
                                return null;
                            }
                        })
                .filter(Objects::nonNull)
                .toList();
    }


    private static List<Set<Class<?>>> getResourceDefClassesPrioritySets() {
        val classes = Grasscutter.reflector.getSubTypesOf(GameResource.class);
        val priorities = ResourceType.LoadPriority.getInOrder();
        Grasscutter.getLogger().debug("Priorities are " + priorities);
        val map = new LinkedHashMap<ResourceType.LoadPriority, Set<Class<?>>>(priorities.size());
        priorities.forEach(p -> map.put(p, new HashSet<>()));

        classes.forEach(
                c -> {

                    val annotation = c.getAnnotation(ResourceType.class);
                    if (annotation != null) {
                        map.get(annotation.loadPriority()).add(c);
                    }
                });
        return List.copyOf(map.values());
    }

    @SneakyThrows
    public static void loadAll() {
        if (loadedAll) return;
        long startTime = System.nanoTime();
        Grasscutter.getLogger().info(translate("messages.status.resources.loading"));

        // The script engine must exist before any script based stage can run.
        ScriptLoader.init();

        // ---- Independent stages: all of these are loaded at the same time. ----
        val configData = runStage("ConfigData", ResourceLoader::loadConfigData);
        val abilityEmbryos = runStage("AbilityEmbryos", ResourceLoader::loadAbilityEmbryos);
        val talents = runStage("Talents", ResourceLoader::loadTalents);
        val openConfig = runStage("OpenConfig", ResourceLoader::loadOpenConfig);
        val abilityModifiers = runStage("AbilityModifiers", ResourceLoader::loadAbilityModifiers);
        val excels = runStage("ExcelResources", ResourceLoader::loadResources);
        val dungeonDrops = runStage("DungeonDrops", ResourceLoader::loadDungeonDropData);
        val spawns = runStage("SpawnData", ResourceLoader::loadSpawnData);
        val quests = runStage("Quests", ResourceLoader::loadQuests);
        val scriptSceneData = runStage("ScriptSceneData", ResourceLoader::loadScriptSceneData);
        val homeworldData =
                runStage("HomeworldDefaultSaveData", ResourceLoader::loadHomeworldDefaultSaveData);
        val npcBorn = runStage("NpcBornData", ResourceLoader::loadNpcBornData);
        val routes = runStage("Routes", ResourceLoader::loadRoutes);
        val blossom = runStage("BlossomResources", ResourceLoader::loadBlossomResources);
        val levelEntity = runStage("ConfigLevelEntity", ResourceLoader::loadConfigLevelEntityData);
        val gadgetMappings = runStage("GadgetMappings", ResourceLoader::loadGadgetMappings);
        val subfieldMappings = runStage("SubfieldMappings", ResourceLoader::loadSubfieldMappings);
        val monsterMappings = runStage("MonsterMappings", ResourceLoader::loadMonsterMappings);
        val activityCondGroups =
                runStage("ActivityCondGroups", ResourceLoader::loadActivityCondGroups);
        val globalCombat = runStage("GlobalCombatConfig", ResourceLoader::loadGlobalCombatConfig);

        // ---- Script based stages: serialized onto the Lua thread, but running concurrently
        //      with all of the file loaders above. ----
        val scriptStage =
                CompletableFuture.runAsync(
                        () -> {
                            loadQuestShareConfig();
                            loadGroupReplacements();
                            EntityControllerScriptManager.load();
                        },
                        luaExecutor);

        // ---- Dependent stages: scheduled as soon as their inputs are ready. ----
        val mergedEmbryos =
                CompletableFuture.allOf(abilityEmbryos, abilityModifiers)
                        .thenRun(
                                safeStage(
                                        "MergeDynamicAbilities",
                                        ResourceLoader::mergeDynamicAbilitiesIntoEmbryos));

        val talentVarMaps =
                CompletableFuture.allOf(excels, openConfig)
                        .thenRun(
                                safeStage(
                                        "AbilityTalentVarMaps",
                                        ResourceLoader::buildAbilityTalentVarMaps));

        // Scene points resolve daily dungeon lists from the excel data, so this
        // stage waits for the excel resources.
        val scenePoints = afterStage(excels, "ScenePoints", ResourceLoader::loadScenePoints);
        val gameDepot = afterStage(excels, "GameDepot", GameDepot::load);
        val talentLevels = afterStage(excels, "TalentLevelSets", ResourceLoader::cacheTalentLevelSets);
        val activityConfig =
                afterStage(excels, "ActivityConfig", ActivityManager::loadActivityConfigData);
        val trialAvatars =
                afterStage(excels, "TrialAvatarCustomData", ResourceLoader::loadTrialAvatarCustomData);

        // ---- Wait for every stage to finish before declaring the resources loaded. ----
        CompletableFuture.allOf(
                        configData,
                        abilityEmbryos,
                        talents,
                        openConfig,
                        abilityModifiers,
                        excels,
                        dungeonDrops,
                        spawns,
                        quests,
                        scriptSceneData,
                        homeworldData,
                        npcBorn,
                        routes,
                        blossom,
                        levelEntity,
                        gadgetMappings,
                        subfieldMappings,
                        monsterMappings,
                        activityCondGroups,
                        globalCombat,
                        scriptStage,
                        mergedEmbryos,
                        talentVarMaps,
                        scenePoints,
                        gameDepot,
                        talentLevels,
                        activityConfig,
                        trialAvatars)
                .join();

        loadedAll = true;

        long endTime = System.nanoTime();
        long ns = (endTime - startTime);
        Grasscutter.getLogger()
                .info(translate("messages.status.resources.finish") + " (" + ns / 1000000 + "ms)");
        logSlowest("Slowest resource stages", stageTimings, 10);
    }

    public static void loadResources() {
        long startTime = System.nanoTime();
        val errors = new ConcurrentLinkedQueue<Pair<String, Exception>>();

        getResourceDefClassesPrioritySets()
                .forEach(
                        classes -> {
                            classes.stream()
                                    .parallel()
                                    .unordered()
                                    .forEach(
                                            c -> {
                                                val type = c.getAnnotation(ResourceType.class);
                                                if (type == null) return;

                                                val map = GameData.getMapByResourceDef(c);
                                                if (map == null) return;

                                                try {
                                                    loadFromResource(c, type, map);
                                                } catch (Exception e) {
                                                    errors.add(Pair.of(Arrays.toString(type.name()), e));
                                                }
                                            });
                        });
        errors.forEach(
                pair ->
                        Grasscutter.getLogger()
                                .error("Error loading resource file: " + pair.left(), pair.right()));
        long endTime = System.nanoTime();
        long ns = (endTime - startTime);
        Grasscutter.getLogger()
                .debug("Loading resources took " + ns + "ns == " + ns / 1000000 + "ms");
        logSlowest("Slowest excel resources", excelTimings, 8);
    }
	
	private static void loadDungeonDropData() {
		var dungeonDropMap = GameData.getDungeonDropDataMap();
		dungeonDropMap.clear();

		try {
			var dungeonDrops =
					DataLoader.loadList("DungeonDrop.json", DungeonDrop.class);

			for (var dungeonDrop : dungeonDrops) {
				if (dungeonDrop == null
						|| dungeonDrop.getDungeonId() <= 0
						|| dungeonDrop.getDrops() == null
						|| dungeonDrop.getDrops().isEmpty()) {
					continue;
				}

				dungeonDropMap.put(
						dungeonDrop.getDungeonId(),
						dungeonDrop.getDrops());
			}
		} catch (Exception e) {
			Grasscutter.getLogger()
					.error("Unable to load DungeonDrop.json.", e);
		}
	}

    @SuppressWarnings("rawtypes")
    protected static void loadFromResource(Class<?> c, ResourceType type, Int2ObjectMap map)
            throws Exception {
        long startTime = System.nanoTime();
        for (String name : type.name()) {
            loadFromResource(c, FileUtils.getExcelPath(name), map);
        }
        excelTimings.put(c.getSimpleName(), (System.nanoTime() - startTime) / 1_000_000);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    protected static <T> void loadFromResource(Class<T> c, Path filename, Int2ObjectMap map)
            throws Exception {
        val results =
                switch (FileUtils.getFileExtension(filename)) {
                    // Large excel files are parsed in parallel by splitting the JSON array.
                    case "json" -> ParallelJsonArrayLoader.loadList(filename, c);
                    case "tsj" -> TsvUtils.loadTsjToListSetField(filename, c);
                    case "tsv" -> TsvUtils.loadTsvToListSetField(filename, c);
                    default -> null;
                };
        if (results == null) return;
        results.forEach(
                o -> {
                    GameResource res = (GameResource) o;
                    res.onLoad();
                    map.put(res.getId(), res);
                });
    }

    private static void loadGlobalCombatConfig() {
        try {
            GameData.setConfigGlobalCombat(
                    JsonUtils.loadToClass(
                            getResourcePath("BinOutput/Common/ConfigGlobalCombat.json"),
                            ConfigGlobalCombat.class));
        } catch (IOException e) {
            Grasscutter.getLogger()
                    .error("Cannot load ConfigGlobalCombat.json, this error is important, fix it!");
        }
    }

    private static void loadScenePoints() {
        val pattern = Pattern.compile("scene([0-9]+)_point\\.json");
        List<Path> files;
        try {
            files = listDirectory("BinOutput/Scene/Point", "scene*_point.json");
        } catch (IOException ignored) {
            Grasscutter.getLogger()
                    .error("Scene point files cannot be found, you cannot use teleport waypoints!");
            return;
        }

        // Parse the scene point files in parallel. This stage runs after the excel
        // resources have loaded - updateDailyDungeon reads DailyDungeonConfigData.
        val parsed =
                parseFilesInParallel(
                        files,
                        path -> {
                            val matcher = pattern.matcher(path.getFileName().toString());
                            if (!matcher.find()) return null;

                            ScenePointConfig config;
                            try {
                                config = JsonUtils.loadToClass(path, ScenePointConfig.class);
                            } catch (Exception e) {
                                e.printStackTrace();
                                return null;
                            }

                            if (config.points == null) return null;
                            return Map.entry(Integer.parseInt(matcher.group(1)), config);
                        });

        // Merge serially - GameData's maps are not thread-safe.
        parsed.forEach(
                entry -> {
                    int sceneId = entry.getKey();
                    val config = entry.getValue();

                    val scenePoints = new IntArrayList();
                    config.points.forEach(
                            (pointId, pointData) -> {
                                val scenePoint = new ScenePointEntry(sceneId, pointData);
                                scenePoints.add((int) pointId);
                                pointData.setId(pointId);

                                GameData.getScenePointIdList().add((int) pointId);
                                GameData.getScenePointEntryMap().put((sceneId << 16) + pointId, scenePoint);

                                pointData.updateDailyDungeon();
                            });
                    GameData.getScenePointsPerScene().put(sceneId, scenePoints);
                });
    }

    private static void loadRoutes() {
        List<Path> files;
        try {
            files = listDirectory("BinOutput/LevelDesign/Routes/", "*.json");
        } catch (IOException e) {
            Grasscutter.getLogger().error("Failed to load SceneRouteData folder.");
            return;
        }

        val parsed = parseFilesInParallel(files, path -> JsonUtils.loadToClass(path, SceneRoutes.class));
        // Merge serially - multiple files can contribute routes to the same scene.
        parsed.forEach(
                data -> {
                    val routesArray = data.getRoutes();
                    if (routesArray == null) return;
                    val routesMap =
                            GameData.getSceneRouteData()
                                    .getOrDefault(data.getSceneId(), new Int2ObjectOpenHashMap<>());
                    for (Route route : routesArray) {
                        routesMap.put(route.getLocalId(), route);
                    }
                    GameData.getSceneRouteData().put(data.getSceneId(), routesMap);
                });
        Grasscutter.getLogger()
                .debug("Loaded " + GameData.getSceneRouteData().size() + " SceneRouteDatas.");
    }

    private static void cacheTalentLevelSets() {

        GameData.getProudSkillDataMap()
                .forEach(
                        (id, data) ->
                                GameData.getProudSkillGroupLevels()
                                        .computeIfAbsent(data.getProudSkillGroupId(), i -> new IntArraySet())
                                        .add(data.getLevel()));

        GameData.getAvatarSkillDataMap()
                .forEach(
                        (id, data) ->
                                GameData.getAvatarSkillLevels()
                                        .put(
                                                (int) id,
                                                GameData.getProudSkillGroupLevels().get(data.getProudSkillGroupId())));

        GameData.getProudSkillGroupLevels()
                .forEach(
                        (id, set) ->
                                GameData.getProudSkillGroupMaxLevels()
                                        .put((int) id, set.intStream().max().orElse(-1)));
    }

    private static void loadAbilityEmbryos() {
        List<AbilityEmbryoEntry> embryoList = null;

        try {
            embryoList =
                    JsonUtils.loadToList(getDataPath("AbilityEmbryos.json"), AbilityEmbryoEntry.class);
        } catch (Exception ignored) {
        }

        if (embryoList == null) {

            var pattern = Pattern.compile("ConfigAvatar_(.+?)\\.json");

            List<AbilityEmbryoEntry> entries;
            try {
                val files = listDirectory("BinOutput/Avatar/", "ConfigAvatar_*.json");
                entries =
                        parseFilesInParallel(
                                files,
                                path -> {
                                    var matcher = pattern.matcher(path.getFileName().toString());
                                    if (!matcher.find()) return null;

                                    AvatarConfig config;
                                    try {
                                        config = JsonUtils.loadToClass(path, AvatarConfig.class);
                                    } catch (Exception e) {
                                        Grasscutter.getLogger()
                                                .error("Error loading player ability embryos:", e);
                                        return null;
                                    }

                                    if (config.abilities == null) return null;

                                    return new AbilityEmbryoEntry(
                                            matcher.group(1),
                                            config.abilities.stream()
                                                    .map(Object::toString)
                                                    .toArray(
                                                            size ->
                                                                    new String[config
                                                                            .abilities
                                                                            .size()]));
                                });
            } catch (IOException e) {
                Grasscutter.getLogger().error("Error loading ability embryos: no files found");
                return;
            }

            embryoList = entries;

            try {
                GameDepot.setPlayerAbilities(
                        JsonUtils.loadToMap(
                                getResourcePath(
                                        "BinOutput/AbilityGroup/AbilityGroup_Other_PlayerElementAbility.json"),
                                String.class,
                                AvatarConfig.class));
            } catch (IOException e) {
                Grasscutter.getLogger().error("Error loading player abilities:", e);
            }
        }

        // Load the fishing ability groups (applied to avatars while fishing at a pool).
        try {
            var fishingGroups =
                    JsonUtils.loadToMap(
                            getResourcePath("BinOutput/AbilityGroup/AbilityGroup_Fishing.json"),
                            String.class,
                            AvatarConfig.class);
            var merged = new HashMap<>(GameDepot.getPlayerAbilities());
            merged.putAll(fishingGroups);
            GameDepot.setPlayerAbilities(merged);
        } catch (Exception e) {
            Grasscutter.getLogger().error("Error loading fishing ability group:", e);
        }

        if (embryoList == null || embryoList.isEmpty()) {
            Grasscutter.getLogger().error("No embryos loaded!");
            return;
        }

        for (AbilityEmbryoEntry entry : embryoList) {
            GameData.getAbilityEmbryoInfo().put(entry.getName(), entry);
        }
    }

    private static void loadAbilityModifiers() {
        List<Path> files;
        try (Stream<Path> paths = Files.walk(getResourcePath("BinOutput/Ability/Temp/"))) {
            files =
                    paths.filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(".json"))
                            .toList();
        } catch (IOException e) {
            Grasscutter.getLogger().error("Error loading ability modifiers: ", e);
            return;
        }

        // Parse the (thousands of) files in parallel, then merge the results into
        // GameData's maps serially to keep the writes on a single thread.
        val parsed = parseFilesInParallel(files, ResourceLoader::parseAbilityModifiers);
        parsed.forEach(list -> list.forEach(ResourceLoader::loadAbilityData));
    }

    /** Parses one ability modifier file without touching any shared state. */
    private static List<AbilityData> parseAbilityModifiers(Path path) {
        try {
            val dataList = JsonUtils.loadToList(path, AbilityConfigData.class);
            if (dataList == null) return List.of();

            return dataList.stream()
                    .filter(data -> data.Default != null)
                    .peek(
                            data ->
                                    data.Default.isDynamicAbility =
                                            data.Default.isDynamicAbility || data.isDynamicAbility)
                    .map(data -> data.Default)
                    .toList();
        } catch (Exception e) {
            Grasscutter.getLogger()
                    .error("Error loading ability modifiers from path " + path.toString() + ": ", e);
            return List.of();
        }
    }

    private static void mergeDynamicAbilitiesIntoEmbryos() {

    for (Map.Entry<String, AbilityEmbryoEntry> entry : GameData.getAbilityEmbryoInfo().entrySet()) {

        String avatarName = entry.getKey();

        AbilityEmbryoEntry embryo = entry.getValue();

        List<String> mergedAbilities = new ArrayList<>(Arrays.asList(embryo.getAbilities()));

        for (AbilityData abilityData : GameData.getAbilityDataMap().values()) {
            if (!abilityData.isDynamicAbility) {
                continue;
            }

            if (abilityData.abilityName.startsWith("Avatar_" + avatarName)) {
                if (!mergedAbilities.contains(abilityData.abilityName)) {
                    mergedAbilities.add(abilityData.abilityName);
                }
            }
        }

        AbilityEmbryoEntry mergedEntry = new AbilityEmbryoEntry(
            embryo.getName(),
            mergedAbilities.toArray(new String[mergedAbilities.size()])
        );

        GameData.getAbilityEmbryoInfo().put(avatarName, mergedEntry);
    }
}

    private static void loadAbilityData(AbilityData data) {
        GameData.getAbilityDataMap().put(data.abilityName, data);
        GameData.getAbilityHashes().put(Utils.abilityHash(data.abilityName), data.abilityName);

        var modifiers = data.modifiers;
        if (modifiers == null || modifiers.size() == 0) return;

        var name = data.abilityName;
        var modifierEntry = new AbilityModifierEntry(name);
        modifiers.forEach(
                (key, modifier) -> {
                    Stream.ofNullable(modifier.onAdded)
                            .flatMap(Stream::of)

                            .filter(action -> action.type == AbilityModifierAction.Type.HealHP)
                            .forEach(action -> modifierEntry.getOnAdded().add(action));
                    Stream.ofNullable(modifier.onThinkInterval)
                            .flatMap(Stream::of)

                            .filter(action -> action.type == AbilityModifierAction.Type.HealHP)
                            .forEach(action -> modifierEntry.getOnThinkInterval().add(action));
                    Stream.ofNullable(modifier.onRemoved)
                            .flatMap(Stream::of)

                            .filter(action -> action.type == AbilityModifierAction.Type.HealHP)
                            .forEach(action -> modifierEntry.getOnRemoved().add(action));
                });
    }

    private static void loadTalents() {
        List<Path> files;
        try (var paths = Files.walk(getResourcePath("BinOutput/Talent/AvatarTalents/"))) {
            files =
                    paths.filter(Files::isRegularFile)
                            .filter(path -> path.toString().endsWith(".json"))
                            .toList();
        } catch (IOException e) {
            Grasscutter.getLogger().error("Error loading talents: ", e);
            return;
        }

        // Parse the files in parallel, then merge the results serially - the
        // talents map is not thread-safe.
        val parsed =
                parseFilesInParallel(
                        files,
                        path -> {
                            Map<String, List<TalentData>> talents =
                                    JsonUtils.loadToMap(
                                            path,
                                            String.class,
                                            new TypeToken<List<TalentData>>() {}.getType());
                            return talents;
                        });
        parsed.forEach(talents -> GameData.getTalents().putAll(talents));
    }

	private static void loadSpawnData() {
		String[] spawnDataNames = {"Spawns.json", "GadgetSpawns.json", "CustomSpawns.json"};
		ArrayList<SpawnGroupEntry> spawnEntryMap = new ArrayList<>();

		for (String name : spawnDataNames) {

			try (InputStreamReader reader = DataLoader.loadReader(name)) {

				spawnEntryMap.addAll(JsonUtils.loadToList(reader, SpawnGroupEntry.class));
			} catch (Exception ignored) {
			}
		}
		if (spawnEntryMap.isEmpty()) {
			Grasscutter.getLogger().error("No spawn data loaded!");
			return;
		}

		HashMap<GridBlockId, ArrayList<SpawnDataEntry>> areaSort = new HashMap<>();
		for (SpawnGroupEntry entry : spawnEntryMap) {
			entry
					.getSpawns()
					.forEach(
							s -> {
								s.setGroup(entry);
								GridBlockId point = s.getBlockId();
								if (!areaSort.containsKey(point)) {
									areaSort.put(point, new ArrayList<>());
								}
								areaSort.get(point).add(s);
							});
		}
		GameDepot.addSpawnListById(areaSort);
	}

    private static void buildAbilityTalentVarMaps() {
        var abilityTalentVarMap = GameData.getAbilityTalentVarMap();
        var openConfigToGroup = GameData.getOpenConfigToProudSkillGroup();

        for (var proudSkill : GameData.getProudSkillDataMap().values()) {
            if (proudSkill.getOpenConfig() != null && !proudSkill.getOpenConfig().isEmpty()) {
                openConfigToGroup.put(proudSkill.getOpenConfig(), proudSkill.getProudSkillGroupId());
            }
        }

        var varNameMap = GameData.getVarNameToTalentVars();
        Set<String> addedCombos = new java.util.HashSet<>();
        for (var entry : GameData.getOpenConfigEntries().entrySet()) {
            var configEntry = entry.getValue();
            if (configEntry.getAbilityVarSetters() == null) continue;
            for (var setter : configEntry.getAbilityVarSetters()) {
                if (setter.getAbilityName() == null || setter.getVarName() == null) continue;
                var tv = new GameData.AbilityTalentVar(configEntry.getName(), setter.getVarName(), setter.getParamIndex());
                abilityTalentVarMap.computeIfAbsent(setter.getAbilityName(), k -> new java.util.ArrayList<>()).add(tv);

                String combo = configEntry.getName() + "|" + setter.getVarName() + "|" + setter.getParamIndex();
                if (addedCombos.add(combo)) {
                    varNameMap.computeIfAbsent(setter.getVarName(), k -> new java.util.ArrayList<>()).add(tv);
                }
            }
        }
    }

    private static void loadOpenConfig() {

        List<OpenConfigEntry> list = null;

        try {
            list = JsonUtils.loadToList(getDataPath("OpenConfig.json"), OpenConfigEntry.class);
        } catch (Exception ignored) {
        }

        if (list == null) {
            Map<String, OpenConfigEntry> map = new TreeMap<>();
            String[] folderNames = {
                "BinOutput/Talent/EquipTalents/",
                "BinOutput/Talent/AvatarTalents/",
                "BinOutput/Talent/RelicTalents/"
            };

            for (String folderName : folderNames) {
                try {
                    val files = listDirectory(folderName, "*.json");
                    // Parse in parallel; merge in file order so duplicate names keep
                    // the same precedence as a serial load.
                    val parsed =
                            parseFilesInParallel(
                                    files, path -> JsonUtils.loadToMap(path, String.class, OpenConfigData[].class));
                    parsed.forEach(
                            entries ->
                                    entries.forEach(
                                            (name, data) -> map.put(name, new OpenConfigEntry(name, data))));
                } catch (IOException e) {
                    Grasscutter.getLogger()
                            .error("Error loading open config: no files found in " + folderName);
                    return;
                }
            }

            list = new ArrayList<>(map.values());
        }

        if (list == null || list.isEmpty()) {
            Grasscutter.getLogger().error("No openconfig entries loaded!");
            return;
        }

        for (OpenConfigEntry entry : list) {
            GameData.getOpenConfigEntries().put(entry.getName(), entry);
        }
    }

    private static void loadQuests() {
        List<Path> files;
        try (var stream = Files.list(getResourcePath("BinOutput/Quest/"))) {
            files = stream.toList();
        } catch (IOException e) {
            Grasscutter.getLogger().error("Quest data missing");
            return;
        }

        // Parse the quest files in parallel, then merge the results serially - the
        // main quest map is not thread-safe.
        val parsed = parseFilesInParallel(files, path -> JsonUtils.loadToClass(path, MainQuestData.class));
        parsed.forEach(
                mainQuest -> {
                    GameData.getMainQuestDataMap().put(mainQuest.getId(), mainQuest);
                    mainQuest.onLoad();
                });

        try {
            val questEncryptionMap = GameData.getMainQuestEncryptionMap();
            var path = "QuestEncryptionKeys.json";
            try {
                JsonUtils.loadToList(getResourcePath(path), QuestEncryptionKey.class)
                        .forEach(key -> questEncryptionMap.put(key.getMainQuestId(), key));
            } catch (IOException | NullPointerException ignored) {
            }

            try {
                DataLoader.loadList(path, QuestEncryptionKey.class)
                        .forEach(key -> questEncryptionMap.put(key.getMainQuestId(), key));
            } catch (IOException | NullPointerException ignored) {
            }

            Grasscutter.getLogger().debug("Loaded {} quest keys.", questEncryptionMap.size());
        } catch (Exception e) {
            Grasscutter.getLogger().error("Unable to load quest keys.", e);
        }

        Grasscutter.getLogger()
                .debug("Loaded " + GameData.getMainQuestDataMap().size() + " MainQuestDatas.");
    }

    public static void loadScriptSceneData() {
        List<Path> files;
        try (val stream = Files.list(getResourcePath("ScriptSceneData/"))) {
            files = stream.toList();
        } catch (IOException e) {
            Grasscutter.getLogger().debug("ScriptSceneData folder missing or empty.");
            return;
        }

        val parsed =
                parseFilesInParallel(
                        files,
                        path -> {
                            val data = JsonUtils.loadToClass(path, ScriptSceneData.class);
                            if (data == null) return null;
                            return Map.entry(path.getFileName().toString(), data);
                        });
        parsed.forEach(
                entry -> GameData.getScriptSceneDataMap().put(entry.getKey(), entry.getValue()));
        Grasscutter.getLogger()
                .debug("Loaded " + GameData.getScriptSceneDataMap().size() + " ScriptSceneDatas.");
    }

    private static void loadHomeworldDefaultSaveData() {
        val pattern = Pattern.compile("scene([0-9]+)_home_config\\.json");
        List<Path> files;
        try {
            files = listDirectory("BinOutput/HomeworldDefaultSave", "scene*_home_config.json");
        } catch (IOException e) {
            Grasscutter.getLogger().error("Failed to load HomeworldDefaultSave folder.");
            return;
        }

        val parsed =
                parseFilesInParallel(
                        files,
                        path -> {
                            val matcher = pattern.matcher(path.getFileName().toString());
                            if (!matcher.find()) return null;

                            try {
                                val sceneId = Integer.parseInt(matcher.group(1));
                                val data = JsonUtils.loadToClass(path, HomeworldDefaultSaveData.class);
                                if (data == null) return null;
                                return Map.entry(sceneId, data);
                            } catch (Exception ignored) {
                                return null;
                            }
                        });
        parsed.forEach(
                entry -> GameData.getHomeworldDefaultSaveData().put(entry.getKey(), entry.getValue()));
        Grasscutter.getLogger()
                .debug(
                        "Loaded "
                                + GameData.getHomeworldDefaultSaveData().size()
                                + " HomeworldDefaultSaveDatas.");
    }

    private static void loadNpcBornData() {
        List<Path> files;
        try {
            files = listDirectory("BinOutput/Scene/SceneNpcBorn/", "*.json");
        } catch (IOException e) {
            Grasscutter.getLogger().error("Failed to load SceneNpcBorn folder.");
            return;
        }

        // Parsing includes building a spatial index per file, which makes this a
        // good candidate for parallelism.
        val parsed =
                parseFilesInParallel(
                        files,
                        path -> {
                            try {
                                val data = JsonUtils.loadToClass(path, SceneNpcBornData.class);
                                if (data.getBornPosList() == null || data.getBornPosList().size() == 0) {
                                    return null;
                                }

                                data.setIndex(
                                        SceneIndexManager.buildIndex(
                                                3, data.getBornPosList(), item -> item.getPos().toPoint()));
                                return data;
                            } catch (IOException ignored) {
                                return null;
                            }
                        });
        parsed.forEach(data -> GameData.getSceneNpcBornData().put(data.getSceneId(), data));
        Grasscutter.getLogger()
                .debug("Loaded " + GameData.getSceneNpcBornData().size() + " SceneNpcBornDatas.");
    }

    private static void loadConfigData() {
        loadConfigData(GameData.getAvatarConfigData(), "BinOutput/Avatar/", ConfigEntityAvatar.class);
        loadConfigData(
                GameData.getMonsterConfigData(), "BinOutput/Monster/", ConfigEntityMonster.class);
        loadConfigDataMap(
                GameData.getGadgetConfigData(), "BinOutput/Gadget/", ConfigEntityGadget.class);
    }

    private static <T extends ConfigEntityBase> void loadConfigData(
            Map<String, T> targetMap, String folderPath, Class<T> configClass) {
        val className = configClass.getName();
        List<Path> files;
        try {
            files = listDirectory(folderPath, "*.json");
        } catch (IOException e) {
            Grasscutter.getLogger().error("Failed to load {} folder.", className);
            return;
        }

        // Parse the files in parallel, then merge the results serially - the target
        // map is not thread-safe.
        val parsed =
                parseFilesInParallel(
                        files,
                        path -> {
                            val name = path.getFileName().toString().replace(".json", "");
                            return new AbstractMap.SimpleEntry<>(
                                    name, JsonUtils.loadToClass(path, configClass));
                        });
        parsed.forEach(entry -> targetMap.put(entry.getKey(), entry.getValue()));

        Grasscutter.getLogger().debug("Loaded {} {} entries.", targetMap.size(), className);
    }

    private static <T extends ConfigEntityBase> void loadConfigDataMap(
            Map<String, T> targetMap, String folderPath, Class<T> configClass) {
        val className = configClass.getName();
        List<Path> files;
        try {
            files = listDirectory(folderPath, "*.json");
        } catch (IOException e) {
            Grasscutter.getLogger().error("Failed to load {} folder.", className);
            return;
        }

        // Parse the files in parallel, then merge the results serially - the target
        // map is not thread-safe.
        val parsed =
                parseFilesInParallel(files, path -> JsonUtils.loadToMap(path, String.class, configClass));
        parsed.forEach(targetMap::putAll);

        Grasscutter.getLogger().debug("Loaded {} {} entries.", targetMap.size(), className);
    }

    private static void loadBlossomResources() {
        try {
            GameDepot.setBlossomConfig(DataLoader.loadClass("BlossomConfig.json", BlossomConfig.class));
            Grasscutter.getLogger().debug("Loaded BlossomConfig.");
        } catch (IOException e) {
            Grasscutter.getLogger().warn("Failed to load BlossomConfig.");
        }
    }

    private static void loadConfigLevelEntityData() {
        val pattern = Pattern.compile("ConfigLevelEntity_(.+?)\\.json");
        List<Path> files;
        try {
            files = listDirectory("BinOutput/LevelEntity/", "ConfigLevelEntity_*.json");
        } catch (IOException e) {
            Grasscutter.getLogger().error("Error loading config level entity: no files found");
            return;
        }

        val parsed =
                parseFilesInParallel(
                        files,
                        path -> {
                            val matcher = pattern.matcher(path.getFileName().toString());
                            if (!matcher.find()) return null;
                            Map<String, ConfigLevelEntity> config;
                            try {
                                config = JsonUtils.loadToMap(path, String.class, ConfigLevelEntity.class);
                            } catch (Exception e) {
                                Grasscutter.getLogger().error("Error loading player ability embryos:", e);
                                return null;
                            }
                            return config;
                        });
        parsed.forEach(GameData.getConfigLevelEntityDataMap()::putAll);

        if (GameData.getConfigLevelEntityDataMap().isEmpty()) {
            Grasscutter.getLogger().error("No config level entity loaded!");
            return;
        }
    }

    private static void loadQuestShareConfig() {

        val pattern = Pattern.compile("Q(.+?)\\ShareConfig.lua");

        try {
            var bindings = ScriptLoader.getEngine().createBindings();
            try (var stream =
                    Files.newDirectoryStream(getResourcePath("Scripts/Quest/Share/"), "Q*ShareConfig.lua")) {
            stream.forEach(
                    path -> {
                        val matcher = pattern.matcher(path.getFileName().toString());
                        if (!matcher.find()) return;

                        var cs = ScriptLoader.getScript("Quest/Share/" + path.getFileName().toString());
                        if (cs == null) return;

                        try {
                            ScriptLoader.eval(cs, bindings);

                            var teleportDataMap =
                                    ScriptLoader.getSerializer()
                                            .toMap(TeleportData.class, bindings.get("quest_data"));
                            var rewindDataMap =
                                    ScriptLoader.getSerializer().toMap(RewindData.class, bindings.get("rewind_data"));

                            GameData.getTeleportDataMap()
                                    .putAll(
                                            teleportDataMap.entrySet().stream()
                                                    .collect(
                                                            Collectors.toMap(
                                                                    entry -> Integer.valueOf(entry.getKey()), Entry::getValue)));
                            GameData.getRewindDataMap()
                                    .putAll(
                                            rewindDataMap.entrySet().stream()
                                                    .collect(
                                                            Collectors.toMap(
                                                                    entry -> Integer.valueOf(entry.getKey()), Entry::getValue)));
                        } catch (Throwable e) {
                            Grasscutter.getLogger()
                                    .error(
                                            "Error while loading Quest Share Config: {}", path.getFileName().toString());
                        }
                    });
            }
        } catch (IOException e) {
            Grasscutter.getLogger().error("Error loading Quest Share Config: no files found");
            return;
        }
        if (GameData.getTeleportDataMap() == null
                || GameData.getTeleportDataMap().isEmpty()
                || GameData.getRewindDataMap() == null
                || GameData.getRewindDataMap().isEmpty()) {
            Grasscutter.getLogger().error("No Quest Share Config loaded!");
            return;
        }
    }

    private static void loadGadgetMappings() {
        try {
            val gadgetMap = GameData.getGadgetMappingMap();
            try {
                JsonUtils.loadToList(getResourcePath("Server/GadgetMapping.json"), GadgetMapping.class)
                        .forEach(entry -> gadgetMap.put(entry.getGadgetId(), entry));
            } catch (IOException | NullPointerException ignored) {
            }
            Grasscutter.getLogger().debug("Loaded {} gadget mappings.", gadgetMap.size());
        } catch (Exception e) {
            Grasscutter.getLogger().error("Unable to load gadget mappings.", e);
        }
    }

    private static void loadSubfieldMappings() {
        try {
            val subfieldMap = GameData.getSubfieldMappingMap();
            try {
                JsonUtils.loadToList(getResourcePath("Server/SubfieldMapping.json"), SubfieldMapping.class)
                        .forEach(entry -> subfieldMap.put(entry.getEntityId(), entry));
                ;
            } catch (IOException | NullPointerException ignored) {
            }
            Grasscutter.getLogger().debug("Loaded {} subfield mappings.", subfieldMap.size());
        } catch (Exception e) {
            Grasscutter.getLogger().error("Unable to load subfield mappings.", e);
        }

        try {
            val dropSubfieldMap = GameData.getDropSubfieldMappingMap();
            try {
                JsonUtils.loadToList(
                                getResourcePath("Server/DropSubfieldMapping.json"), DropSubfieldMapping.class)
                        .forEach(entry -> dropSubfieldMap.put(entry.getDropId(), entry));
                ;
            } catch (IOException | NullPointerException ignored) {
            }
            Grasscutter.getLogger().debug("Loaded {} drop subfield mappings.", dropSubfieldMap.size());
        } catch (Exception e) {
            Grasscutter.getLogger().error("Unable to load drop subfield mappings.", e);
        }

        try {
            val dropTableExcelConfigDataMap = GameData.getDropTableExcelConfigDataMap();
            for (String fileName :
                    new String[] {
                        "Server/DropTableExcelConfigData.json",
                        "Server/DropSubTableExcelConfigData.json"
                    }) {
                try {
                    JsonUtils.loadToList(
                                    getResourcePath(fileName), DropTableExcelConfigData.class)
                            .forEach(entry -> dropTableExcelConfigDataMap.put(entry.getId(), entry));
                } catch (IOException | NullPointerException ignored) {
                }
            }
            Grasscutter.getLogger()
                    .debug("Loaded {} drop table configs.", dropTableExcelConfigDataMap.size());
        } catch (Exception e) {
            Grasscutter.getLogger().error("Unable to load drop table config data.", e);
        }
    }

    private static void loadMonsterMappings() {
        try {
            var monsterMap = GameData.getMonsterMappingMap();
            try {
                JsonUtils.loadToList(getResourcePath("Server/MonsterMapping.json"), MonsterMapping.class)
                        .forEach(entry -> monsterMap.put(entry.getMonsterId(), entry));
            } catch (IOException | NullPointerException ignored) {
            }

            Grasscutter.getLogger().debug("Loaded {} monster mappings.", monsterMap.size());
        } catch (Exception e) {
            Grasscutter.getLogger().error("Unable to load monster mappings.", e);
        }
    }

    private static void loadActivityCondGroups() {
        try {
            val gadgetMap = GameData.getActivityCondGroupMap();
            try {
                JsonUtils.loadToList(
                                getResourcePath("Server/ActivityCondGroups.json"), ActivityCondGroup.class)
                        .forEach(entry -> gadgetMap.put(entry.getCondGroupId(), entry));
            } catch (IOException | NullPointerException ignored) {
            }
            Grasscutter.getLogger().debug("Loaded {} ActivityCondGroups.", gadgetMap.size());
        } catch (Exception e) {
            Grasscutter.getLogger().error("Unable to load ActivityCondGroups.", e);
        }
    }

    private static void loadTrialAvatarCustomData() {
        try {
            String pathName = "CustomResources/TrialAvatarExcels/";
            try {
                JsonUtils.loadToList(
                                getResourcePath(pathName + "TrialAvatarActivityDataExcelConfigData.json"),
                                TrialAvatarActivityDataData.class)
                        .forEach(
                                instance -> {
                                    instance.onLoad();
                                    GameData.getTrialAvatarActivityDataCustomData()
                                            .put(instance.getTrialAvatarIndexId(), instance);
                                });
            } catch (IOException | NullPointerException ignored) {
            }
            Grasscutter.getLogger().debug("Loaded trial activity custom data.");
            try {
                JsonUtils.loadToList(
                                getResourcePath(pathName + "TrialAvatarActivityExcelConfigData.json"),
                                TrialAvatarActivityCustomData.class)
                        .forEach(
                                instance -> {
                                    instance.onLoad();
                                    GameData.getTrialAvatarActivityCustomData()
                                            .put(instance.getScheduleId(), instance);
                                });
            } catch (IOException | NullPointerException ignored) {
            }
            Grasscutter.getLogger().debug("Loaded trial activity schedule custom data.");
            try {
                JsonUtils.loadToList(
                                getResourcePath(pathName + "TrialAvatarData.json"), TrialAvatarCustomData.class)
                        .forEach(
                                instance -> {
                                    instance.onLoad();
                                    GameData.getTrialAvatarCustomData().put(instance.getTrialAvatarId(), instance);
                                });
            } catch (IOException | NullPointerException ignored) {
            }
            Grasscutter.getLogger().debug("Loaded trial avatar custom data.");
        } catch (Exception e) {
            Grasscutter.getLogger().error("Unable to load trial avatar custom data.", e);
        }
    }

    private static void loadGroupReplacements() {
        Bindings bindings = ScriptLoader.getEngine().createBindings();

        CompiledScript cs = ScriptLoader.getScript("Scene/groups_replacement.lua");
        if (cs == null) {
            Grasscutter.getLogger().error("Error while loading Group Replacements: file not found");
            return;
        }

        try {
            ScriptLoader.eval(cs, bindings);

            var replacementsMap =
                    ScriptLoader.getSerializer()
                            .toMap(GroupReplacementData.class, bindings.get("replacements"));

            GameData.getGroupReplacements()
                    .putAll(
                            replacementsMap.entrySet().stream()
                                    .collect(
                                            Collectors.toMap(
                                                    entry -> Integer.valueOf(entry.getValue().getId()), Entry::getValue)));

        } catch (Throwable e) {
            Grasscutter.getLogger().error("Error while loading Group Replacements");
        }

        if (GameData.getGroupReplacements() == null || GameData.getGroupReplacements().isEmpty()) {
            Grasscutter.getLogger().error("No Group Replacements loaded!");
        } else {
            Grasscutter.getLogger()
                    .debug("Loaded {} group replacements.", GameData.getGroupReplacements().size());
        }
    }

    public static class AbilityConfigData {
        public AbilityData Default;
        public boolean isDynamicAbility;
    }

    public static class AvatarConfig {
        @SerializedName(
                value = "abilities",
                alternate = {"targetAbilities"})
        public ArrayList<AvatarConfigAbility> abilities;
    }

    public static class AvatarConfigAbility {
        public String abilityName;

        public String toString() {
            return abilityName;
        }
    }

    private static class OpenConfig {
        public OpenConfigData[] data;
    }

    public static class OpenConfigData {
        public String $type;

        @SerializedName(value = "abilityName", alternate = {"BEAFNCHOJGD"})
        public String abilityName;

        @SerializedName(value = "varName", alternate = {"AAAENDNEBIG", "paramSpecial"})
        public String varName;

        @SerializedName(value = "varValue", alternate = {"KCHPDCEBCNI", "paramDelta"})
        public com.google.gson.JsonElement varValue;

        @SerializedName(
                value = "talentIndex",
                alternate = {"OJOFFKLNAHN", "LPHIOIIJJOD"})
        public int talentIndex;

        @SerializedName(
                value = "skillID",
                alternate = {"overtime"})
        public int skillID;

        @SerializedName(
                value = "pointDelta",
                alternate = {"IGEBKIHPOIF"})
        public int pointDelta;

        @SerializedName(value = "talentParam", alternate = {"FJIKJIDMFNH"})
        public String talentParam;
    }

    public static
    class ScenePointConfig {
        public Map<Integer, PointData> points;
    }
}
