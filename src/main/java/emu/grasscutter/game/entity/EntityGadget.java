package emu.grasscutter.game.entity;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.config.ConfigEntityGadget;
import emu.grasscutter.data.binout.config.fields.ConfigAbilityData;
import emu.grasscutter.data.excels.GadgetData;
import emu.grasscutter.data.excels.fishing.FishPoolData;
import emu.grasscutter.data.excels.fishing.FishStockData;
import emu.grasscutter.data.excels.monster.MonsterCurveData;
import emu.grasscutter.game.entity.gadget.*;
import emu.grasscutter.game.entity.gadget.platform.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.world.*;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.net.proto.AbilitySyncStateInfoOuterClass.AbilitySyncStateInfo;
import emu.grasscutter.net.proto.AnimatorParameterValueInfoPairOuterClass.AnimatorParameterValueInfoPair;
import emu.grasscutter.net.proto.EntityAuthorityInfoOuterClass.EntityAuthorityInfo;
import emu.grasscutter.net.proto.EntityClientDataOuterClass.EntityClientData;
import emu.grasscutter.net.proto.EntityRendererChangedInfoOuterClass.EntityRendererChangedInfo;
import emu.grasscutter.net.proto.GadgetInteractReqOuterClass.GadgetInteractReq;
import emu.grasscutter.net.proto.MotionInfoOuterClass.MotionInfo;
import emu.grasscutter.net.proto.PropPairOuterClass.PropPair;
import emu.grasscutter.net.proto.ProtEntityTypeOuterClass.ProtEntityType;
import emu.grasscutter.net.proto.SceneEntityAiInfoOuterClass.SceneEntityAiInfo;
import emu.grasscutter.net.proto.SceneEntityInfoOuterClass.SceneEntityInfo;
import emu.grasscutter.net.proto.SceneGadgetInfoOuterClass.SceneGadgetInfo;
import emu.grasscutter.net.proto.VectorOuterClass.Vector;
import emu.grasscutter.net.proto.FishPoolInfoOuterClass;
import emu.grasscutter.scripts.EntityControllerScriptManager;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.data.*;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.utils.helpers.ProtoHelper;
import it.unimi.dsi.fastutil.ints.*;
import java.util.*;
import javax.annotation.Nullable;
import lombok.*;

@ToString(callSuper = true)
public class EntityGadget extends EntityBaseGadget {
    @Getter private final GadgetData gadgetData;

    @Getter(onMethod_ = @Override)
    @Setter
    private int gadgetId;

    @Getter private final Position bornPos;
    @Getter private final Position bornRot;
    @Getter @Setter private GameEntity owner = null;
    @Getter @Setter private List<GameEntity> children = new ArrayList<>();

    @Getter private int state;
    @Getter @Setter private int pointType;
    @Getter private GadgetContent content;

    @Getter(onMethod_ = @Override, lazy = true)
    private final Int2FloatMap fightProperties = new Int2FloatOpenHashMap();

    @Getter @Setter private SceneGadget metaGadget;
    @Nullable @Getter ConfigEntityGadget configGadget;
    @Getter @Setter private BaseRoute routeConfig;

    @Getter @Setter private int stopValue = 0; // Controller related, inited to zero
    @Getter @Setter private int startValue = 0; // Controller related, inited to zero
    @Getter @Setter private int ticksSinceChange;

    @Getter private boolean interactEnabled = true;

    public EntityGadget(Scene scene, int gadgetId, Position pos) {
        this(scene, gadgetId, pos, null, null);
    }

    public EntityGadget(Scene scene, int gadgetId, Position pos, Position rot) {
        this(scene, gadgetId, pos, rot, null);
    }

    public EntityGadget(
            Scene scene, int gadgetId, Position pos, Position rot, int campId, int campType) {
        this(scene, gadgetId, pos, rot, null, campId, campType);
    }

    public EntityGadget(
            Scene scene, int gadgetId, Position pos, Position rot, GadgetContent content) {
        this(scene, gadgetId, pos, rot, content, 0, 0);
    }

