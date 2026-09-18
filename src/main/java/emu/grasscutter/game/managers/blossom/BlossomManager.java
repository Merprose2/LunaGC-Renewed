package emu.grasscutter.game.managers.blossom;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.BlossomGroupsExcelConfigData;
import emu.grasscutter.data.excels.BlossomSectionOrderExcelConfigData;
import emu.grasscutter.data.excels.RewardPreviewData;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.entity.gadget.GadgetWorktop;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.*;
import emu.grasscutter.game.world.SpawnDataEntry.SpawnGroupEntry;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.server.packet.send.PacketBlossomBriefInfoNotify;
import emu.grasscutter.utils.FileUtils;
import emu.grasscutter.utils.Utils;
import it.unimi.dsi.fastutil.ints.*;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDateTime;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class BlossomManager {
    private final Scene scene;
    private final List<BlossomActivity> blossomActivities = new ArrayList<>();
    private final List<BlossomActivity> activeChests = new ArrayList<>();
    private final List<EntityGadget> createdEntity = new ArrayList<>();

    private final List<SpawnDataEntry> blossomConsumed = new ArrayList<>();

    private static final int[] SCOIN_REWARDS = {4101, 4103, 4104, 4105, 4106, 4107, 4108, 4109, 4110};
    private static final int[] EXP_REWARDS   = {4001, 4003, 4004, 4005, 4006, 4007, 4008, 4009, 4010};
    private static final int[] DRAGON_A     = {30311, 30312, 30313, 30314, 30315, 30316, 30317, 30318, 30319};
    private static final int[] DRAGON_B     = {30321, 30322, 30323, 30324, 30325, 30326, 30327, 30328, 30329};

    /**
     * Every scene group that belongs to a ley line outcrop camp, taken from
     * BlossomGroupsExcelConfigData. Used to recognise a camp group that has to be streamed in by
     * proximity even in the regions whose groups are flagged as dynamic_load (Mondstadt, Liyue and
     * Inazuma are configured that way, which is why their outcrops only appeared where hand written
     * spawn data existed).
     */
    private static volatile IntSet blossomCampGroups;

    /** Group id -> city id of the camps above. */
    private static volatile Int2IntMap blossomCampCityIds;

    /**
     * The operator gadget of every camp whose blossom is not a visible ElementBlock gadget. Most
     * camps use it (all the newer nations, the EXP ley lines and the magical ore ones), so a server
     * that only knows 70360056 / 70360057 never spawns those outcrops at all.
     */
    private static final int INVISIBLE_OPERATOR_GADGET_ID = 70360001;

    /** Config id for a camp whose operator config is unknown (it is placed from its group). */
    private static final int SYNTHETIC_CAMP_CONFIG_ID = -1;

    /** Every camp of a scene, keyed by scene id. Built once, lazily. */
    private static final Map<Integer, List<CampPoint>> campsByScene = new ConcurrentHashMap<>();

    /** The scene the ley line camps live in (the whole of Teyvat is one scene). */
    private static final int CAMP_SCENE_ID = 3;

    /**
     * Cities whose camp scripts are complete in the resources, meaning that both the operator and the
     * monsters of a camp are known. Their encounters must be built from that data alone - the generic
     * monster pool in BlossomConfig would spawn monsters of the wrong region, and camps without Lua
     * data (which would have nothing to fight) are not placed at all.
     */
    private static final Set<Integer> CITIES_WITH_COMPLETE_CAMP_DATA = Set.of(1, 2, 3, 4);

    /** The hour of the day at which the outcrops of a nation move on to their next section. */
    private static final int DAILY_RESET_HOUR = 4;

    /** City -> its sections in rotation order (BlossomSectionOrderExcelConfigData). */
    private static volatile Int2ObjectMap<IntList> sectionOrderByCity;

    /**
     * A ley line camp: the operator gadget that hosts its blossom and where it stands.
     *
     * <p>The camp scripts define that operator as a plain gadget which no suite references, because
     * the real server creates it as part of the blossom schedule ("RefreshBlossomGroup"). A server
     * that only loads the group suites therefore never sees these gadgets - which is why the newer
     * nations had (almost) no outcrops.
     *
     * @param groupId The scene group the camp belongs to.
     * @param configId The operator's config id inside that group.
     * @param gadgetId The operator gadget id (visible ElementBlock or the invisible operator).
     * @param type The blossom the camp hosts.
     * @param pos The operator's position, or null when it is only known from the group.
     * @param rot The operator's rotation, or null.
     * @param monsterIds The monsters of this camp, as listed by its own script. The list is empty
     *     when the resources do not contain the camp's Lua data.
     */
    public record CampPoint(
            int groupId,
            int cityId,
            int sectionId,
            int configId,
            int gadgetId,
            BlossomType type,
            Position pos,
            Position rot,
            List<Integer> monsterIds) {}

    /**
     * The day the outcrops are currently on.
     *
     * <p>The game rotates them at 04:00, not at midnight, so the day is derived from the time four
     * hours earlier - at 03:00 the previous day is still the current one.
     */
    public static int currentBlossomDay() {
        return (int) LocalDateTime.now().minusHours(DAILY_RESET_HOUR).toLocalDate().toEpochDay();
    }

    /** City -> its sections, in the order they take turns (cities without a rotation stay empty). */
    private static Int2ObjectMap<IntList> getSectionOrderByCity() {
        var cached = sectionOrderByCity;
        if (cached != null) {
            return cached;
        }

        var orders = new Int2ObjectOpenHashMap<IntList>();
        var table = GameData.getBlossomSectionOrderExcelConfigDataMap();
        if (table != null) {
            var rows = new ArrayList<>(table.values());
            rows.sort(java.util.Comparator.comparingInt(BlossomSectionOrderExcelConfigData::getOrder));
            for (var row : rows) {
                orders.computeIfAbsent(row.getCityId(), city -> new IntArrayList())
                        .add(row.getSectionId());
            }
        }

        sectionOrderByCity = orders;
        return orders;
    }

    /**
     * Whether a camp holds an outcrop today.
     *
     * <p>A nation that is listed in {@code BlossomSectionOrderExcelConfigData} only offers the camps
     * of the section that is currently up, and that section rotates daily. Nations that are not
     * listed there (Fontaine, Natlan and the other later regions) are not part of the rotation and
     * keep every camp they have.
     */
    private static boolean isCampActiveToday(CampPoint camp) {
        var sections = getSectionOrderByCity().get(camp.cityId());
        if (sections == null || sections.isEmpty()) {
            return true;
        }

        return camp.sectionId() == sections.getInt(currentBlossomDay() % sections.size());
    }

    /** Marker for a camp that has no entry in the harvested-camps map. */
    private static final int NOT_CONSUMED = -1;

    /** Upper bound for the harvested camps kept per player; a full rotation is far smaller. */
    private static final int MAX_REMEMBERED_CAMPS = 4096;

    /** Operator definitions inside a camp script: config id, gadget id, position and rotation. */
    private static final java.util.regex.Pattern SCRIPT_GADGET =
            java.util.regex.Pattern.compile(
                    "\\{\\s*config_id\\s*=\\s*(\\d+)\\s*,\\s*gadget_id\\s*=\\s*(\\d+)\\s*,"
                            + "\\s*pos\\s*=\\s*\\{\\s*x\\s*=\\s*([-\\d.]+)\\s*,\\s*y\\s*=\\s*([-\\d.]+)\\s*,\\s*z\\s*=\\s*([-\\d.]+)\\s*\\}\\s*,"
                            + "\\s*rot\\s*=\\s*\\{\\s*x\\s*=\\s*([-\\d.]+)\\s*,\\s*y\\s*=\\s*([-\\d.]+)\\s*,\\s*z\\s*=\\s*([-\\d.]+)\\s*\\}");

    /** A monster id inside a camp script's monsters table. */
    private static final java.util.regex.Pattern SCRIPT_MONSTER_ID =
            java.util.regex.Pattern.compile("monster_id\\s*=\\s*(\\d+)");

    /** A camp script's monsters table. */
    private static final java.util.regex.Pattern SCRIPT_MONSTERS =
            java.util.regex.Pattern.compile(
                    "\\bmonsters\\s*=\\s*\\{(.*?)\\n\\}",
                    java.util.regex.Pattern.DOTALL);

    /** The entry of a camp script's monsters table, so the camp can be placed on its monsters. */
    private static final java.util.regex.Pattern SCRIPT_MONSTER_ENTRY =
            java.util.regex.Pattern.compile(
                    "monster_id\\s*=\\s*(\\d+)\\s*,\\s*pos\\s*=\\s*\\{\\s*x\\s*=\\s*([-\\d.]+)\\s*,\\s*y\\s*=\\s*([-\\d.]+)\\s*,\\s*z\\s*=\\s*([-\\d.]+)");

    public BlossomManager(Scene scene) {
        this.scene = scene;
    }

    /** The camps of a scene, read once from the camp tables and their scene scripts. */
    public static List<CampPoint> getCamps(int sceneId) {
        var camps = campsByScene.get(sceneId);
        if (camps == null) {
            camps = loadCamps(sceneId);
            campsByScene.put(sceneId, camps);
        }
        return camps;
    }

    /** The camp of a scene group, or null when that group is not a ley line camp. */
    public static CampPoint getCamp(int groupId) {
        if (groupId <= 0 || !isBlossomCampGroup(groupId)) {
            return null;
        }
        for (var camp : getCamps(CAMP_SCENE_ID)) {
            if (camp.groupId() == groupId) {
                return camp;
            }
        }
        return null;
    }

    /** Is this gadget a ley line encounter operator? (invisible ones only inside a camp group) */
    public static boolean isBlossomOperator(int gadgetId, int groupId) {
        return BlossomType.valueOf(gadgetId) != null
                || (gadgetId == INVISIBLE_OPERATOR_GADGET_ID && isBlossomCampGroup(groupId));
    }

    /**
     * The blossom a gadget hosts, or null when the gadget is not a blossom operator. The invisible
     * operator carries no type of its own, so its camp decides whether it hosts Mora or EXP.
     */
    public static BlossomType getBlossomType(int gadgetId, int groupId) {
        var type = BlossomType.valueOf(gadgetId);
        if (type != null) {
            return type;
        }
        if (gadgetId == INVISIBLE_OPERATOR_GADGET_ID) {
            var camp = getCamp(groupId);
            return camp == null ? null : camp.type();
        }
        return null;
    }

    /**
     * Reads every camp of a scene: the camp tables say which group belongs to which city and which
     * blossom it hosts, the group's own script says where that camp's operator stands.
     */
    private static List<CampPoint> loadCamps(int sceneId) {
        var camps = new ArrayList<CampPoint>();
        var campTable = GameData.getBlossomGroupsExcelConfigDataMap();
        if (campTable == null || campTable.isEmpty()) {
            return camps;
        }

        for (var camp : campTable.values()) {
            if (camp == null || camp.getNewGroupVec() == null) {
                continue;
            }

            // Magical ore outcrops and event camps are not ley line blossoms.
            var type = getCampBlossomType(camp);
            if (type == null) {
                continue;
            }

            for (int groupId : camp.getNewGroupVec()) {
                var point = readCampFromScript(sceneId, camp, groupId, type);

                if (point == null) {
                    /*
                     * Nothing in the camp's script tells us where the camp stands, so it is placed
                     * where the scene's group stands.
                     *
                     * In the four nations whose camp data is complete (Mondstadt, Liyue, Inazuma and
                     * Sumeru) such a camp is skipped instead: without its Lua there is nothing for the
                     * player to fight, so it must not be offered.
                     */
                    if (CITIES_WITH_COMPLETE_CAMP_DATA.contains(camp.getCityId())) {
                        continue;
                    }

                    point =
                            new CampPoint(
                                    groupId,
                                    camp.getCityId(),
                                    camp.getSectionId(),
                                    SYNTHETIC_CAMP_CONFIG_ID,
                                    type.getGadgetId(),
                                    type,
                                    null,
                                    null,
                                    List.of());
                }

                camps.add(point);
            }
        }

        Grasscutter.getLogger()
                .debug("Loaded {} ley line outcrop camps for scene {}.", camps.size(), sceneId);
        return camps;
    }

    /** The blossom a camp hosts, taken from the refresh entries it can be refreshed with. */
    private static BlossomType getCampBlossomType(BlossomGroupsExcelConfigData camp) {
        var refreshIds = camp.getRefreshTypeVec();
        if (refreshIds == null) {
            return null;
        }

        for (int refreshId : refreshIds) {
            var refresh = GameData.getBlossomRefreshExcelConfigDataMap().get(refreshId);
            if (refresh == null) {
                continue;
            }
            if (refresh.getBlossomChestId() == BlossomType.GOLD.getBlossomChestId()) {
                return BlossomType.GOLD;
            }
            if (refresh.getBlossomChestId() == BlossomType.BLUE.getBlossomChestId()) {
                return BlossomType.BLUE;
            }
        }

        return null;
    }

    /**
     * Finds a camp's operator in its scene script. The script lists one gadget per blossom type plus
     * the invisible operator, and it is the only place that knows where the operator stands.
     */
    private static CampPoint readCampFromScript(
            int sceneId, BlossomGroupsExcelConfigData camp, int groupId, BlossomType type) {
        var path =
                FileUtils.getScriptPath(
                        "Scene/%d/scene%d_group%d.lua".formatted(sceneId, sceneId, groupId));
        if (path == null || !Files.isRegularFile(path)) {
            return null;
        }

        String source;
        try {
            source = Files.readString(path);
        } catch (IOException exception) {
            Grasscutter.getLogger()
                    .debug("Camp {} script could not be read: {}", groupId, exception.getMessage());
            return null;
        }

        int cityId = camp.getCityId();
        int sectionId = camp.getSectionId();
        var gadgets = SCRIPT_GADGET.matcher(source);
        var monsters = readCampMonsters(source);
        CampPoint invisible = null;

        while (gadgets.find()) {
            int configId = Integer.parseInt(gadgets.group(1));
            int gadgetId = Integer.parseInt(gadgets.group(2));
            var pos =
                    new Position(
                            Float.parseFloat(gadgets.group(3)),
                            Float.parseFloat(gadgets.group(4)),
                            Float.parseFloat(gadgets.group(5)));
            var rot =
                    new Position(
                            Float.parseFloat(gadgets.group(6)),
                            Float.parseFloat(gadgets.group(7)),
                            Float.parseFloat(gadgets.group(8)));

            if (BlossomType.valueOf(gadgetId) == type) {
                // The blossom this camp is refreshed with, e.g. the Mora operator of a Mora camp.
                return new CampPoint(
                        groupId, cityId, sectionId, configId, gadgetId, type, pos, rot, monsters);
            }
            if (gadgetId == INVISIBLE_OPERATOR_GADGET_ID && invisible == null) {
                invisible =
                        new CampPoint(
                                groupId,
                                cityId,
                                sectionId,
                                configId,
                                gadgetId,
                                type,
                                pos,
                                rot,
                                monsters);
            }
        }

        if (invisible != null) {
            return invisible;
        }

        // No operator at all: the camp stands where its own monsters stand.
        var monsterPos = readCampMonsterPosition(source);
        if (monsterPos != null) {
            return new CampPoint(
                    groupId,
                    cityId,
                    sectionId,
                    SYNTHETIC_CAMP_CONFIG_ID,
                    type.getGadgetId(),
                    type,
                    monsterPos,
                    new Position(),
                    monsters);
        }

        return null;
    }

    /** The monsters a camp's script lists; empty when the resources lack the camp's Lua data. */
    private static List<Integer> readCampMonsters(String source) {
        var table = SCRIPT_MONSTERS.matcher(source);
        if (!table.find()) {
            return List.of();
        }

        var monsters = new ArrayList<Integer>();
        var ids = SCRIPT_MONSTER_ID.matcher(table.group(1));
        while (ids.find()) {
            int id = Integer.parseInt(ids.group(1));
            if (!monsters.contains(id)) {
                monsters.add(id);
            }
        }
        return monsters;
    }

    /** Where a camp's monsters stand, used for camps whose script has no operator. */
    private static Position readCampMonsterPosition(String source) {
        var entry = SCRIPT_MONSTER_ENTRY.matcher(source);
        if (!entry.find()) {
            return null;
        }

        return new Position(
                Float.parseFloat(entry.group(2)),
                Float.parseFloat(entry.group(3)),
                Float.parseFloat(entry.group(4)));
    }

    /**
     * Marks a camp as harvested, so it stays gone until the daily reset. Camp slots one city is not
     * offering today are pruned at the same time, which keeps the stored map small.
     */
    private void consumeCamp(EntityGadget gadget) {
        if (gadget == null || !isBlossomCampGroup(gadget.getGroupId())) {
            return;
        }

        var consumed = getHostConsumedCamps();
        if (consumed == null) {
            return;
        }

        int today = currentBlossomDay();
        synchronized (consumed) {
            consumed.entrySet().removeIf(entry -> entry.getValue() != today);
            if (consumed.size() > MAX_REMEMBERED_CAMPS) {
                consumed.clear();
            }
            consumed.put(gadget.getGroupId(), today);
        }

        var world = scene.getWorld();
        if (world != null && world.getHost() != null) {
            world.getHost().save();
        }
    }

    /**
     * Was this camp's blossom already harvested in this scene instance? The state lives on the world's
     * host player, so it survives a relog - a collected outcrop only comes back after the daily reset.
     */
    private boolean isCampConsumed(CampPoint camp) {
        return isCampConsumed(camp.groupId());
    }

    private boolean isCampConsumed(int groupId) {
        var consumed = getHostConsumedCamps();
        if (consumed == null) {
            return false;
        }

        synchronized (consumed) {
            return consumed.getOrDefault(groupId, NOT_CONSUMED) == currentBlossomDay();
        }
    }

    /**
     * Has the world's host already harvested the camp of this scene group today?
     *
     * <p>Used to keep such a camp out of the scene (and off the map) until the daily reset, no matter
     * which path would otherwise bring it back.
     */
    public boolean isCampHarvested(int groupId) {
        return groupId > 0 && isBlossomCampGroup(groupId) && isCampConsumed(groupId);
    }

    /**
     * Sends the outcrop markers of every nation to a single player.
     *
     * <p>Called when a player enters a scene so that the map shows the ley lines of the whole world
     * right away - otherwise the markers only arrive for outcrops the player walked up to.
     */
    public void sendBlossomIcons(Player player) {
        if (player == null) {
            return;
        }

        player.sendPacket(new PacketBlossomBriefInfoNotify(this.buildBriefInfos(List.of())));
    }

    /** The camps the world's host has already harvested, or null when there is no host to store them. */
    private Map<Integer, Integer> getHostConsumedCamps() {
        var world = scene.getWorld();
        var host = world == null ? null : world.getHost();
        return host == null ? null : host.getBlossomConsumedCamps();
    }

    /** Is a blossom of this type already standing this close to the given position? */
    private boolean hasBlossomNear(Position pos, BlossomType type, float radius) {
        float radiusSquared = radius * radius;
        for (var entity : Map.copyOf(scene.getEntities()).values()) {
            if (!(entity instanceof EntityGadget gadget)
                    || gadget.getGadgetId() != type.getGadgetId()) {
                continue;
            }

            var other = gadget.getPosition();
            float dx = pos.getX() - other.getX();
            float dy = pos.getY() - other.getY();
            float dz = pos.getZ() - other.getZ();
            if (dx * dx + dy * dy + dz * dz <= radiusSquared) {
                return true;
            }
        }
        return false;
    }

    /** Where a camp stands: from its script, or from the group the scene has loaded. */
    private Position resolveCampPosition(CampPoint camp) {
        if (camp.pos() != null) {
            return camp.pos();
        }

        var group = findLoadedGroup(camp.groupId());
        return group == null ? null : group.pos;
    }

    /** Finds a group among the blocks the scene has parsed, without forcing a block to load. */
    private SceneGroup findLoadedGroup(int groupId) {
        var blocks = scene.getScriptManager().getBlocks();
        if (blocks == null) {
            return null;
        }

        for (var block : blocks.values()) {
            var groups = block.groups;
            if (groups == null) {
                continue;
            }
            var group = groups.get(groupId);
            if (group != null) {
                return group;
            }
        }
        return null;
    }

    /**
     * Brings a camp's ley line encounter into the scene once its group is loaded.
     *
     * <p>Camp scripts keep the operator out of their suites: in the real game the server creates it
     * when the camp is refreshed. Doing the same here is what gives the camps of Sumeru, Fontaine,
     * Natlan and Snezhnaya an outcrop at all - before this, a camp only had a blossom if someone had
     * hand written a spawn entry for it.
     */
    public void onCampGroupLoaded(SceneGroup group) {
        if (group == null || !isBlossomCampGroup(group.id)) {
            return;
        }

        var camp = getCamp(group.id);
        if (camp == null || isCampConsumed(camp) || !isCampActiveToday(camp)) {
            return;
        }

        // A suite of the group may already carry the operator, and the player may already be in the
        // encounter - in both cases there is nothing to add.
        if (scene.getEntityByConfigId(camp.configId(), camp.groupId()) instanceof EntityGadget) {
            return;
        }

        // Camps without an operator in their script are placed where their group stands.
        var pos = camp.pos() != null ? camp.pos() : group.pos;
        if (pos == null) {
            return;
        }
        if (this.hasBlossomNear(pos, camp.type(), 12f)) {
            return;
        }

        var gadget =
                new EntityGadget(
                        scene,
                        camp.gadgetId(),
                        pos,
                        camp.rot() != null ? camp.rot() : new Position());
        gadget.setGroupId(camp.groupId());
        gadget.setConfigId(camp.configId());
        gadget.setState(0);

        // Adding it to the scene is what installs the worktop option (see Scene#addEntityDirectly).
        scene.addEntity(gadget);

        Grasscutter.getLogger()
                .debug(
                        "Blossom camp {} ({}) is active at {}.",
                        camp.groupId(),
                        camp.type(),
                        pos);
    }

    /** Is this scene group one of the ley line outcrop camps? */
    public static boolean isBlossomCampGroup(int groupId) {
        return groupId > 0 && getBlossomCampGroups().contains(groupId);
    }

    private static IntSet getBlossomCampGroups() {
        var cached = blossomCampGroups;
        if (cached != null) {
            return cached;
        }

        var groups = new IntOpenHashSet();
        var cityIds = new Int2IntOpenHashMap();

        try {
            var dataMap = GameData.getBlossomGroupsExcelConfigDataMap();
            if (dataMap != null) {
                for (var camp : dataMap.values()) {
                    if (camp == null || camp.getNewGroupVec() == null) {
                        continue;
                    }
                    for (int groupId : camp.getNewGroupVec()) {
                        groups.add(groupId);
                        cityIds.put(groupId, camp.getCityId());
                    }
                }
            }
        } catch (Exception exception) {
            Grasscutter.getLogger()
                    .debug("Could not read the blossom camp groups: {}", exception.getMessage());
        }

        blossomCampCityIds = cityIds;
        blossomCampGroups = groups;

        Grasscutter.getLogger().debug("Loaded {} ley line outcrop camp groups.", groups.size());
        return groups;
    }

    /**
     * The city a blossom (camp group) belongs to. Nations use one group prefix per city, so the
     * prefix is a safe fallback when the camp table is unavailable.
     */
    public static int getCityIdForGroup(int groupId) {
        if (groupId <= 0) {
            return 0;
        }

        if (getBlossomCampGroups().contains(groupId)) {
            int cityId = blossomCampCityIds.get(groupId);
            if (cityId > 0) {
                return cityId;
            }
        }

        // 1330xxxxx = Mondstadt, 1331xxxxx = Liyue, ..., 1336xxxxx = Snezhnaya
        int region = (groupId / 100000) % 10;
        return (groupId >= 133000000 && region <= 6) ? region + 1 : 0;
    }

    public void onTick() {
        synchronized (blossomActivities) {
            var it = blossomActivities.iterator();
            while (it.hasNext()) {
                var active = it.next();
                active.onTick();
                if (active.getPass()) {
                    EntityGadget chest = active.getChest();
                    scene.addEntity(chest);
                    scene.setChallenge(null);
                    activeChests.add(active);
                    it.remove();
                }
            }
        }
    }

    public void recycleGadgetEntity(List<GameEntity> entities) {
        for (var entity : entities) {
            if (entity instanceof EntityGadget gadget) {
                synchronized (createdEntity) {
                    createdEntity.remove(gadget);
                }
            }
        }
        notifyIcon();
    }

    public void initBlossom(EntityGadget gadget) {
        synchronized (createdEntity) {
            if (createdEntity.contains(gadget)) {
                return;
            }
        }
        var spawnEntry = gadget.getSpawnEntry();
        if (spawnEntry != null && blossomConsumed.contains(spawnEntry)) {
            return;
        }
        var id = gadget.getGadgetId();
        if (getBlossomType(id, gadget.getGroupId()) == null) {
            return;
        }
        if (isCampConsumed(gadget.getGroupId())) {
            /*
             * Safety net: the scene already refuses to add an outcrop the host harvested today, but if
             * one slips through here it must not become a working blossom again before the reset.
             */
            return;
        }
        gadget.buildContent();
        gadget.setState(204);
        int worldLevel = getWorldLevel();
        if (!(gadget.getContent() instanceof GadgetWorktop gadgetWorktop)) {
            Grasscutter.getLogger()
                    .debug("Blossom gadget {} has no worktop content, skipping it.", id);
            return;
        }
        gadgetWorktop.addWorktopOptions(new int[] {187});
        gadgetWorktop.setOnSelectWorktopOptionEvent(
                (GadgetWorktop context, int option) -> {
                    BlossomActivity activity;
                    EntityGadget entityGadget = context.getGadget();
                    synchronized (blossomActivities) {
                        for (BlossomActivity i : this.blossomActivities) {
                            if (i.getGadget() == entityGadget) {
                                return false;
                            }
                        }

                        int volume = 0;
                        IntList monsters = new IntArrayList();
                        var camp = getCamp(entityGadget.getGroupId());
                        var campMonsters = camp == null ? List.<Integer>of() : camp.monsterIds();

                        while (true) {
                            var remain = GameDepot.getBlossomConfig().getMonsterFightingVolume() - volume;
                            if (remain <= 0) {
                                break;
                            }

                            if (!campMonsters.isEmpty()) {
                                /*
                                 * The camp's own script lists the monsters of its region (the ley
                                 * lines of every nation field their own fauna), so those are used
                                 * instead of the generic pool below.
                                 */
                                monsters.add(
                                        campMonsters.get(
                                                Utils.randomRange(0, campMonsters.size() - 1)));
                                volume += 10;
                                continue;
                            }

                            var rand = Utils.randomRange(1, 100);
                            if (rand > 85 && remain >= 50) { // 15% ,generate strong monster
                                monsters.addAll(getRandomMonstersID(2, 1));
                                volume += 50;
                            } else if (rand > 50 && remain >= 20) { // 35% ,generate normal monster
                                monsters.addAll(getRandomMonstersID(1, 1));
                                volume += 20;
                            } else { // 50% ,generate weak monster
                                monsters.addAll(getRandomMonstersID(0, 1));
                                volume += 10;
                            }
                        }

                        Grasscutter.getLogger().info("Blossom Monsters:" + monsters);

                        activity = new BlossomActivity(entityGadget, monsters, -1, worldLevel);
                        blossomActivities.add(activity);
                    }
                    entityGadget.updateState(201);
                    scene.setChallenge(activity.getChallenge());
                    scene.removeEntity(entityGadget, VisionTypeOuterClass.VisionType.VisionType_VISION_REMOVE);
                    activity.start();
                    return true;
                });
        synchronized (createdEntity) {
            createdEntity.add(gadget);
        }
        notifyIcon();
    }

    public void notifyIcon() {
        scene.broadcastPacket(new PacketBlossomBriefInfoNotify(this.buildBriefInfos(List.of())));
    }

    /**
     * The ley line outcrops the client may show for the given cities (every city when the list is
     * empty). Two sources are combined:
     *
     * <ol>
     *   <li>the static spawn data, which covers the outcrops of the older nations, and
     *   <li>the outcrops the scene created from their own camp group. Those are the only ones
     *       present in the newer nations (Sumeru, Fontaine, Natlan, Snezhnaya), so without them the
     *       map / handbook list would stay empty there even though the outcrop is right in front of
     *       the player.
     * </ol>
     */
    public List<BlossomBriefInfoOuterClass.BlossomBriefInfo> buildBriefInfos(
            List<Integer> cityIds) {
        final int wl = getWorldLevel();
        final int worldLevel = (wl < 0) ? 0 : ((wl > 9) ? 9 : wl);
        final var worldLevelData = GameData.getWorldLevelDataMap().get(worldLevel);
        final int monsterLevel = (worldLevelData != null) ? worldLevelData.getMonsterLevel() : 1;
        List<BlossomBriefInfoOuterClass.BlossomBriefInfo> blossoms = new ArrayList<>();

        // 1. The outcrops that come from the static spawn data.
        GameDepot.getSpawnLists()
                .forEach(
                        (gridBlockId, spawnDataEntryList) -> {
                            int sceneId = gridBlockId.getSceneId();
                            spawnDataEntryList.stream()
                                    .map(SpawnDataEntry::getGroup)
                                    .map(SpawnGroupEntry::getSpawns)
                                    .filter(Objects::nonNull)
                                    .flatMap(List::stream)
                                    .filter(Objects::nonNull)
                                    /*
                                     * Camps are listed by the camp pass below, which knows whether a
                                     * camp is up today, where it stands and whether the host already
                                     * harvested it. Listing them here as well would keep a marker
                                     * alive after a relog, simply because an old spawn data entry
                                     * still points at it.
                                     */
                                    .filter(this::shouldListSpawnDataBlossom)
                                    .filter(spawn -> !blossomConsumed.contains(spawn))
                                    .filter(spawn -> BlossomType.valueOf(spawn.getGadgetId()) != null)
                                    .forEach(
                                            spawn ->
                                                    addBriefInfo(
                                                            blossoms,
                                                            sceneId,
                                                            spawn,
                                                            worldLevel,
                                                            monsterLevel,
                                                            cityIds));
                        });

        // 2. The outcrops that only exist as a scene group gadget (every nation's own camps).
        for (var gadget : snapshotCreatedEntities()) {
            if (gadget.getSpawnEntry() != null || gadget.getScene() == null) {
                // Spawned from the spawn data (already listed above) or already detached.
                continue;
            }

            var type = getBlossomType(gadget.getGadgetId(), gadget.getGroupId());
            if (type == null) {
                continue;
            }

            int cityId = getCityIdForGroup(gadget.getGroupId());
            if (!matchesCity(cityIds, cityId)) {
                continue;
            }

            var brief =
                    buildBriefInfo(
                            gadget.getScene().getId(),
                            type,
                            gadget.getPosition(),
                            cityId,
                            worldLevel,
                            monsterLevel);

            if (brief != null) {
                blossoms.add(brief);
            }
        }

        /*
         * 3. Every camp of this scene, so the map and the handbook list the outcrops of every nation
         *    - including the camps the player has not walked past yet.
         */
        for (var camp : getCamps(this.scene.getId())) {
            if (!isCampActiveToday(camp) || isCampConsumed(camp)) {
                continue;
            }

            var campPos = resolveCampPosition(camp);
            if (campPos == null) {
                // Nothing in the resources says where this camp stands.
                continue;
            }

            int campCityId = getCityIdForGroup(camp.groupId());
            if (!matchesCity(cityIds, campCityId)) {
                continue;
            }

            var campBrief =
                    buildBriefInfo(
                            this.scene.getId(),
                            camp.type(),
                            campPos,
                            campCityId,
                            worldLevel,
                            monsterLevel);

            if (campBrief != null) {
                blossoms.add(campBrief);
            }
        }

        return blossoms;
    }

    private List<EntityGadget> snapshotCreatedEntities() {
        synchronized (createdEntity) {
            return new ArrayList<>(createdEntity);
        }
    }

    /**
     * Whether an outcrop from the static spawn data should be listed for the client.
     *
     * <p>Camps are normally listed by the camp pass, which also applies the section rotation and the
     * daily harvest state. This keeps a camp's old spawn data entry from becoming a second marker that
     * survives a relog - while still using it for camps nothing else can place.
     */
    private boolean shouldListSpawnDataBlossom(SpawnDataEntry spawn) {
        var group = spawn.getGroup();
        if (group == null || !isBlossomCampGroup(group.getGroupId())) {
            // Not a camp: the camp pass does not know this outcrop at all.
            return true;
        }

        var camp = getCamp(group.getGroupId());
        if (camp == null) {
            return true;
        }

        // A harvested camp stays off the map, and one that is not up today is not offered at all.
        if (isCampConsumed(camp) || !isCampActiveToday(camp)) {
            return false;
        }

        // The camp pass lists it (with its city) whenever it can place it; otherwise this entry is the
        // only thing that knows where the camp stands.
        return resolveCampPosition(camp) == null;
    }

    private static boolean matchesCity(List<Integer> cityIds, int cityId) {
        return cityIds == null || cityIds.isEmpty() || cityIds.contains(cityId);
    }

    private static void addBriefInfo(
            List<BlossomBriefInfoOuterClass.BlossomBriefInfo> blossoms,
            int sceneId,
            SpawnDataEntry spawn,
            int worldLevel,
            int monsterLevel,
            List<Integer> cityIds) {
        var type = BlossomType.valueOf(spawn.getGadgetId());
        if (type == null || spawn.getPos() == null) {
            return;
        }

        int cityId =
                getCityIdForGroup(
                        spawn.getGroup() == null ? 0 : spawn.getGroup().getGroupId());
        if (!matchesCity(cityIds, cityId)) {
            return;
        }

        var brief =
                buildBriefInfo(
                        sceneId, type, spawn.getPos(), cityId, worldLevel, monsterLevel);
        if (brief != null) {
            blossoms.add(brief);
        }
    }

    private static BlossomBriefInfoOuterClass.BlossomBriefInfo buildBriefInfo(
            int sceneId,
            BlossomType type,
            Position pos,
            int cityId,
            int worldLevel,
            int monsterLevel) {
        Integer previewReward = getPreviewReward(type, worldLevel);
        if (previewReward == null) {
            return null;
        }

        return BlossomBriefInfoOuterClass.BlossomBriefInfo.newBuilder()
                .setSceneId(sceneId)
                .setPos(pos.toProto())
                .setResin(20)
                .setMonsterLevel(monsterLevel)
                .setRewardId(previewReward)
                .setCircleCampId(type.getCircleCampId())
                .setRefreshId(type.getBlossomChestId())
                // The client groups the outcrops by city, so an outcrop without a city id is not
                // listed in the map / handbook at all.
                .setCityId(cityId)
                .build();
    }

    public int getWorldLevel() {
        return scene.getWorld().getWorldLevel();
    }

    private static Integer getFallbackPreviewReward(int blossomChestId, Object data, int worldLevel) {
        int wl = Math.max(0, Math.min(worldLevel, 8));
        if (blossomChestId == 1) return SCOIN_REWARDS[wl];
        if (blossomChestId == 2) return EXP_REWARDS[wl];
        if (blossomChestId == 3) return DRAGON_A[wl];
        if (blossomChestId == 4) return DRAGON_B[wl];

        if (data != null) {
            try {
                var method = data.getClass().getMethod("getRefreshType");
                Object typeObj = method.invoke(data);
                if (typeObj != null) {
                    String str = typeObj.toString();
                    if (str.contains("SENTRY_TOWER")) return 31701;
                    if (str.contains("BOMB")) return 31702;
                }
            } catch (Exception ignored) {}
        }

        return null;
    }

    private static Integer getPreviewReward(BlossomType type, int worldLevel) {
        // TODO: blossoms should be based on their city
        if (type == null) {
            Grasscutter.getLogger().error("Illegal blossom type {}", type);
            return null;
        }

        int blossomChestId = type.getBlossomChestId();
        var dataMap = GameData.getBlossomRefreshExcelConfigDataMap();

        if (dataMap == null || dataMap.isEmpty()) {
            Grasscutter.getLogger().debug("Blossom refresh config data is missing.");
            return null;
        }

        for (var data : dataMap.values()) {
            if (data == null) {
                continue;
            }

            if (blossomChestId == data.getBlossomChestId()) {
                var dropVecList = data.getDropVec();

                // Handles File 1 where "DropVec" was capitalized and GSON skipped deserialization
                if (dropVecList == null || dropVecList.length == 0) {
                    Integer fallback = getFallbackPreviewReward(blossomChestId, data, worldLevel);
                    if (fallback != null) {
                        return fallback;
                    }

                    Grasscutter.getLogger()
                            .debug(
                                    "Blossom refresh config has no drop vector: blossomChestId={}, type={}",
                                    blossomChestId,
                                    type);
                    return null;
                }

                if (worldLevel < 0 || worldLevel >= dropVecList.length) {
                    Grasscutter.getLogger()
                            .debug(
                                    "Illegal blossom world level: worldLevel={}, dropVecLength={}, blossomChestId={}, type={}",
                                    worldLevel,
                                    dropVecList.length,
                                    blossomChestId,
                                    type);
                    return null;
                }

                if (dropVecList[worldLevel] == null) {
                    Grasscutter.getLogger()
                            .debug(
                                    "Blossom drop vector entry is null: worldLevel={}, blossomChestId={}, type={}",
                                    worldLevel,
                                    blossomChestId,
                                    type);
                    return null;
                }

                return dropVecList[worldLevel].getPreviewReward();
            }
        }

        Grasscutter.getLogger().debug("Cannot find blossom type {}", type);
        return null;
    }

    private static RewardPreviewData getRewardList(BlossomType type, int worldLevel) {
        Integer previewReward = getPreviewReward(type, worldLevel);
        if (previewReward == null) return null;
        return GameData.getRewardPreviewDataMap().get((int) previewReward);
    }

    public List<GameItem> onReward(Player player, EntityGadget chest, boolean useCondensedResin) {
        var resinManager = player.getResinManager();
        synchronized (activeChests) {
            var it = activeChests.iterator();
            while (it.hasNext()) {
                var activeChest = it.next();
                if (activeChest.getChest() == chest) {
                    boolean pay =
                            useCondensedResin ? resinManager.useCondensedResin(1) : resinManager.useResin(20);
                    if (pay) {
                        int worldLevel = getWorldLevel();
                        List<GameItem> items = new ArrayList<>();
                        var gadget = activeChest.getGadget();
                        var type = getBlossomType(gadget.getGadgetId(), gadget.getGroupId());
                        RewardPreviewData blossomRewards = getRewardList(type, worldLevel);
                        if (blossomRewards == null) {
                            Grasscutter.getLogger()
                                    .error("Blossom could not support world level : " + worldLevel);
                            return null;
                        }
                        var rewards = blossomRewards.getPreviewItems();
                        for (ItemParamData blossomReward : rewards) {
                            int rewardCount = blossomReward.getCount();
                            if (useCondensedResin) {
                                rewardCount += blossomReward.getCount(); // Double!
                            }
                            items.add(new GameItem(blossomReward.getItemId(), rewardCount));
                        }
                        it.remove();
                        recycleGadgetEntity(List.of(gadget));
                        consumeCamp(gadget);
                        var spawnEntry = gadget.getSpawnEntry();
                        if (spawnEntry != null) {
                            // Only static spawn data entries can be marked as consumed.
                            blossomConsumed.add(spawnEntry);
                        }
                        return items;
                    }
                    return null;
                }
            }
        }
        return null;
    }

    public static IntList getRandomMonstersID(int difficulty, int count) {
        IntList result = new IntArrayList();
        List<Integer> monsters =
                GameDepot.getBlossomConfig().getMonsterIdsPerDifficulty().get(difficulty);
        for (int i = 0; i < count; i++) {
            result.add((int) monsters.get(Utils.randomRange(0, monsters.size() - 1)));
        }
        return result;
    }
}