    public EntityGadget(
            Scene scene,
            int gadgetId,
            Position pos,
            Position rot,
            GadgetContent content,
            int campId,
            int campType) {
        super(scene, pos, rot, campId, campType);

        this.gadgetData = GameData.getGadgetDataMap().get(gadgetId);
        if (gadgetData != null && gadgetData.getJsonName() != null) {
            this.configGadget = GameData.getGadgetConfigData().get(gadgetData.getJsonName());
        }

        this.id = this.getScene().getWorld().getNextEntityId(EntityIdType.GADGET);
        this.gadgetId = gadgetId;
        this.content = content;
        this.bornPos = this.getPosition().clone();
        this.bornRot = this.getRotation().clone();
        this.fillFightProps(configGadget);

        // Check if this gadget is the abyss defense objective's gadget.
        // That doesn't have a level and defaults to having 5000 hp, so it dies in like 2 hits on 11-1.
        // I'll forgive player skill issues and scale its hp up here.
        // TODO: find out how its fight props are actually scaled
        if (gadgetData.getJsonName().equals("SceneObj_Gear_Operator_Mamolu_Entity")) {
            MonsterCurveData curve = GameData.getMonsterCurveDataMap().get(11);
            if (curve != null) {
                FightProperty[] hpProps = {
                    FightProperty.FIGHT_PROP_MAX_HP,
                    FightProperty.FIGHT_PROP_BASE_HP,
                    FightProperty.FIGHT_PROP_CUR_HP
                };
                for (var prop : hpProps) {
                    setFightProperty(
                            prop, this.getFightProperty(prop) * curve.getMultByProp("GROW_CURVE_HP_ENVIRONMENT"));
                }
            }
        }

        if (GameData.getGadgetMappingMap().containsKey(gadgetId)) {
            var controllerName = GameData.getGadgetMappingMap().get(gadgetId).getServerController();
            this.setEntityController(EntityControllerScriptManager.getGadgetController(controllerName));
            if (this.getEntityController() == null) {
                Grasscutter.getLogger().warn("Gadget controller {} not found.", controllerName);
            }
        }

        this.initAbilities(); // TODO: move this
    }

    private void addConfigAbility(ConfigAbilityData abilityData) {
        var data = GameData.getAbilityData(abilityData.getAbilityName());
        if (data != null)
            this.getScene().getWorld().getHost().getAbilityManager().addAbilityToEntity(this, data);
    }

    @Override
    public void initAbilities() {
        // TODO: handle pre-dynamic, static and dynamic here
        if (this.configGadget != null && this.configGadget.getAbilities() != null) {
            for (var ability : this.configGadget.getAbilities()) {
                this.addConfigAbility(ability);
            }
        }
    }

    public void setInteractEnabled(boolean enable) {
        this.interactEnabled = enable;
        this.getScene()
                .broadcastPacket(new PacketGadgetStateNotify(this, this.getState())); // Update the interact
    }

    public void setState(int state) {
        this.state = state;
        // Cache the gadget state
        if (metaGadget != null && metaGadget.group != null) {
            var instance = getScene().getScriptManager().getGroupInstanceById(metaGadget.group.id);
            if (instance != null) instance.cacheGadgetState(metaGadget, state);
        }
    }

    public void updateState(int state) {
        if (state == this.getState()) return; // Don't triggers events

        var oldState = this.getState();
        this.setState(state);
        ticksSinceChange = getScene().getSceneTimeSeconds();
        this.getScene().broadcastPacket(new PacketGadgetStateNotify(this, state));
        getScene()
                .getScriptManager()
                .callEvent(
                        new ScriptArgs(
                                        this.getGroupId(),
                                        EventType.EVENT_GADGET_STATE_CHANGE,
                                        state,
                                        this.getConfigId())
                                .setParam3(oldState));
    }

    @Deprecated(forRemoval = true) // Dont use!
    public void setContent(GadgetContent content) {
        this.content = this.content == null ? content : this.content;
    }

    // TODO refactor
    public void buildContent() {
        if (this.getContent() != null
                || this.getGadgetData() == null
                || this.getGadgetData().getType() == null) {
            return;
        }

        this.content =
                switch (this.getGadgetData().getType()) {
                    case GatherPoint -> new GadgetGatherPoint(this);
                    case GatherObject -> {
                        var gatherObject = new GadgetGatherObject(this);
                        // Only breakable gather objects (ore veins, etc.) need an HP bar -
                        // plain click-to-gather gadgets (flowers, mushrooms...) must NOT get
                        // fight properties, or the client will route them through the combat
                        // "kill" flow, which drops a second, duplicate item on top of the one
                        // already granted directly in GadgetGatherObject#onInteract().
                        if (gatherObject.requiresBreaking()
                                && this.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP) <= 0f) {
                            this.setFightProperty(FightProperty.FIGHT_PROP_BASE_HP, 50f);
                            this.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, 50f);
                            this.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 50f);
                        }
                        yield gatherObject;
                    }
                    case Worktop, SealGadget -> new GadgetWorktop(this);
                    case RewardStatue -> new GadgetRewardStatue(this);
                    case Chest -> new GadgetChest(this);
                    case Gadget -> new GadgetObject(this);
                    case ViewPoint -> new GadgetViewPoint(this);
                    default -> null;
                };
    }

    @Override
    public void onInteract(Player player, GadgetInteractReq interactReq) {
        if (!this.interactEnabled) return;

        if (this.getContent() == null) {
            return;
        }

        boolean shouldDelete = this.getContent().onInteract(player, interactReq);

        if (shouldDelete) {
            this.getScene().killEntity(this);
        }
    }

    @Override
    public void onCreate() {
        // Lua event
        getScene()
                .getScriptManager()
                .callEvent(
                        new ScriptArgs(this.getGroupId(), EventType.EVENT_GADGET_CREATE, this.getConfigId()));

        // Check if this gadget is a fishing pool and populate it with fish
        this.checkAndPopulateFishPool();
    }

    @Override
    public void onRemoved() {
        super.onRemoved();
        if (!children.isEmpty()) {
            getScene().removeEntities(children, VisionTypeOuterClass.VisionType.VisionType_VISION_REMOVE);
            children.clear();
        }
    }

    @Override
    public void onDeath(int killerId) {
        super.onDeath(killerId); // Invoke super class's onDeath() method.

        // Trigger item drops for breakable ores / crates only.
        // NOTE: plain click-to-gather objects (flowers, mushrooms, etc.) already receive
        // their item directly in GadgetGatherObject#onInteract(); calling dropItems() here
        // unconditionally caused a *second*, physical item to spawn in the world for every
        // gather interaction (duplicate/"floating" item bug).
        if (this.getContent() instanceof GadgetGatherObject gatherObject
                && gatherObject.requiresBreaking()) {
            Player killerPlayer = null;
            GameEntity killer = this.getScene().getEntityById(killerId);
            if (killer instanceof EntityAvatar avatar) {
                killerPlayer = avatar.getPlayer();
            } else if (killer instanceof EntityClientGadget clientGadget && clientGadget.getOwner() != null) {
                killerPlayer = clientGadget.getOwner();
            } else {
                killerPlayer = this.getScene().getWorld().getHost();
            }
            if (killerPlayer != null) {
                gatherObject.dropItems(killerPlayer);
            }
        }

        if (this.getSpawnEntry() != null) {
            this.getScene().getDeadSpawnedEntities().add(getSpawnEntry());
        }
        if (getScene().getChallenge() != null) {
            getScene().getChallenge().onGadgetDeath(this);
        }
        getScene()
                .getScriptManager()
                .callEvent(
                        new ScriptArgs(this.getGroupId(), EventType.EVENT_ANY_GADGET_DIE, this.getConfigId()));

        SceneGroupInstance groupInstance =
                getScene().getScriptManager().getGroupInstanceById(this.getGroupId());

        if (groupInstance != null && metaGadget != null && metaGadget.isOneoff) {
            groupInstance.getDeadEntities().add(metaGadget.config_id);
        }
    }

    public boolean startPlatform() {
        if (routeConfig == null) {
            return false;
        }

        if (routeConfig.isStarted()) {
            return true;
        }

        if (routeConfig instanceof ConfigRoute configRoute) {
            var route = this.getScene().getSceneRouteById(configRoute.getRouteId());
            if (route != null) {
                var points = route.getPoints();
                if (points == null || points.length == 0) {
                    return false;
                }
                if (configRoute.getStartIndex() == points.length - 1) {
                    configRoute.setStartIndex(0);
                }
                val currIndex = configRoute.getStartIndex();

                Position prevpos;
                if (currIndex == 0) {
                    prevpos = getPosition();
                    this.getScene()
                            .getScriptManager()
                            .callEvent(
                                    new ScriptArgs(
                                                    this.getGroupId(),
                                                    EventType.EVENT_PLATFORM_REACH_POINT,
                                                    this.getConfigId(),
                                                    configRoute.getRouteId())
                                            .setParam3(0)
                                            .setEventSource(this.getConfigId()));
                } else {
                    prevpos = points[currIndex].getPos();
                }

                double time = 0;
                for (var i = currIndex; i < points.length; ++i) {
                    time += points[i].getPos().computeDistance(prevpos) / points[i].getTargetVelocity();
                    prevpos = points[i].getPos();
                    val I = i;
                    configRoute
                            .getScheduledIndexes()
                            .add(
                                    this.getScene()
                                            .getScheduler()
                                            .scheduleDelayedTask(
                                                    () -> {
                                                        if (points[I].isHasReachEvent() && I > currIndex) {
                                                            this.getScene()
                                                                    .getScriptManager()
                                                                    .callEvent(
                                                                            new ScriptArgs(
                                                                                            this.getGroupId(),
                                                                                            EventType.EVENT_PLATFORM_REACH_POINT,
                                                                                            this.getConfigId(),
                                                                                            configRoute.getRouteId())
                                                                                    .setParam3(I)
                                                                                    .setEventSource(this.getConfigId()));
                                                        }
                                                        configRoute.setStartIndex(I);
                                                        this.position.set(points[I].getPos());
                                                        if (I == points.length - 1) {
                                                            configRoute.setStarted(false);
                                                        }
                                                    },
                                                    (int) time));
                }
            }
        }

        getScene().broadcastPacket(new PacketSceneTimeNotify(getScene()));
        routeConfig.startRoute(getScene());
        getScene().broadcastPacket(new PacketPlatformStartRouteNotify(this));

        return true;
    }

    public boolean stopPlatform() {
        if (routeConfig == null) {
            return false;
        }

        if (!routeConfig.isStarted()) {
            return true;
        }

        if (routeConfig instanceof ConfigRoute configRoute) {
            for (var task : configRoute.getScheduledIndexes()) {
                this.getScene().getScheduler().cancelTask(task);
            }
            configRoute.getScheduledIndexes().clear();
        }

        routeConfig.stopRoute(getScene());
        getScene().broadcastPacket(new PacketPlatformStopRouteNotify(this));

        return true;
    }

    /** Gadget id of the wild fishing spots ("FishPool" type in GadgetExcelConfigData). */
    public static final int FISH_POOL_GADGET_ID = 70950099;

    public void checkAndPopulateFishPool() {
        var poolData = resolveFishPoolData();
        if (poolData == null
                || poolData.getStockList() == null
                || poolData.getStockList().isEmpty()) {
            return;
        }

        populateFishPool(poolData);
    }

    /**
     * Resolves the FishPoolExcelConfigData entry of this gadget. The official data source is the
     * scene group's "fishing_id" field of the fishing shoal gadget (e.g. scene 3 group 133002054,
     * gadget 54001 -> pool 1008). Falls back to config/gadget id lookups for home world/legacy
     * pools.
     */
    public FishPoolData resolveFishPoolData() {
        boolean isPoolGadget = this.getGadgetId() == FISH_POOL_GADGET_ID;
        if (!isPoolGadget && this.metaGadget != null && this.metaGadget.fishing_id > 0) {
            isPoolGadget = true;
        }
        if (!isPoolGadget
                && this.getGadgetData() != null
                && this.getGadgetData().getJsonName() != null) {
            String name = this.getGadgetData().getJsonName().toLowerCase();
            if (name.contains("fishingshoal") || name.contains("fishpool") || name.contains("fishing")) {
                isPoolGadget = true;
            }
        }

        if (!isPoolGadget) return null;

        // Official: the group's fishing_id references FishPoolExcelConfigData directly.
        if (this.metaGadget != null && this.metaGadget.fishing_id > 0) {
            var poolData = GameData.getFishPoolDataMap().get(this.metaGadget.fishing_id);
            if (poolData != null) return poolData;
        }

        // Legacy fallbacks (e.g. home world pools without group fishing data).
        var poolData = GameData.getFishPoolDataMap().get(this.getConfigId());
        if (poolData == null) {
            poolData = GameData.getFishPoolDataMap().get(this.getGadgetId());
        }
        return poolData;
    }

    public void populateFishPool(FishPoolData poolData) {
        // Clear previous fish if any
        if (!this.getChildren().isEmpty()) {
            this.getScene()
                    .removeEntities(
                            this.getChildren(),
                            VisionTypeOuterClass.VisionType.VisionType_VISION_REMOVE);
            this.getChildren().clear();
        }

        int maxToSpawn = poolData.getMaxNum() > 0 ? poolData.getMaxNum() : 5;
        List<FishStockData> activeStocks = getActiveFishStocks(poolData);
        if (activeStocks.isEmpty()) return;

        int spawnedCount = 0;

        // Stock guarantee: specific fish are guaranteed to be present in the pool.
        if (poolData.getStockGuarantee() != null) {
            for (var entry : poolData.getStockGuarantee().entrySet()) {
                if (spawnedCount >= maxToSpawn) break;

                int guaranteedFishId;
                try {
                    guaranteedFishId = Integer.parseInt(entry.getKey());
                } catch (NumberFormatException ignored) {
                    continue;
                }

                int guaranteedCount = Math.min(entry.getValue(), maxToSpawn - spawnedCount);
                for (int i = 0; i < guaranteedCount; i++) {
                    if (spawnFishInPool(guaranteedFishId)) {
                        spawnedCount++;
                    }
                }
            }
        }

        // Fill the remaining slots with weighted random rolls from the active stocks.
        int attempts = 0;
        while (spawnedCount < maxToSpawn && attempts++ < maxToSpawn * 3) {
            int fishId = rollFishIdFromStocks(activeStocks);
            if (fishId > 0 && spawnFishInPool(fishId)) {
                spawnedCount++;
            }
        }
    }

    /** Spawns a single replacement fish if the pool is below its capacity (fish restock). */
    public void restockFishPool() {
        // Skip if the pool gadget is no longer part of the scene.
        if (this.getScene().getEntityById(this.getId()) != this) return;

        var poolData = resolveFishPoolData();
        if (poolData == null) return;

        int maxToSpawn = poolData.getMaxNum() > 0 ? poolData.getMaxNum() : 5;
        long liveFish =
                this.getChildren().stream()
                        .filter(
                                e ->
                                        e instanceof EntityMonster m
                                                && m.getFishId() > 0
                                                && this.getScene().isInScene(e))
                        .count();
        if (liveFish >= maxToSpawn) return;

        int fishId = rollFishIdFromStocks(getActiveFishStocks(poolData));
        if (fishId > 0) {
            spawnFishInPool(fishId);
        }
    }

    /** Returns the stocks that are currently active, based on the in-game time of day. */
    private List<FishStockData> getActiveFishStocks(FishPoolData poolData) {
        boolean isDay = isSceneDayTime();
        List<FishStockData> stocks = new ArrayList<>();
        for (int stockId : poolData.getStockList()) {
            var stock = GameData.getFishStockDataMap().get(stockId);
            if (stock == null || stock.getFishWeight() == null || stock.getFishWeight().isEmpty()) {
                continue;
            }

            String type = stock.getType();
            if ("FISH_STOCK_TYPE_DAY".equals(type) && !isDay) continue;
            if ("FISH_STOCK_TYPE_NIGHT".equals(type) && isDay) continue;

            stocks.add(stock);
        }
        return stocks;
    }

    /** Weighted random pick of a fish id from the given stocks. */
    private int rollFishIdFromStocks(List<FishStockData> stocks) {
        if (stocks == null || stocks.isEmpty()) return 0;

        var stock = stocks.get((int) (Math.random() * stocks.size()));
        int totalWeight = 0;
        for (int weight : stock.getFishWeight().values()) {
            totalWeight += weight;
        }
        if (totalWeight <= 0) return 0;

        int randomWeight = (int) (Math.random() * totalWeight);
        int currentWeight = 0;
        for (var entry : stock.getFishWeight().entrySet()) {
            currentWeight += entry.getValue();
            if (randomWeight < currentWeight) {
                try {
                    return Integer.parseInt(entry.getKey());
                } catch (NumberFormatException ignored) {
                    return 0;
                }
            }
        }
        return 0;
    }

    /** Day (06:00 - 18:00) vs Night (18:00 - 06:00) according to the in-game clock. */
    private boolean isSceneDayTime() {
        int secondsOfDay = (int) ((this.getScene().getWorld().getWorldTime() / 1000L) % 86400L);
        return secondsOfDay >= 21600 && secondsOfDay < 64800;
    }

    private boolean spawnFishInPool(int fishId) {
        var fishData = GameData.getFishDataMap().get(fishId);
        if (fishData == null) return false;

        var monsterData = GameData.getMonsterDataMap().get(fishData.getMonsterId());
        if (monsterData == null) return false;

        // Spread in a circle around the fishing spot (official fish swim ~1.5m - 4m from the center)
        float angle = (float) (Math.random() * 2 * Math.PI);
        float radius = 1.5f + (float) (Math.random() * 2.5f);

        Position pos = new Position(
                this.getPosition().getX() + (float) (radius * Math.cos(angle)),
                this.getPosition().getY(),
                this.getPosition().getZ() + (float) (radius * Math.sin(angle))
        );

        Position rot = new Position(0, (float) (Math.random() * 360), 0);

        EntityMonster fish = new EntityMonster(this.getScene(), monsterData, pos, rot, 1);
        fish.setFishId(fishId);
        fish.setFishPoolEntityId(this.getId());
        fish.setFishPoolPos(this.getPosition());
        fish.setFishPoolGadgetId(this.getGadgetId());
        fish.setPoseId(fishData.getInitPose());

        this.getScene().addEntity(fish);
        this.getChildren().add(fish); // Automatically cleaned up if pool despawns
        return true;
    }

    @Override
    public SceneEntityInfo toProto() {
        EntityAuthorityInfo authority =
                EntityAuthorityInfo.newBuilder()
                        .setAbilityInfo(AbilitySyncStateInfo.newBuilder())
                        .setRendererChangedInfo(EntityRendererChangedInfo.newBuilder())
                        .setAiInfo(
                                SceneEntityAiInfo.newBuilder().setIsAiOpen(true))
                        .setBornPos(bornPos.toProto())
                        .build();

        SceneEntityInfo.Builder entityInfo =
                SceneEntityInfo.newBuilder()
                        .setEntityId(getId())
                        .setEntityType(ProtEntityType.ProtEntityType_PROT_ENTITY_GADGET)
                        .setMotionInfo(
                                MotionInfo.newBuilder()
                                        .setPos(getPosition().toProto())
                                        .setRot(getRotation().toProto())
                                        .setSpeed(Vector.newBuilder()))
                        .addAnimatorParaList(AnimatorParameterValueInfoPair.newBuilder())
                        .setEntityClientData(EntityClientData.newBuilder())
                        .setEntityAuthorityInfo(authority)
                        .setLifeState(1);

        PropPair pair =
                PropPair.newBuilder()
                        .setType(PlayerProperty.PROP_LEVEL.getId())
                        .setPropValue(ProtoHelper.newPropValue(PlayerProperty.PROP_LEVEL, 1))
                        .build();
        entityInfo.addPropList(pair);

        // We do not use the getter to null check because the getter will create a fight prop map if it
        // is null
        if (this.fightProperties != null) {
            addAllFightPropsToEntityInfo(entityInfo);
        }

        var gadgetInfo =
                SceneGadgetInfo.newBuilder()
                        .setGadgetId(this.getGadgetId())
                        .setGroupId(this.getGroupId())
                        .setConfigId(this.getConfigId())
                        .setGadgetState(this.getState())
                        .setIsEnableInteract(this.interactEnabled)
                        .setAuthorityPeerId(this.getScene().getWorld().getHostPeerId());

        if (this.metaGadget != null) {
            gadgetInfo.setDraftId(this.metaGadget.draft_id);
        }

        if (owner != null) {
            gadgetInfo.setOwnerEntityId(owner.getId());
        }

        // Fish pool info (wild fishing spots, gadget 70950099): officially sent as
        // FishPoolInfo { pool_id, fish_area_list }, taken from the group's fishing_id/fishing_areas.
        int poolId = 0;
        if (this.metaGadget != null && this.metaGadget.fishing_id > 0) {
            poolId = this.metaGadget.fishing_id;
        } else {
            var pData = GameData.getFishPoolDataMap().get(this.getConfigId());
            if (pData != null) {
                poolId = pData.getId();
            } else if (this.getGadgetId() == FISH_POOL_GADGET_ID) {
                poolId = this.getConfigId();
            }
        }

        if (poolId > 0) {
            var poolInfo = FishPoolInfoOuterClass.FishPoolInfo.newBuilder()
                    .setPoolId(poolId)
                    .setTodayFishNum(0);
            if (this.metaGadget != null && this.metaGadget.fishing_areas != null) {
                poolInfo.addAllFishAreaList(this.metaGadget.fishing_areas);
            }
            gadgetInfo.setFishPoolInfo(poolInfo);
        }

        if (this.getContent() != null) {
            this.getContent().onBuildProto(gadgetInfo);
        }

        if (routeConfig != null) {
            gadgetInfo.setPlatform(getPlatformInfo());
        }

        entityInfo.setGadget(gadgetInfo);

        return entityInfo.build();
    }

    public PlatformInfoOuterClass.PlatformInfo.Builder getPlatformInfo() {
        if (routeConfig != null) {
            return routeConfig.toProto();
        }

        return PlatformInfoOuterClass.PlatformInfo.newBuilder();
    }
}