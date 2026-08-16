package emu.grasscutter.game.world;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.GameDepot;
import emu.grasscutter.data.binout.SceneNpcBornEntry;
import emu.grasscutter.data.binout.routes.Route;
import emu.grasscutter.data.binout.config.ConfigEntityGadget;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.data.excels.codex.CodexAnimalData;
import emu.grasscutter.data.excels.monster.MonsterData;
import emu.grasscutter.data.excels.scene.SceneData;
import emu.grasscutter.data.excels.world.WorldLevelData;
import emu.grasscutter.data.server.Grid;
import emu.grasscutter.game.avatar.Avatar;
import emu.grasscutter.game.dungeons.DungeonManager;
import emu.grasscutter.game.dungeons.DungeonSettleListener;
import emu.grasscutter.game.dungeons.challenge.WorldChallenge;
import emu.grasscutter.game.dungeons.enums.DungeonPassConditionType;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.entity.gadget.GadgetGatherObject;
import emu.grasscutter.game.entity.gadget.GadgetGatherPoint;
import emu.grasscutter.game.entity.gadget.GadgetWorktop;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.managers.blossom.BlossomManager;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.player.TeamInfo;
import emu.grasscutter.game.props.*;
import emu.grasscutter.game.quest.QuestGroupSuite;
import emu.grasscutter.game.world.data.TeleportProperties;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.net.proto.AttackResultOuterClass.AttackResult;
import emu.grasscutter.net.proto.ChangeHpDebtsReasonOuterClass;
import emu.grasscutter.net.proto.ChangeHpReasonOuterClass.ChangeHpReason;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass.PropChangeReason;
import emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType;
import emu.grasscutter.scripts.SceneIndexManager;
import emu.grasscutter.scripts.SceneScriptManager;
import emu.grasscutter.scripts.constants.EventType;
import emu.grasscutter.scripts.constants.ScriptGadgetState;
import emu.grasscutter.scripts.data.SceneBlock;
import emu.grasscutter.scripts.data.SceneGroup;
import emu.grasscutter.scripts.data.ScriptArgs;
import emu.grasscutter.server.event.entity.EntityCreationEvent;
import emu.grasscutter.server.event.player.PlayerTeleportEvent;
import emu.grasscutter.server.packet.send.*;
import emu.grasscutter.server.scheduler.ServerTaskScheduler;
import emu.grasscutter.utils.algorithms.KahnsSort;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;
import javax.annotation.Nullable;
import lombok.*;
import emu.grasscutter.game.props.ClimateType;
import emu.grasscutter.game.props.EnterReason;
import emu.grasscutter.net.proto.EnterTypeOuterClass;
import emu.grasscutter.server.packet.send.PacketScenePlayerLocationNotify;

import static emu.grasscutter.GameConstants.ENTITY_ID_BIT_SHIFT;

public class Scene {
    @Getter private final World world;
    @Getter private final SceneData sceneData;
    @Getter private final List<Player> players;
    @Getter private final Map<Integer, GameEntity> entities;
    @Getter private final Map<Integer, GameEntity> weaponEntities;
    @Getter private final Set<SpawnDataEntry> spawnedEntities;
    @Getter private final Set<SpawnDataEntry> deadSpawnedEntities;
	private final Set<SpawnDataEntry> pendingStaticRespawns;
    @Getter private final Set<SceneBlock> loadedBlocks;
    @Getter private final Set<SceneGroup> loadedGroups;
    @Getter private final BlossomManager blossomManager;
    private final HashSet<Integer> unlockedForces;
    private final long startWorldTime;
    @Getter @Setter DungeonManager dungeonManager;
    @Getter Int2ObjectMap<Route> sceneRoutes;
    private Set<SpawnDataEntry.GridBlockId> loadedGridBlocks;
	private Set<SpawnDataEntry.GridBlockId> loadedMissingScriptGridBlocks;
    @Getter @Setter private boolean dontDestroyWhenEmpty;
    @Getter private final SceneScriptManager scriptManager;
    @Getter @Setter private WorldChallenge challenge;
    @Getter private List<DungeonSettleListener> dungeonSettleListeners;
    @Getter @Setter private int prevScene;
    @Getter @Setter private int prevScenePoint;
    @Getter @Setter private int killedMonsterCount;
    private Set<SceneNpcBornEntry> npcBornEntrySet;
    @Getter private boolean finishedLoading = false;
    @Getter protected int tickCount = 0;
    @Getter private boolean isPaused = false;
	
	private static final int ICEWIND_SCENE_ID = 3;
	private static final int ICEWIND_GROUP_ID = 133402002;
	private static final int ICEWIND_TALK_CONFIG_ID = 2004;
	private static final int ICEWIND_PROP_CONFIG_ID = 2006;
	private static final int ICEWIND_PROP_GADGET_ID = 70330531;
	private static final int ICEWIND_BLOCK_ID = 334;
	private static final Position ICEWIND_PROP_POS = new Position(3603.317f, 438.115f, 3814.537f);
	private static final Position ICEWIND_PROP_ROT = new Position(0f, 221.7f, 0f);
	private static final Set<Integer> ICEWIND_FALLBACK_MONSTER_IDS = Set.of(24070101, 24070102, 24070201, 24070202, 24070301);
	
	private static final int ICEWIND_FALLBACK_REFRESH_SECONDS = 24;
	private static final int ICEWIND_FALLBACK_LOW_HP_REFRESH_SECONDS = 8;
	private static final float ICEWIND_FALLBACK_CLIMAX_HP_RATIO = 0.715f;
	
	private static final int ICEWIND_WEATHER_ID = 5009;
	private static final int ICEWIND_DEFAULT_WEATHER_ID = 0;

	private static final Position ICEWIND_PLAYER_START_POS =
			new Position(3588.0f, 438.2f, 3804.0f);

	private static final Position ICEWIND_PLAYER_START_ROT =
			new Position(0f, 45f, 0f);
			
	private static final int PMA_ROUTE_BARRIER_SCENE_ID = 3;
	private static final int PMA_ROUTE_BARRIER_GROUP_ID = 133220374;
	private static final int PMA_ROUTE_BARRIER_CONFIG_A = 374001;
	private static final int PMA_ROUTE_BARRIER_CONFIG_B = 374002;
	private static final int PMA_ROUTE_BARRIER_GADGET_A = 70290155;
	private static final int PMA_ROUTE_BARRIER_GADGET_B = 70290156;
	
	private static final int GOLDEN_WOLFLORD_SCENE_ID = 3;
	private static final int GOLDEN_WOLFLORD_GROUP_ID = 133225275;
	private static final int GOLDEN_WOLFLORD_CONFIG_ID = 275002;
	private static final int GOLDEN_WOLFLORD_MONSTER_ID = 22060101;
	private static final int GOLDEN_WOLFLORD_WEATHER_ID = 3321;
	private static final int GOLDEN_WOLFLORD_DEFAULT_WEATHER_ID = 0;

	private static final int GOLDEN_WOLFLORD_BLOSSOM_CONFIG_ID = 275007;
	private static final int GOLDEN_WOLFLORD_BLOSSOM_GADGET_ID = 70210106;

	private static final float GOLDEN_WOLFLORD_MIN_DISPLAY_HP_RATIO = 0.72f;
	private static final float GOLDEN_WOLFLORD_WEATHER_RADIUS = 90.0f;

	private static final Position GOLDEN_WOLFLORD_ARENA_POS =
			new Position(-6657.744f, 193.481f, -2661.123f);

	private boolean goldenWolflordWeatherActive = false;

	private final Map<Integer, Float> goldenWolflordVirtualHp = new ConcurrentHashMap<>();
	private final Map<Integer, Float> goldenWolflordVirtualMaxHp = new ConcurrentHashMap<>();

	private boolean icewindSuiteFallbackWeatherActive = false;
	
	private static final float ICEWIND_FALLBACK_SAFE_HP_RATIO = 0.80f;
	private static final float ICEWIND_FALLBACK_MIN_DISPLAY_HP_RATIO = 0.78f;

	private final Map<Integer, Float> icewindFallbackVirtualHp = new ConcurrentHashMap<>();
	private final Map<Integer, Float> icewindFallbackVirtualMaxHp = new ConcurrentHashMap<>();

	private final Map<Integer, Long> pendingIcewindSuiteArenaTeleports = new ConcurrentHashMap<>();
	private final Map<Integer, Integer> icewindFallbackSpawnTimes = new ConcurrentHashMap<>();
	private final Map<Integer, Float> icewindFallbackLastHpRatios = new ConcurrentHashMap<>();

    private final List<Runnable> afterLoadedCallbacks = new ArrayList<>();
    private final List<Runnable> afterHostInitCallbacks = new ArrayList<>();

	private static final int SEIRAI_SCENE_ID = 3;

	private static final int SEIRAI_WEATHER_DEFAULT = 0;
	private static final int SEIRAI_WEATHER_THUNDER_MANIFESTATION = 3264;
	private static final int SEIRAI_WEATHER_AMAKUMO_LOWER = 3219;
	private static final int SEIRAI_WEATHER_SEIRAIMARU = 3165;
	private static final int SEIRAI_WEATHER_INITIAL_ISLAND = 3165;
	private static final int SEIRAI_WEATHER_ASASE_SHRINE = 3422;

	private static final int SANGONOMIYA_WEATHER_GENERAL = 3067;

	private static final int TSURUMI_WEATHER_GENERAL = 3073;

	private static final int HIISI_WEATHER_GENERAL = 6345;

	private static final Position THUNDER_MANIFESTATION_ARENA_POS =
			new Position(-4707.378f, 479.99323f, -4258.842f);

	private static final Position SEIRAI_AMAKUMO_LOWER_POS =
			new Position(-4664.7285f, 198.70815f, -4228.009f);

	private static final Position SEIRAI_SEIRAIMARU_POS =
			new Position(-4406.118f, 232.224f, -3835.204f);

	private static final Position SEIRAI_INITIAL_ISLAND_POS =
			new Position(-4254.716f, 200.696f, -3929.955f);

	private static final Position SEIRAI_ASASE_SHRINE_POS =
			new Position(-4700.761f, 205.0f, -3674.826f);

	private static final float SEIRAI_ASASE_SHRINE_RADIUS = 165.0f;

	private static final float THUNDER_MANIFESTATION_WEATHER_RADIUS = 230.0f;
	private static final float THUNDER_MANIFESTATION_MIN_WEATHER_Y = 350.0f;

	private static final float SEIRAI_SEIRAIMARU_RADIUS = 220.0f;
	private static final float SEIRAI_INITIAL_ISLAND_RADIUS = 430.0f;
	private static final float SEIRAI_AMAKUMO_LOWER_RADIUS = 560.0f;
	
	private final Map<Integer, Integer> seiraiFallbackWeatherByUid = new ConcurrentHashMap<>();
	
	private final Map<Integer, Integer> dragonspineFallbackWeatherByUid = new ConcurrentHashMap<>();

	private static final int DRAGONSPINE_SCENE_ID = 3;

	private static final int DRAGONSPINE_WEATHER_DEFAULT = 0;
	private static final int DRAGONSPINE_WEATHER_GENERAL = 2022;
	private static final int DRAGONSPINE_WEATHER_PEAK = 2023;
	private static final int DRAGONSPINE_WEATHER_CRYO_HYPOSTASIS = 2125;

	private static final int SHEER_COLD_MAX = 10000;

	// Retail baseline:
	// +1% per second in Subzero Climate.
	// -5% per second outside Subzero Climate.
	private static final int SHEER_COLD_GAIN_PER_SECOND = 100;
	private static final int SHEER_COLD_DRAIN_PER_SECOND = 500;
	private static final int SHEER_COLD_WARMTH_DRAIN_PER_SECOND = 2500;

	private static final float SHEER_COLD_GADGET_WARMTH_RADIUS = 8.0f;
	private static final float SHEER_COLD_SCENE_POINT_WARMTH_RADIUS = 10.0f;

	/*
	 * Confirmed by REL6.6 runtime probes:
	 * 70310015 = lit Dragonspine fire basin / campfire
	 * 70310022 = lit Dragonspine bonfire ("Bornfires" in the internal name)
	 * 70310023 = Frostbearing Tree invisible heat producer
	 */
	private static final Set<Integer> DRAGONSPINE_CONFIRMED_WARMTH_GADGET_IDS =
			Set.of(
					70310015,
					70310022,
					70310023);

	private static final Set<String> DRAGONSPINE_WARMTH_NAME_HINTS = Set.of(
			"campfire",
			"bonfire",
			"firebasin",
			"bornfires",
			"cookpot",
			"cooking",
			"torch",
			"brazier",
			"heatsource",
			"heat_source",
			"invisibleheat",
			"heat_producer",
			"warming",
			"seelie",
			"warmseelie",
			"warm_seelie",
			"frostbearing",
			"ancientbloodtree",
			"dragonspinetree");

	private final Map<Integer, Long> sheerColdLastUpdateByUid =
        	new ConcurrentHashMap<>();

	private final Map<Integer, Integer> sheerColdLastDamageByUid =
        	new ConcurrentHashMap<>();

	/*
	 * Main Dragonspine perimeter in the X/Z plane.
	 *
	 * These points were sampled around the mountain's weather boundary and are
	 * arranged continuously around the perimeter. Y is intentionally ignored so the same horizontal
	 * boundary applies at every elevation; the summit weather is still selected separately by DRAGONSPINE_PEAK_MIN_Y.
	 *
	 * A perimeter polygon is used instead of several overlapping circles. This
	 * removes the gaps between old circles and avoids their weather spill into
	 * nearby Mondstadt/Liyue terrain.
	 */
	private static final double[][] DRAGONSPINE_WEATHER_PERIMETER_XZ = {
			{556.2268, -790.966},
			{595.8846, -974.2335},
			{499.84894, -1207.7948},
			{831.9527, -1391.7156},
			{1011.6961, -1356.859},
			{1081.2991, -1340.9827},
			{1163.5707, -1266.6521},
			{1246.9128, -1248.8917},
			{1280.1598, -1237.4648},
			{1403.036, -1157.1901},
			{1549.2825, -1036.5328},
			{1561.4319, -1000.49426},
			{1603.198, -953.9342},
			{1566.95, -897.82227},
			{1541.3408, -755.0368},
			{1535.5869, -635.9338},
			{1510.0792, -557.6269},
			{1506.585, -512.81573},
			{1496.8411, -461.16733},
			{1431.2866, -438.4074},
			{1428.5781, -355.90057},
			{1266.4911, -363.1467},
			{1133.2083, -321.52982},
			{1064.3125, -334.4753},
			{968.65173, -357.35547},
			{801.35126, -408.56415},
			{729.0226, -516.79865},
			{730.2262, -521.92706},
			{691.1538, -549.14105},
			{667.6577, -595.8496},
			{646.4743, -621.7398},
			{613.2624, -694.8876},
			{571.9562, -773.13544}
	};

	/*
	 * Sangonomiya Shrine perimeter in the X/Z plane.
	 *
	 * These points are already arranged continuously around the boundary.
	 * Y is intentionally ignored so the same horizontal perimeter applies at
	 * every elevation around the shrine, waterfalls, cliffs, and lower paths.
	 */
	private static final double[][] SANGONOMIYA_WEATHER_PERIMETER_XZ = {
			{-3945.285, -1056.3728},
			{-3897.814, -1065.2528},
			{-3858.6768, -1052.088},
			{-3810.4138, -1038.7576},
			{-3777.267, -1032.6497},
			{-3733.0571, -1004.1456},
			{-3690.243, -979.49835},
			{-3645.9495, -952.5349},
			{-3647.7095, -946.2949},
			{-3638.323, -930.9094},
			{-3611.2878, -878.5145},
			{-3593.1438, -831.94977},
			{-3566.8086, -767.417},
			{-3574.542, -738.89374},
			{-3594.4153, -686.7346},
			{-3618.0378, -644.9324},
			{-3644.0693, -612.40765},
			{-3641.6382, -590.1775},
			{-3676.3137, -573.8977},
			{-3698.9832, -587.8092},
			{-3759.7224, -583.33984},
			{-3777.6543, -606.3279},
			{-3834.4744, -614.4811},
			{-3903.8596, -620.0091},
			{-3949.2917, -660.18994},
			{-3970.977, -717.8509},
			{-3992.4358, -769.68384},
			{-3987.752, -821.4353},
			{-3994.314, -860.78925},
			{-3984.6733, -909.70215},
			{-3981.5112, -938.4824},
			{-3976.57, -977.2434},
			{-3967.6797, -1023.8548}
	};
	
	/*
	 * Tsurumi Island weather perimeter in the X/Z plane.
	 *
	 * Y is deliberately ignored so caves, cliffs, elevated terrain, and
	 * underground areas inside the horizontal island boundary receive the
	 * same weather profile.
	 *
	 * The Golden Wolflord arena is inside this polygon. Its encounter weather
	 * therefore receives explicit priority in applySeiraiFallbackWeather().
	 */
	private static final double[][] TSURUMI_WEATHER_PERIMETER_XZ = {
			{-6135.585, -3269.1047},
			{-6366.2153, -3204.9846},
			{-6274.818, -3282.8364},
			{-6415.3, -3135.5952},
			{-6523.622, -2891.8843},
			{-6618.703, -2761.4695},
			{-6818.6978, -2715.6787},
			{-6748.992, -2495.455},
			{-6412.203, -2270.3623},
			{-5997.094, -2266.4353},
			{-5711.072, -2349.364},
			{-5592.6123, -2621.8452},
			{-5809.151, -2797.5205},
			{-5878.544, -3036.1528},
			{-6063.014, -3152.9297}
	};
	
	/*
	 * Hiisi Island weather perimeter in the X/Z plane.
	 *
	 * Y is deliberately ignored so the same atmosphere applies across the
	 * island regardless of elevation, including cliffs, ruins, lower terrain,
	 * and elevated areas.
	 */
	private static final double[][] HIISI_WEATHER_PERIMETER_XZ = {
			{1677.0642, 10158.858},
			{1914.9126, 10237.664},
			{2119.4636, 10353.916},
			{2372.5654, 10551.104},
			{2557.975, 10665.884},
			{2474.5815, 10838.049},
			{2375.691, 10923.986},
			{2198.2034, 10957.586},
			{2151.9104, 10968.8545},
			{2144.7458, 11281.389},
			{1995.958, 11272.563},
			{1665.4482, 11270.768},
			{1458.9417, 11206.139},
			{1394.2742, 10911.5625},
			{1383.4873, 10448.144},
			{1618.8003, 10155.363}
	};

	/*
	 * Actual Cryo Hypostasis arena center, based on the measured
	 * center and arena boundaries.
	 */
	private static final Position DRAGONSPINE_CRYO_HYPOSTASIS_ARENA_POS =
			new Position(1171.1259f, 285.6235f, -547.84076f);

	/*
	 * Switch to the dedicated peak profile at or above this height.
	 * Anything below it returns to the ordinary Dragonspine profile.
	 */
	private static final float DRAGONSPINE_PEAK_MIN_Y = 438.0f;

	/*
	 * The measured arena edges are roughly 27–31 units from the
	 * center. A radius of 40 leaves a small safety margin without
	 * extending deeply outside the arena.
	 */
	private static final float DRAGONSPINE_CRYO_HYPOSTASIS_WEATHER_RADIUS = 40.0f;

	private static final int OCEANID_SCENE_ID = 3;
	private static final int OCEANID_WEATHER_DEFAULT = 0;
	private static final int OCEANID_WEATHER_ID = 2021;
	private static final Position OCEANID_ARENA_POS = new Position(1788.556f, 200.550f, 277.001f);
	private static final int OCEANID_LEGACY_MONSTER_ID = 20050101;
	private static final int OCEANID_DIRECT_MONSTER_ID = 20050102;
	private static final int OCEANID_DIRECT_MUTE_MONSTER_ID = 20050103;
	private static final Position OCEANID_DIRECT_BOSS_POS = new Position(1788.556f, 200.550f, 277.001f);
	private static final Position OCEANID_DIRECT_BOSS_ROT = new Position(0f, 180f, 0f);
	private static final Position OCEANID_BLOSSOM_POS = new Position(1788.556f, 200.5f, 277.001f);
	private static final Position OCEANID_BLOSSOM_ROT = new Position(0f, 0f, 0f);
	private static final float OCEANID_WEATHER_RADIUS = 75.0f;
	
	private final Map<Integer, Integer> oceanidFallbackWeatherByUid = new ConcurrentHashMap<>();
	
	private static final Set<Integer> OCEANID_FALLBACK_MIMIC_CONFIGS = Set.of(
			769012, 769013, 769014,
			769015, 769016, 769017, 769018, 769019, 769020,
			769021, 769022, 769023,
			769024, 769025,
			769031, 769032, 769033,
			769034, 769035, 769036, 769037, 769038, 769039,
			769040, 769041, 769042,
			769043, 769044);
	
	private static final int OCEANID_GROUP_ID = 133102769;
	private static final int OCEANID_BOSS_CONFIG_ID = 769026;
	private static final int OCEANID_BLOSSOM_CONFIG_ID = 769054;
	
	private static final float OCEANID_ENCOUNTER_RADIUS = 78.0f;
	private static final float OCEANID_ENCOUNTER_RESET_RADIUS = 135.0f;
	private static final float OCEANID_FALLBACK_MIN_DISPLAY_HP_RATIO = 0.08f;
	
	private static final int OCEANID_FALLBACK_WAVE_COUNT = 3;
	private static final int OCEANID_MIMICS_PER_WAVE = 5;
	
	private static final List<Integer> OCEANID_PLATFORM_CONFIGS = List.of(
			769001, 769002, 769003,
			769004, 769005, 769006,
			769007, 769008, 769009);
	
	private static final Set<Integer> OCEANID_CONTROL_GADGET_CONFIGS = Set.of(
			769010, // HP checker
			769011, 769045, 769046, 769047, // boss/operator gadgets
			769055, // anchor
			769062, 769063, 769064, 769065 // old starter worktop gadgets
	);
	
	private static final List<Integer> OCEANID_FALLBACK_MIMIC_POOL = List.of(
			769012, 769013, 769014,
			769015, 769016, 769017, 769018, 769019, 769020,
			769021, 769022, 769023,
			769024, 769025,
			769031, 769032, 769033,
			769034, 769035, 769036, 769037, 769038, 769039,
			769040, 769041, 769042,
			769043, 769044);
	
	private int oceanidFallbackBossEntityId = 0;
	private int oceanidFallbackWaveIndex = 0;
	private boolean oceanidFallbackEncounterActive = false;
	private boolean oceanidFallbackWaitingForLeave = false;
	private boolean oceanidFallbackDefeatedUntilLeave = false;
	private boolean oceanidFallbackFinishing = false;
	private long oceanidFallbackLastStartMs = 0L;
	
	private final Map<Integer, Float> oceanidFallbackVirtualHp = new ConcurrentHashMap<>();
	private final Map<Integer, Float> oceanidFallbackVirtualMaxHp = new ConcurrentHashMap<>();
	private final Map<Integer, Float> sheerColdMeterByUid = new ConcurrentHashMap<>();

    @Getter private GameEntity sceneEntity;
    @Getter private final ServerTaskScheduler scheduler;

    public Scene(World world, SceneData sceneData) {
        this.world = world;
        this.sceneData = sceneData;
        this.players = new CopyOnWriteArrayList<>();
        this.entities = new ConcurrentHashMap<>();
        this.weaponEntities = new ConcurrentHashMap<>();

        this.prevScene = 3;
        this.sceneRoutes = GameData.getSceneRoutes(getId());

        this.startWorldTime = world.getWorldTime();

        this.spawnedEntities = ConcurrentHashMap.newKeySet();
        this.deadSpawnedEntities = ConcurrentHashMap.newKeySet();
		this.pendingStaticRespawns = ConcurrentHashMap.newKeySet();
        this.loadedBlocks = ConcurrentHashMap.newKeySet();
        this.loadedGroups = ConcurrentHashMap.newKeySet();
        this.loadedGridBlocks = new HashSet<>();
		this.loadedMissingScriptGridBlocks = new HashSet<>();
        this.npcBornEntrySet = ConcurrentHashMap.newKeySet();
        this.scriptManager = new SceneScriptManager(this);
        this.blossomManager = new BlossomManager(this);
        this.unlockedForces = new HashSet<>();
        this.sceneEntity = new EntityScene(this);
        this.scheduler = new ServerTaskScheduler();
    }

    public int getId() {
        return sceneData.getId();
    }

    public SceneType getSceneType() {
        return getSceneData().getSceneType();
    }

    public int getPlayerCount() {
        return this.getPlayers().size();
    }

    public Player getHost() {
        return this.getWorld().getHost();
    }

    public GameEntity getEntityById(int id) {

        if (id == 0x13800001) return this.sceneEntity;
        else if (id == this.getWorld().getLevelEntityId()) return this.getWorld().getEntity();

        var teamEntityPlayer =
                players.stream().filter(p -> p.getTeamManager().getEntity().getId() == id).findAny();
        if (teamEntityPlayer.isPresent()) return teamEntityPlayer.get().getTeamManager().getEntity();

        var entity = this.entities.get(id);
        if (entity == null) entity = this.weaponEntities.get(id);
        if (entity == null && (id >> ENTITY_ID_BIT_SHIFT) == EntityIdType.AVATAR.getId()) {
            for (var player : getPlayers()) {
                for (var avatar : player.getTeamManager().getActiveTeam()) {
                    if (avatar.getId() == id) return avatar;
                }
            }
        }

        if (entity == null && (id >> ENTITY_ID_BIT_SHIFT) == EntityIdType.WEAPON.getId()) {
            for (var player : this.getPlayers()) {
                for (var avatar : player.getTeamManager().getActiveTeam()) {
                    if (avatar.getWeaponEntityId() == id) return avatar;
                }
            }
        }

        return entity;
    }

    public GameEntity getFirstEntityByConfigId(int configId) {
        return this.entities.values().stream()
                .filter(x -> x.getConfigId() == configId)
                .findFirst()
                .orElse(null);
    }

    public GameEntity getEntityByConfigId(int configId, int groupId) {
        return this.entities.values().stream()
                .filter(x -> x.getConfigId() == configId && x.getGroupId() == groupId)
                .findFirst()
                .orElse(null);
    }

    @Nullable public Route getSceneRouteById(int routeId) {
        return sceneRoutes.get(routeId);
    }

    public void setPaused(boolean paused) {
        if (this.isPaused != paused) {
            this.isPaused = paused;
            this.broadcastPacket(new PacketSceneTimeNotify(this));
        }
    }

    public int getSceneTime() {
        return (int) (this.getWorld().getWorldTime() - this.startWorldTime);
    }

    public int getSceneTimeSeconds() {
        return this.getSceneTime() / 1000;
    }

    public void addDungeonSettleObserver(DungeonSettleListener dungeonSettleListener) {
        if (dungeonSettleListeners == null) {
            dungeonSettleListeners = new ArrayList<>();
        }

        dungeonSettleListeners.add(dungeonSettleListener);
    }

    public void triggerDungeonEvent(DungeonPassConditionType conditionType, int... params) {
        if (this.dungeonManager == null) return;
        this.dungeonManager.triggerEvent(conditionType, params);
    }

    public boolean isInScene(GameEntity entity) {
        return this.entities.containsKey(entity.getId());
    }

    public synchronized void addPlayer(Player player) {

        if (getPlayers().contains(player)) {
            return;
        }

        if (player.getScene() != null) {
            player.getScene().removePlayer(player);
        }

        getPlayers().add(player);
        player.setSceneId(this.getId());
        player.setScene(this);

        this.setupPlayerAvatars(player);
		this.applySeiraiFallbackWeather(player, false);
		this.applyDragonspineFallbackWeather(player, false);
		this.applyOceanidFallbackWeather(player, false);
    }

    public synchronized void removePlayer(Player player) {
		
		if (this.getId() == ICEWIND_SCENE_ID && this.icewindSuiteFallbackWeatherActive) {
			this.resetIcewindSuiteFallbackWeather(player);
		}
		
		if (this.getId() == GOLDEN_WOLFLORD_SCENE_ID && this.goldenWolflordWeatherActive) {
			this.resetGoldenWolflordFallbackWeather(player);
		}
		
		if (this.getId() == SEIRAI_SCENE_ID && this.seiraiFallbackWeatherByUid.remove(player.getUid()) != null) {
			player.setWeather(SEIRAI_WEATHER_DEFAULT, ClimateType.CLIMATE_SUNNY);
		}
		
		if (this.getId() == DRAGONSPINE_SCENE_ID && this.dragonspineFallbackWeatherByUid.remove(player.getUid()) != null) {
			player.setWeather(DRAGONSPINE_WEATHER_DEFAULT, ClimateType.CLIMATE_SUNNY);
		}
		
		if (this.getId() == OCEANID_SCENE_ID && this.oceanidFallbackWeatherByUid.remove(player.getUid()) != null) {
			player.setWeather(OCEANID_WEATHER_DEFAULT, ClimateType.CLIMATE_SUNNY);
		}

        if (this.getChallenge() != null && this.getChallenge().inProgress()) {
            player.sendPacket(new PacketDungeonChallengeFinishNotify(this.getChallenge()));
        }

        getPlayers().remove(player);
		if (getPlayers().isEmpty()) {
			this.goldenWolflordWeatherActive = false;
		}
		
        player.setScene(null);

        this.removePlayerAvatars(player);

        for (EntityBaseGadget gadget : player.getTeamManager().getGadgets()) {
            this.removeEntity(gadget);
        }

        this.getEntities().values().stream()
                .filter(gameEntity -> gameEntity instanceof EntityVehicle)
                .map(gameEntity -> (EntityVehicle) gameEntity)
                .filter(entityVehicle -> entityVehicle.getOwner().equals(player))
                .forEach(entityVehicle -> this.removeEntity(entityVehicle, VisionType.VisionType_VISION_REMOVE));

        if (this.getPlayerCount() <= 0 && !this.dontDestroyWhenEmpty) {
            this.getScriptManager().onDestroy();
            this.getWorld().deregisterScene(this);
        }

        this.saveGroups();
    }

    private void setupPlayerAvatars(Player player) {

        player.getTeamManager().getActiveTeam().clear();

        TeamInfo teamInfo = player.getTeamManager().getCurrentTeamInfo();
        for (int avatarId : teamInfo.getAvatars()) {
            Avatar avatar = player.getAvatars().getAvatarById(avatarId);
            if (avatar == null) {
                if (player.getTeamManager().isUsingTrialTeam()) {
                    avatar = player.getTeamManager().getTrialAvatars().get(avatarId);
                }
                if (avatar == null) continue;
            }
            player
                    .getTeamManager()
                    .getActiveTeam()
                    .add(
                            EntityCreationEvent.call(
                                    EntityAvatar.class,
                                    new Class<?>[] {Scene.class, Avatar.class},
                                    new Object[] {player.getScene(), avatar}));
        }

        if (player.getTeamManager().getCurrentCharacterIndex()
                        >= player.getTeamManager().getActiveTeam().size()
                || player.getTeamManager().getCurrentCharacterIndex() < 0) {
            player
                    .getTeamManager()
                    .setCurrentCharacterIndex(player.getTeamManager().getCurrentCharacterIndex() - 1);
        }
    }

    private synchronized void removePlayerAvatars(Player player) {
        var team = player.getTeamManager().getActiveTeam();

        team.forEach(e -> removeEntity(e, VisionType.VisionType_VISION_REMOVE));
        team.clear();
    }

    public void spawnPlayer(Player player) {
        var teamManager = player.getTeamManager();
        if (this.isInScene(teamManager.getCurrentAvatarEntity())) {
            return;
        }

        if (teamManager.getCurrentAvatarEntity().getFightProperty(FightProperty.FIGHT_PROP_CUR_HP)
                <= 0f) {
            teamManager.getCurrentAvatarEntity().setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 1f);
        }

        this.addEntity(teamManager.getCurrentAvatarEntity());
		this.applySeiraiFallbackWeather(player, false);
		this.applyDragonspineFallbackWeather(player, false);
		this.applyOceanidFallbackWeather(player, false);

        teamManager.getActiveTeam().stream()
                .map(EntityAvatar::getAvatar)
                .forEach(Avatar::sendSkillExtraChargeMap);
    }

    private void addEntityDirectly(GameEntity entity) {
        getEntities().put(entity.getId(), entity);
        entity.onCreate();
    }

    public synchronized void addEntity(GameEntity entity) {
		if (this.isBlockedPmaRouteBarrierEntity(entity)) {
			return;
		}
        this.addEntityDirectly(entity);
        this.broadcastPacket(new PacketSceneEntityAppearNotify(entity));
    }

    public synchronized void addEntityToSingleClient(Player player, GameEntity entity) {
        this.addEntityDirectly(entity);
        player.sendPacket(new PacketSceneEntityAppearNotify(entity));
    }

    public void addDropEntity(GameItem item, GameEntity bornForm, Player player, boolean share) {

        ItemData itemData = GameData.getItemDataMap().get(item.getItemId());
        if (itemData == null) return;
        if (itemData.isEquip()) {
            float range = (1.5f + (.05f * item.getCount()));
            for (int j = 0; j < item.getCount(); j++) {
                Position pos = bornForm.getPosition().nearby2d(range).addY(0.5f);
                EntityItem entity = new EntityItem(this, player, itemData, pos, item.getCount(), share);
                addEntity(entity);
            }
        } else {
            EntityItem entity =
                    new EntityItem(
                            this,
                            player,
                            itemData,
                            bornForm.getPosition().clone().addY(0.5f),
                            item.getCount(),
                            share);
            addEntity(entity);
        }
    }

    public void addEntities(Collection<? extends GameEntity> entities) {
        addEntities(entities, VisionType.VisionType_VISION_BORN);
    }

    public void updateEntity(GameEntity entity) {
        this.broadcastPacket(new PacketSceneEntityUpdateNotify(entity));
    }

    public void updateEntity(GameEntity entity, VisionType type) {
        this.broadcastPacket(new PacketSceneEntityUpdateNotify(Arrays.asList(entity), type));
    }

    private static <T> List<List<T>> chopped(List<T> list, final int L) {
        List<List<T>> parts = new ArrayList<List<T>>();
        final int N = list.size();
        for (int i = 0; i < N; i += L) {
            parts.add(new ArrayList<T>(list.subList(i, Math.min(N, i + L))));
        }
        return parts;
    }

    public synchronized void addEntities(
            Collection<? extends GameEntity> entities, VisionType visionType) {
        if (entities == null || entities.isEmpty()) {
            return;
        }

        var filteredEntities =
				entities.stream()
						.filter(entity -> !this.isBlockedPmaRouteBarrierEntity(entity))
						.toList();

        if (filteredEntities.isEmpty()) {
			return;
		}

		for (var entity : filteredEntities) {
			this.addEntityDirectly(entity);
		}

		for (var l : chopped(new ArrayList<>(filteredEntities), 100)) {
			this.broadcastPacket(new PacketSceneEntityAppearNotify(l, visionType));
		}
	}

    private GameEntity removeEntityDirectly(GameEntity entity) {
        var removed = getEntities().remove(entity.getId());
        if (removed != null) {
            removed.onRemoved();
        }
        return removed;
    }

    public void removeEntity(GameEntity entity) {
        this.removeEntity(entity, VisionType.VisionType_VISION_DIE);
    }

    public synchronized void removeEntity(GameEntity entity, VisionType visionType) {
        GameEntity removed = this.removeEntityDirectly(entity);
        if (removed != null) {
            this.broadcastPacket(new PacketSceneEntityDisappearNotify(removed, visionType));
        }
    }

    public void removeEntities(List<GameEntity> entity, VisionType visionType) {
        var toRemove =
                entity.stream()
                        .filter(Objects::nonNull)
                        .map(this::removeEntityDirectly)
                        .filter(Objects::nonNull)
                        .toList();
        if (!toRemove.isEmpty()) {
            this.broadcastPacket(new PacketSceneEntityDisappearNotify(toRemove, visionType));
        }
    }

    public synchronized void replaceEntity(EntityAvatar oldEntity, EntityAvatar newEntity) {
        this.removeEntityDirectly(oldEntity);
        this.addEntityDirectly(newEntity);
        this.broadcastPacket(
                new PacketSceneEntityDisappearNotify(oldEntity, VisionType.VisionType_VISION_REPLACE));
        this.broadcastPacket(
                new PacketSceneEntityAppearNotify(
                        newEntity, VisionType.VisionType_VISION_REPLACE, oldEntity.getId()));
    }

    public void showOtherEntities(Player player) {
        GameEntity currentEntity = player.getTeamManager().getCurrentAvatarEntity();
        List<GameEntity> entities =
                this.getEntities().values().stream()
                        .filter(entity -> entity != currentEntity)
                        .filter(
                                gameEntity ->
                                        !(gameEntity instanceof Rebornable rebornable) || !rebornable.isInCD())
                        .toList();

        player.sendPacket(new PacketSceneEntityAppearNotify(entities, VisionType.VisionType_VISION_MEET));
    }

    public void handleAttack(AttackResult result) {
        GameEntity target = getEntityById(result.getDefenseId());
        ElementType attackType = ElementType.getTypeByValue(result.getElementType());

        if (target == null) {
            Grasscutter.getLogger().info("handleAttack: target not found defenseId={} attackerId={} damage={}", result.getDefenseId(), result.getAttackerId(), result.getDamage());
            return;
        }
		if (this.isOceanidPlatformEntity(target)) {
			this.hardenOceanidPlatform((EntityGadget) target);
			return;
		}
        if (target instanceof EntityAvatar) {
            if (((EntityAvatar) target).getPlayer().isInGodMode()) {
                return;
            }
        }
		
		if (target instanceof EntityMonster monster && this.isGoldenWolflordMonster(monster)) {
			if (this.handleGoldenWolflordVirtualDamage(monster, result.getDamage(), result.getAttackerId())) {
				return;
			}
		}
		
		if (target instanceof EntityMonster monster && this.isIcewindFallbackMonster(monster)) {
			if (this.handleIcewindSuiteVirtualDamage(monster, result.getDamage(), result.getAttackerId())) {
				return;
			}
		}

        GameEntity attacker = getEntityById(result.getAttackerId());
        if (attacker instanceof EntityClientGadget && target instanceof EntityAvatar) {
            if (target.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS) > 0f) return;
            float curHp = target.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
            float capped = Math.min(result.getDamage(), curHp - 1f);
            if (capped > 0) target.damage(capped, result.getAttackerId(), attackType);
            return;
        }

        if (target instanceof EntityAvatar avatar) {
            if (avatar.getPlayer()
            .getAbilityManager()
            .isAbilityInvulnerable()) {
                return;
            }
        }

        target.damage(result.getDamage(), result.getAttackerId(), attackType);

        if (attacker instanceof EntityAvatar arlecAttacker
                && arlecAttacker.getAvatar().getAvatarId() == 10000096
                && !(target instanceof EntityAvatar)) {
            reduceArlecchinoBoL(arlecAttacker);
        }

        if (attacker instanceof EntityAvatar clorindeAttacker
                && clorindeAttacker.getAvatar().getAvatarId() == 10000098
                && !(target instanceof EntityAvatar)) {
            reduceClorindeBoL(clorindeAttacker);
        }
    }

    private void reduceClorindeBoL(EntityAvatar clorinde) {
        float curDebt = clorinde.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        if (curDebt <= 0f) return;
        float reduction = curDebt * 0.015f;
        float newDebt = Math.max(0f, curDebt - reduction);
        float change = newDebt - curDebt;
        clorinde.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, newDebt);
        broadcastPacket(new PacketEntityFightPropUpdateNotify(clorinde, FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
        var debtsReason = newDebt <= 0f
            ? ChangeHpDebtsReasonOuterClass.ChangeHpDebtsReason.CHANGE_HP_DEBTS_REASON_CHANGE_HP_DEBTS_PAY_FINISH
            : ChangeHpDebtsReasonOuterClass.ChangeHpDebtsReason.CHANGE_HP_DEBTS_REASON_CHANGE_HP_DEBTS_PAY;
        broadcastPacket(new PacketEntityFightPropChangeReasonNotify(
            clorinde,
            FightProperty.FIGHT_PROP_CUR_HP_DEBTS,
            change,
            PropChangeReasonOuterClass.PropChangeReason.PropChangeReason_PROP_CHANGE_ABILITY,
            debtsReason
        ));
    }

    private void reduceArlecchinoBoL(EntityAvatar arlecchino) {
        float curDebt = arlecchino.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        if (curDebt <= 0f) return;
        float reduction = curDebt * 0.024f;
        float newDebt = Math.max(0f, curDebt - reduction);
        float change = newDebt - curDebt;
        arlecchino.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS, newDebt);
        broadcastPacket(new PacketEntityFightPropUpdateNotify(arlecchino, FightProperty.FIGHT_PROP_CUR_HP_DEBTS));
        var debtsReason = newDebt <= 0f
            ? ChangeHpDebtsReasonOuterClass.ChangeHpDebtsReason.CHANGE_HP_DEBTS_REASON_CHANGE_HP_DEBTS_PAY_FINISH
            : ChangeHpDebtsReasonOuterClass.ChangeHpDebtsReason.CHANGE_HP_DEBTS_REASON_CHANGE_HP_DEBTS_PAY;
        broadcastPacket(new PacketEntityFightPropChangeReasonNotify(
            arlecchino,
            FightProperty.FIGHT_PROP_CUR_HP_DEBTS,
            change,
            PropChangeReasonOuterClass.PropChangeReason.PropChangeReason_PROP_CHANGE_ABILITY,
            debtsReason
        ));
    }

    public void killEntity(GameEntity target) {
        killEntity(target, 0);
    }

    public void killEntity(GameEntity target, int attackerId) {
        GameEntity attacker = null;

        if (attackerId > 0) {
            attacker = getEntityById(attackerId);
        }
		
		if (this.isOceanidPlatformEntity(target)) {
			this.hardenOceanidPlatform((EntityGadget) target);
			return;
		}

        if (attacker != null) {

            if (attacker instanceof EntityClientGadget gadgetAttacker) {
                var clientGadgetOwner = getEntityById(gadgetAttacker.getOwnerEntityId());
                if (clientGadgetOwner instanceof EntityAvatar) {
                    ((EntityClientGadget) attacker)
                            .getOwner()
                            .getCodex()
                            .checkAnimal(target, CodexAnimalData.CountType.CODEX_COUNT_TYPE_KILL);
                }
            } else if (attacker instanceof EntityAvatar avatarAttacker) {
                avatarAttacker
                        .getPlayer()
                        .getCodex()
                        .checkAnimal(target, CodexAnimalData.CountType.CODEX_COUNT_TYPE_KILL);
            }
        }

        this.broadcastPacket(new PacketLifeStateChangeNotify(attackerId, target, LifeState.LIFE_DEAD));

        var world = this.getWorld();
		if (target instanceof EntityMonster monster
				&& attacker != null
				&& this.getSceneType() != SceneType.SCENE_DUNGEON
				&& !this.isOceanidFallbackBody(monster)) {
			boolean handled = false;

			var legacyDrops = world.getServer().getDropSystemLegacy().getDropData();

			if (monster.getMetaMonster() == null
					&& (monster.getSpawnEntry() != null || this.isIcewindFallbackMonster(monster))
					&& legacyDrops.containsKey(monster.getMonsterData().getId())) {
				world.getServer().getDropSystemLegacy().callDrop(monster);
				handled = true;
			}

			if (!handled && !world.getServer().getDropSystem().handleMonsterDrop(monster)) {
				if (monster.getMetaMonster() != null) {
					Grasscutter.getLogger()
							.debug(
									"Can not solve monster drop: drop_id = {}, drop_tag = {}. Falling back to legacy drop system.",
									monster.getMetaMonster().drop_id,
									monster.getMetaMonster().drop_tag);
				} else {
					Grasscutter.getLogger()
							.debug(
									"Can not solve static monster drop: monster_id = {}, kill_drop_id = {}. Falling back to legacy drop system.",
									monster.getMonsterData().getId(),
									monster.getMonsterData().getKillDropId());
				}

				world.getServer().getDropSystemLegacy().callDrop(monster);
			}
		}

        if (target instanceof EntityGadget gadget) {
            if (gadget.getMetaGadget() != null) {
                world
                        .getServer()
                        .getDropSystem()
                        .handleChestDrop(
                                gadget.getMetaGadget().drop_id, gadget.getMetaGadget().drop_count, gadget);
            }
        }

		this.removeEntity(target);

		if (target instanceof EntityClientGadget cg && cg.getOwner() != null) {
			cg.getOwner().getTeamManager().getGadgets().remove(cg);
		}

		target.onDeath(attackerId);
		this.markStaticSpawnForRespawn(target);

		if (target instanceof EntityMonster monster) {
			this.handleOceanidFallbackMonsterDeath(monster, attackerId);

			Player host = this.getWorld().getHost();

			if (host != null && host.getDailyTaskManager() != null) {
				host.getDailyTaskManager().onMonsterDeath(this, monster.getGroupId(), monster.getConfigId(), attackerId);
			}
		}

		this.triggerDungeonEvent(DungeonPassConditionType.DUNGEON_COND_KILL_MONSTER_COUNT, ++killedMonsterCount);
    }

	private void markStaticSpawnForRespawn(GameEntity entity) {
		if (entity == null) {
			return;
		}

		SpawnDataEntry spawnEntry = entity.getSpawnEntry();

		/*
		 * Lua monsters, manually spawned entities, summons and other
		 * non-static entities have no SpawnDataEntry.
		 */
		if (spawnEntry == null) {
			return;
		}

		/*
		 * Icewind Suite has its own reset and spawning system.
		 */
		if (this.isIcewindSuiteStaticSpawn(spawnEntry)) {
			return;
		}

		/*
		 * Every static monster is allowed to respawn after being killed.
		 */
		if (entity instanceof EntityMonster) {
			this.pendingStaticRespawns.add(spawnEntry);
			return;
		}

		/*
		 * Only collectible static gadgets are allowed to respawn.
		 *
		 * Chests, worktops, reward blossoms, quest gadgets and ordinary
		 * interactables are deliberately excluded.
		 */
		if (entity instanceof EntityGadget gadget
				&& (gadget.getContent() instanceof GadgetGatherObject
						|| gadget.getContent() instanceof GadgetGatherPoint)) {
			this.pendingStaticRespawns.add(spawnEntry);
		}
	}

	private boolean isIcewindSuiteStaticSpawn(SpawnDataEntry spawnEntry) {
		if (spawnEntry == null || this.getId() != ICEWIND_SCENE_ID) {
			return false;
		}

		if (ICEWIND_FALLBACK_MONSTER_IDS.contains(spawnEntry.getMonsterId())) {
			return true;
		}

		return spawnEntry.getGroup() != null
				&& spawnEntry.getGroup().getGroupId() == ICEWIND_GROUP_ID;
	}

    public void onTick() {

        if (this.getSceneType() == SceneType.SCENE_HOME_WORLD
                || this.getSceneType() == SceneType.SCENE_HOME_ROOM) {
            this.finishLoading();
            return;
        }

        if (!isPaused) {
            this.getScheduler().runTasks();
        }

		if (this.getScriptManager().isInit()) {
			this.checkGroups();
			this.checkLegacySpawnsForMissingScriptGroups();

			/*
			 * Stream active daily commission groups according to player proximity.
			 *
			 * This deliberately happens BEFORE checkRegions(). If the player has
			 * teleported far away, the obsolete commission group and its regions
			 * are removed before Lua can observe a giant ENTER/LEAVE transition.
			 */
			Player host =
					this.getWorld().getHost();

			if (host != null
					&& host.getDailyTaskManager() != null) {
				host.getDailyTaskManager()
						.updateActiveGroups(this);
			}
		} else {
			this.checkSpawns();
		}

		this.scriptManager.checkRegions();

        if (challenge != null) {
            challenge.onCheckTimeOut();
        }

        var sceneTime = getSceneTimeSeconds();

        var entities = Map.copyOf(this.getEntities());
        entities.forEach(
                (eid, e) -> {
                    if (!e.isAlive()) {
                        this.getEntities().remove(eid);
                    } else e.onTick(sceneTime);
                });

        blossomManager.onTick();

        var towerManager = getPlayers().get(0).getTowerManager();
        if (towerManager != null && towerManager.isInProgress()) {
            towerManager.onTick();
        }

        this.checkNpcGroup();
		this.processPendingIcewindSuiteArenaTeleports();
		
		if (this.tickCount % 20 == 0) {
			this.checkIcewindSuiteFallbackAntiStall(sceneTime);
			this.checkIcewindSuiteFallbackReset();
			this.checkGoldenWolflordFallbackWeatherState();
		}
		
		if (this.tickCount % 5 == 0) {
			this.checkSeiraiFallbackWeather();
			this.checkDragonspineFallbackWeather();
			this.checkOceanidFallbackWeather();
			this.checkOceanidFallbackEncounter();
		}

		for (Player player : this.getPlayers()) {
			this.updateDragonspineClimate(player);
		}


        this.finishLoading();
        this.checkPlayerRespawn();
        if (this.tickCount++ % 10 == 0) this.broadcastPacket(new PacketSceneTimeNotify(this));
    }

    protected void checkPlayerRespawn() {
        if (this.getScriptManager().getConfig() == null) return;
        var diePos = this.getScriptManager().getConfig().die_y;

        this.players.forEach(
                player -> {
                    if (this.getScriptManager().getConfig() == null) return;

                    if (diePos >= player.getPosition().getY()) {

                        this.respawnPlayer(player);
                    }
                });

        this.getEntities()
                .forEach(
                        (id, entity) -> {
                            if (diePos >= entity.getPosition().getY()) {
                                this.killEntity(entity);
                            }
                        });
    }

    public Position getDefaultLocation(Player player) {
        val defaultPosition = getScriptManager().getConfig().born_pos;
        return defaultPosition != null ? defaultPosition : player.getPosition();
    }

    public Position getDefaultRotation(Player player) {
        var defaultRotation = this.getScriptManager().getConfig().born_rot;
        return defaultRotation != null ? defaultRotation : player.getRotation();
    }

    private Position getRespawnLocation(Player player) {

        var lastCheckpointPos = dungeonManager != null ? dungeonManager.getRespawnLocation() : null;
        return lastCheckpointPos != null ? lastCheckpointPos : getDefaultLocation(player);
    }

    private Position getRespawnRotation(Player player) {
        var lastCheckpointRot =
                this.dungeonManager != null ? this.dungeonManager.getRespawnRotation() : null;
        return lastCheckpointRot != null ? lastCheckpointRot : this.getDefaultRotation(player);
    }

    public boolean respawnPlayer(Player player) {

        player.getTeamManager().applyVoidDamage();

        var targetPos = getRespawnLocation(player);
        var targetRot = getRespawnRotation(player);
        var teleportProps =
                TeleportProperties.builder()
                        .sceneId(getId())
                        .teleportTo(targetPos)
                        .teleportRot(targetRot)
                        .teleportType(PlayerTeleportEvent.TeleportType.INTERNAL)
                        .enterType(EnterTypeOuterClass.EnterType.EnterType_ENTER_GOTO)
                        .enterReason(
                                dungeonManager != null ? EnterReason.DungeonReviveOnWaypoint : EnterReason.Revival);

        return this.getWorld().transferPlayerToScene(player, teleportProps.build());
    }

    public void finishLoading() {
        if (this.finishedLoading) return;

        this.finishedLoading = true;
        this.afterLoadedCallbacks.forEach(Runnable::run);
        this.afterLoadedCallbacks.clear();
    }

    public void runWhenFinished(Runnable runnable) {
        if (this.isFinishedLoading()) {
            runnable.run();
            return;
        }

        this.afterLoadedCallbacks.add(runnable);
    }

    public void playerSceneInitialized(Player player) {

        if (!player.equals(this.getHost())) return;

        this.afterHostInitCallbacks.forEach(Runnable::run);
        this.afterHostInitCallbacks.clear();
    }

    public void runWhenHostInitialized(Runnable runnable) {
        if (this.isFinishedLoading()) {
            runnable.run();
            return;
        }

        this.afterHostInitCallbacks.add(runnable);
    }

    public int getEntityLevel(int baseLevel, int worldLevelOverride) {
        int level = worldLevelOverride > 0 ? worldLevelOverride + baseLevel - 22 : baseLevel;
        level = Math.min(level, 100);
        level = level <= 0 ? 1 : level;

        return level;
    }

    public int getLevelForMonster(int configId, int defaultLevel) {
        if (getDungeonManager() != null) {
            return getDungeonManager().getLevelForMonster(configId);
        } else if (getWorld().getWorldLevel() > 0) {
            var worldLevelData = GameData.getWorldLevelDataMap().get(getWorld().getWorldLevel());

            if (worldLevelData != null) {
                return worldLevelData.getMonsterLevel();
            }
        }
        return defaultLevel;
    }

    public void checkNpcGroup() {
        Set<SceneNpcBornEntry> npcBornEntries = ConcurrentHashMap.newKeySet();
        for (Player player : this.getPlayers()) {
            npcBornEntries.addAll(loadNpcForPlayer(player));
        }

        this.npcBornEntrySet = npcBornEntries;
    }

    public void checkSpawns() {
		this.checkSpawns(false);
	}

	private void checkLegacySpawnsForMissingScriptGroups() {
		this.checkSpawns(true);
	}

	private void checkSpawns(boolean missingScriptOnly) {
		Set<SpawnDataEntry.GridBlockId> loadedGridBlocks = new HashSet<>();

		for (Player player : this.getPlayers()) {
			Collections.addAll(
					loadedGridBlocks,
					SpawnDataEntry.GridBlockId.getAdjacentGridBlockIds(
							player.getSceneId(), player.getPosition()));
		}

		Set<SpawnDataEntry.GridBlockId> previousLoadedGridBlocks =
				missingScriptOnly ? this.loadedMissingScriptGridBlocks : this.loadedGridBlocks;

		Set<SpawnDataEntry.GridBlockId> leavingBlocks = new HashSet<>(previousLoadedGridBlocks);
		leavingBlocks.removeAll(loadedGridBlocks);

		if (!leavingBlocks.isEmpty()) {
			/*
			 * Select only entries that:
			 *
			 * 1. Were genuinely killed or collected.
			 * 2. Were explicitly approved for static respawning.
			 * 3. Belong to a grid block that is no longer loaded.
			 */
			Set<SpawnDataEntry> entriesToRelease =
					this.pendingStaticRespawns.stream()
							.filter(entry -> leavingBlocks.contains(entry.getBlockId()))
							.collect(Collectors.toSet());

			if (!entriesToRelease.isEmpty()) {
				/*
				 * Release ownership only now, after the block has unloaded.
				 * This prevents checkSpawns() from creating another instance
				 * while the old block is still active.
				 */
				this.getSpawnedEntities().removeAll(entriesToRelease);
				this.pendingStaticRespawns.removeAll(entriesToRelease);
			}

			/*
			 * Preserve LunaGC's existing dead-entry cleanup.
			 *
			 * Non-respawnable entries such as chests remain in spawnedEntities,
			 * so clearing their dead flag does not make them spawn again.
			 */
			this.getDeadSpawnedEntities()
					.removeIf(entry -> leavingBlocks.contains(entry.getBlockId()));
		}

		if (previousLoadedGridBlocks.containsAll(loadedGridBlocks)) {
			return;
		}

		if (missingScriptOnly) {
			this.loadedMissingScriptGridBlocks = loadedGridBlocks;
		} else {
			this.loadedGridBlocks = loadedGridBlocks;
		}

		var spawnLists = GameDepot.getSpawnLists();

		Set<SpawnDataEntry> visible = new HashSet<>();

		for (var block : loadedGridBlocks) {
			var spawns = spawnLists.get(block);

			if (spawns != null) {
				visible.addAll(spawns);
			}
		}

		if (missingScriptOnly) {
			visible.removeIf(entry -> !this.shouldUseLegacyFallbackSpawn(entry));
		}
		visible.removeIf(this::isBlockedPmaRouteBarrierSpawn);
		visible.removeIf(this::isPrematureGoldenWolflordBlossomSpawn);

		WorldLevelData worldLevelData = GameData.getWorldLevelDataMap().get(getWorld().getWorldLevel());

		int worldLevelOverride = 0;

		if (worldLevelData != null) {
			worldLevelOverride = worldLevelData.getMonsterLevel();
		}

		List<GameEntity> toAdd = new ArrayList<>();
		List<GameEntity> toRemove = new ArrayList<>();

		var spawnedEntities = this.getSpawnedEntities();

		/*
		 * Defensive snapshot of entries that still have a live entity in the
		 * scene. Even if the bookkeeping sets become temporarily inconsistent,
		 * an existing static entity must never be created a second time.
		 */
		Set<SpawnDataEntry> activeStaticEntries =
				this.getEntities().values().stream()
						.map(GameEntity::getSpawnEntry)
						.filter(Objects::nonNull)
						.collect(Collectors.toSet());

		for (SpawnDataEntry entry : visible) {
			if (!spawnedEntities.contains(entry) && !this.getDeadSpawnedEntities().contains(entry) && !activeStaticEntries.contains(entry)) {
				GameEntity entity = null;

				if (entry.getMonsterId() > 0) {
					MonsterData data = GameData.getMonsterDataMap().get(entry.getMonsterId());

					if (data == null) {
						continue;
					}

					int level = this.getEntityLevel(entry.getLevel(), worldLevelOverride);

					EntityMonster monster =
							new EntityMonster(this, data, entry.getPos(), entry.getRot(), level);

					monster.setGroupId(entry.getGroup().getGroupId());
					monster.setPoseId(entry.getPoseId());
					monster.setConfigId(entry.getConfigId());
					monster.setSpawnEntry(entry);

					entity = monster;
				} else if (entry.getGadgetId() > 0) {
					EntityGadget gadget =
							new EntityGadget(this, entry.getGadgetId(), entry.getPos(), entry.getRot());

					gadget.setGroupId(entry.getGroup().getGroupId());
					gadget.setConfigId(entry.getConfigId());
					gadget.setSpawnEntry(entry);

					int state = entry.getGadgetState();

					if (state > 0) {
						gadget.setState(state);
					}

					gadget.buildContent();

					/*
					 * Keep normal gather/interact gadgets unbreakable, but allow break-required
					 * gather objects like crates/barrels/ore-like objects to actually break.
					 */
					if (gadget.getContent() instanceof GadgetGatherObject gatherObject
							&& !gatherObject.requiresBreaking()) {
						gadget.setFightProperty(FightProperty.FIGHT_PROP_BASE_HP, Float.POSITIVE_INFINITY);
						gadget.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, Float.POSITIVE_INFINITY);
						gadget.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, Float.POSITIVE_INFINITY);
					}

					entity = gadget;

					blossomManager.initBlossom(gadget);
				}

				if (entity == null) {
					continue;
				}

				toAdd.add(entity);
				spawnedEntities.add(entry);
				activeStaticEntries.add(entry);
			}
		}

		for (GameEntity entity : this.getEntities().values()) {
			var spawnEntry = entity.getSpawnEntry();

			if (spawnEntry != null
					&& !(entity instanceof EntityWeapon)
					&& (!missingScriptOnly || this.isMissingScriptSpawn(spawnEntry))
					&& !visible.contains(spawnEntry)) {
				toRemove.add(entity);
				spawnedEntities.remove(spawnEntry);
			}
		}

		if (toAdd.size() > 0) {
			var filteredToAdd =
					toAdd.stream()
							.filter(entity -> !this.isBlockedPmaRouteBarrierEntity(entity))
							.toList();

			if (!filteredToAdd.isEmpty()) {
				filteredToAdd.forEach(this::addEntityDirectly);
				this.broadcastPacket(
						new PacketSceneEntityAppearNotify(filteredToAdd, VisionType.VisionType_VISION_BORN));
			}
		}

		if (toRemove.size() > 0) {
			toRemove.forEach(this::removeEntityDirectly);
			this.broadcastPacket(
					new PacketSceneEntityDisappearNotify(toRemove, VisionType.VisionType_VISION_REMOVE));

			blossomManager.recycleGadgetEntity(toRemove);
		}
	}
	
	private boolean shouldUseLegacyFallbackSpawn(SpawnDataEntry entry) {
		/*
		 * Monsters use the missing-script-group rule.
		 * This is what restores newer-region static monster spawns when Lua group scripts are absent.
		 */
		if (entry.getMonsterId() > 0) {
			return this.isMissingScriptSpawn(entry);
		}

		/*
		 * Static gadget data is useful in newer/partial regions too, but old regions may
		 * already spawn equivalent gadgets through Lua scripts. Avoid duplicates by checking
		 * whether an equivalent script/runtime gadget already exists nearby.
		 */
		if (entry.getGadgetId() > 0) {
			return this.isMissingScriptSpawn(entry) && !this.hasEquivalentScriptGadget(entry);
		}

		return false;
	}
	
	private boolean isBlockedPmaRouteBarrierSpawn(SpawnDataEntry entry) {
		if (entry == null || entry.getGroup() == null) {
			return false;
		}

		if (this.getId() != PMA_ROUTE_BARRIER_SCENE_ID) {
			return false;
		}

		if (entry.getGroup().getGroupId() != PMA_ROUTE_BARRIER_GROUP_ID) {
			return false;
		}

		return (entry.getConfigId() == PMA_ROUTE_BARRIER_CONFIG_A
						&& entry.getGadgetId() == PMA_ROUTE_BARRIER_GADGET_A)
				|| (entry.getConfigId() == PMA_ROUTE_BARRIER_CONFIG_B
						&& entry.getGadgetId() == PMA_ROUTE_BARRIER_GADGET_B);
	}
	
	private boolean isBlockedPmaRouteBarrierEntity(GameEntity entity) {
		if (this.getId() != PMA_ROUTE_BARRIER_SCENE_ID) {
			return false;
		}

		if (!(entity instanceof EntityGadget gadget)) {
			return false;
		}

		if (gadget.getGroupId() != PMA_ROUTE_BARRIER_GROUP_ID) {
			return false;
		}

		return (gadget.getConfigId() == PMA_ROUTE_BARRIER_CONFIG_A
						&& gadget.getGadgetId() == PMA_ROUTE_BARRIER_GADGET_A)
				|| (gadget.getConfigId() == PMA_ROUTE_BARRIER_CONFIG_B
						&& gadget.getGadgetId() == PMA_ROUTE_BARRIER_GADGET_B);
	}

	private boolean hasEquivalentScriptGadget(SpawnDataEntry entry) {
		if (entry.getGadgetId() <= 0 || entry.getPos() == null) {
			return false;
		}

		for (GameEntity entity : this.getEntities().values()) {
			if (!(entity instanceof EntityGadget gadget)) {
				continue;
			}

			/*
			 * Legacy fallback gadgets have a SpawnDataEntry.
			 * Ignore them here so the fallback does not block itself.
			 */
			if (gadget.getSpawnEntry() != null) {
				continue;
			}

			if (gadget.getGadgetId() != entry.getGadgetId()) {
				continue;
			}

			if (isNearSameSpawnPoint(gadget.getPosition(), entry.getPos())) {
				return true;
			}
		}

		return false;
	}

	private boolean isNearSameSpawnPoint(Position a, Position b) {
		float dx = a.getX() - b.getX();
		float dy = a.getY() - b.getY();
		float dz = a.getZ() - b.getZ();

		/*
		 * Strict enough to catch duplicate objects, but loose enough for tiny coordinate
		 * differences between script-spawned and static-spawned data.
		 */
		return dx * dx + dz * dz <= 4.0f && Math.abs(dy) <= 5.0f;
	}

	private boolean isMissingScriptSpawn(SpawnDataEntry entry) {
		if (!this.getScriptManager().isInit()) {
			return true;
		}

		var spawnGroup = entry.getGroup();

		if (spawnGroup == null) {
			return false;
		}

		var scriptBlocks = this.getScriptManager().getBlocks();

		if (scriptBlocks == null) {
			return false;
		}

		var scriptBlock = scriptBlocks.get(spawnGroup.getBlockId());

		if (scriptBlock == null) {
			return true;
		}

		if (scriptBlock.groups == null) {
			this.getScriptManager().loadBlockFromScript(scriptBlock);
		}

		return scriptBlock.groups == null || !scriptBlock.groups.containsKey(spawnGroup.getGroupId());
	}

    public List<SceneBlock> getPlayerActiveBlocks(Player player) {

        return SceneIndexManager.queryNeighbors(
                getScriptManager().getBlocksIndex(),
                player.getPosition().toXZDoubleArray(),
                Grasscutter.getConfig().server.game.loadEntitiesForPlayerRange);
    }

    public Set<Integer> getPlayerActiveGroups(Player player) {

        Position playerPosition = player.getPosition();
        Set<Integer> activeGroups = new HashSet<>();
        for (int i = 0; i < 4; i++) {
            Grid grid = getScriptManager().getGroupGrids().get(i);

            activeGroups.addAll(grid.getNearbyGroups(i, playerPosition));
        }

        return activeGroups;
    }

    public boolean loadBlock(SceneBlock block) {
        if (this.loadedBlocks.contains(block)) return false;

        this.onLoadBlock(block, this.players);
        this.loadedBlocks.add(block);
        return true;
    }

    public void checkGroups() {
        Set<Integer> visible =
                this.players.stream()
                        .map(this::getPlayerActiveGroups)
                        .flatMap(Collection::stream)
                        .collect(Collectors.toSet());

        for (var group : this.loadedGroups) {
            if (!visible.contains(group.id) && !group.dynamic_load && !group.dontUnload)
                unloadGroup(scriptManager.getBlocks().get(group.block_id), group.id);
        }

        var toLoad =
                visible.stream()
                        .filter(g -> this.loadedGroups.stream().noneMatch(gr -> gr.id == g))
                        .map(
                                g -> {
                                    for (var b : scriptManager.getBlocks().values()) {
                                        loadBlock(b);
                                        SceneGroup group = b.groups.getOrDefault(g, null);
                                        if (group != null && !group.dynamic_load) return group;
                                    }

                                    return null;
                                })
                        .filter(Objects::nonNull)
                        .toList();

        this.onLoadGroup(toLoad);
        if (!toLoad.isEmpty()) this.onRegisterGroups();
    }

    public void onLoadBlock(SceneBlock block, List<Player> players) {
        this.getScriptManager().loadBlockFromScript(block);
        scriptManager.getLoadedGroupSetPerBlock().put(block.id, new HashSet<>());

        Grasscutter.getLogger().trace("Scene {} block {} loaded.", this.getId(), block.id);
    }

    public int loadDynamicGroup(int group_id) {
        SceneGroup group = getScriptManager().getGroupById(group_id);
        if (group == null) return -1;

        this.onLoadGroup(new ArrayList<>(List.of(group)));

        if (GameData.getGroupReplacements().containsKey(group_id)) onRegisterGroups();

        if (group.init_config == null) return -1;
        return group.init_config.suite;
    }

    public boolean unregisterDynamicGroup(int groupId) {
        var group = getScriptManager().getGroupById(groupId);
        if (group == null) return false;

        var block = getScriptManager().getBlocks().get(group.block_id);
        this.unloadGroup(block, groupId);
        return true;
    }

    public void onRegisterGroups() {
        var sceneGroups = this.loadedGroups;
        var sceneGroupMap =
                sceneGroups.stream().collect(Collectors.toMap(item -> item.id, item -> item));
        var sceneGroupsIds = sceneGroups.stream().map(group -> group.id).toList();
        var dynamicGroups =
                sceneGroups.stream().filter(group -> group.dynamic_load).map(group -> group.id).toList();

        var nodes = new ArrayList<KahnsSort.Node>();
        var groupList = new ArrayList<Integer>();
        GameData.getGroupReplacements().values().stream()
                .filter(replacement -> dynamicGroups.contains(replacement.id))
                .forEach(
                        replacement -> {
                            Grasscutter.getLogger().debug("Graph ordering replacement {}", replacement);
                            replacement.replace_groups.forEach(
                                    group -> {
                                        nodes.add(new KahnsSort.Node(replacement.id, group));
                                        if (!groupList.contains(group)) groupList.add(group);
                                    });

                            if (!groupList.contains(replacement.id)) groupList.add(replacement.id);
                        });

        KahnsSort.Graph graph = new KahnsSort.Graph(nodes, groupList);
        List<Integer> dynamicGroupsOrdered = KahnsSort.doSort(graph);

        dynamicGroupsOrdered.forEach(
                group -> {
                    if (GameData.getGroupReplacements().containsKey((int) group)) {
                        var data = GameData.getGroupReplacements().get((int) group);
                        var sceneGroupReplacement =
                                this.loadedGroups.stream().filter(g -> g.id == group).findFirst().orElseThrow();
                        if (sceneGroupReplacement.is_replaceable != null) {
                            var it = data.replace_groups.iterator();
                            while (it.hasNext()) {
                                var replace_group = it.next();
                                if (!sceneGroupsIds.contains(replace_group)) continue;

                                SceneGroup sceneGroup = sceneGroupMap.get(replace_group);
                                if (sceneGroup != null
                                        && sceneGroup.is_replaceable != null
                                        && ((sceneGroup.is_replaceable.value
                                                        && sceneGroup.is_replaceable.version
                                                                <= sceneGroupReplacement.is_replaceable.version)
                                                || sceneGroup.is_replaceable.new_bin_only)) {
                                    this.unloadGroup(
                                            scriptManager.getBlocks().get(sceneGroup.block_id), replace_group);
                                    it.remove();
                                    Grasscutter.getLogger().debug("Graph ordering: unloaded {}", replace_group);
                                }
                            }
                        }
                    }
                });
    }

    public void loadTriggerFromGroup(SceneGroup group, String triggerName) {

        this.getScriptManager()
                .registerTrigger(
                        group.triggers.values().stream()
                                .filter(p -> p.getName().contains(triggerName))
                                .toList());
        group.regions.values().stream()
                .filter(q -> q.config_id == Integer.parseInt(triggerName.substring(13)))
                .map(region -> new EntityRegion(this, region))
                .forEach(getScriptManager()::registerRegion);
    }

    public void onLoadGroup(List<SceneGroup> groups) {
        if (groups == null || groups.isEmpty()) {
            return;
        }

        for (var group : groups) {
            if (this.loadedGroups.contains(group)) continue;

            this.getScriptManager().loadGroupFromScript(group);
            if (!this.scriptManager.getLoadedGroupSetPerBlock().containsKey(group.block_id))
                this.onLoadBlock(scriptManager.getBlocks().get(group.block_id), players);
            this.scriptManager.getLoadedGroupSetPerBlock().get(group.block_id).add(group);
        }

        var entities = new ArrayList<GameEntity>();
        for (var group : groups) {
            if (this.loadedGroups.contains(group)) continue;

            if (group.init_config == null) {
                continue;
            }

            var groupInstance = this.getScriptManager().getGroupInstanceById(group.id);
            var cachedInstance = this.getScriptManager().getCachedGroupInstanceById(group.id);
            if (cachedInstance != null) {
                cachedInstance.setLuaGroup(group);
                groupInstance = cachedInstance;
            }

            this.getScriptManager()
                    .refreshGroup(groupInstance, 0, false);

            this.loadedGroups.add(group);
        }

        this.scriptManager.meetEntities(entities);
        groups.forEach(
                g -> scriptManager.callEvent(new ScriptArgs(g.id, EventType.EVENT_GROUP_LOAD, g.id)));

        Grasscutter.getLogger().trace("Scene {} loaded {} group(s)", this.getId(), groups.size());
    }

    public void unloadGroup(SceneBlock block, int group_id) {
        List<GameEntity> toRemove =
                this.getEntities().values().stream()
                        .filter(e -> e != null && (e.getBlockId() == block.id && e.getGroupId() == group_id))
                        .toList();

        if (toRemove.size() > 0) {
            toRemove.forEach(this::removeEntityDirectly);
            this.broadcastPacket(
                    new PacketSceneEntityDisappearNotify(toRemove, VisionType.VisionType_VISION_REMOVE));
        }

        var group = block.groups.get(group_id);
        if (group.triggers != null) {
            group.triggers.values().forEach(getScriptManager()::deregisterTrigger);
        }
        if (group.regions != null) {
            group.regions.values().forEach(getScriptManager()::deregisterRegion);
        }
        if (challenge != null && group.id == challenge.getGroup().id) {
            challenge.fail();
        }

        scriptManager.getLoadedGroupSetPerBlock().get(block.id).remove(group);
        this.loadedGroups.remove(group);

        if (this.scriptManager.getLoadedGroupSetPerBlock().get(block.id).isEmpty()) {
            this.scriptManager.getLoadedGroupSetPerBlock().remove(block.id);
            Grasscutter.getLogger().trace("Scene {} block {} is unloaded.", this.getId(), block.id);
        }

        this.broadcastPacket(new PacketGroupUnloadNotify(List.of(group_id)));
        this.scriptManager.unregisterGroup(group);
    }

    public void onPlayerCreateGadget(EntityClientGadget gadget) {
        var owner = gadget.getOwner();

        this.addEntityDirectly(gadget);
        owner.getTeamManager().getGadgets().add(gadget);

        for (var player : this.getPlayers()) {
            if (player != owner) {
                player.getSession().send(new PacketSceneEntityAppearNotify(gadget));
            }
        }
    }

    public void onPlayerDestroyGadget(int entityId) {
        GameEntity entity = getEntities().get(entityId);

        if (!(entity instanceof EntityClientGadget gadget)) {
            for (var player : this.getPlayers()) {
                player.getTeamManager().getGadgets().removeIf(g -> g.getId() == entityId);
            }
            return;
        }

        this.removeEntityDirectly(gadget);

        var owner = gadget.getOwner();
        owner.getTeamManager().getGadgets().remove(gadget);

        this.broadcastPacket(
                new PacketSceneEntityDisappearNotify(gadget, VisionType.VisionType_VISION_DIE));
    }

    public void broadcastPacket(BasePacket packet) {

        for (Player player : this.getPlayers()) {
            player.getSession().send(packet);
        }
    }

    public void broadcastPacketToOthers(Player excludedPlayer, BasePacket packet) {

        if (this.getPlayerCount() == 1 && this.getPlayers().get(0) == excludedPlayer) {
            return;
        }

        for (Player player : this.getPlayers()) {
            if (player == excludedPlayer) {
                continue;
            }

            player.getSession().send(packet);
        }
    }

    public void addItemEntity(int itemId, int amount, GameEntity bornForm) {
        ItemData itemData = GameData.getItemDataMap().get(itemId);
        if (itemData == null) {
            return;
        }
        if (itemData.isEquip()) {
            float range = (1.5f + (.05f * amount));
            for (int i = 0; i < amount; i++) {
                Position pos = bornForm.getPosition().nearby2d(range).addZ(.9f);
                EntityItem entity = new EntityItem(this, null, itemData, pos, 1);
                addEntity(entity);
            }
        } else {
            EntityItem entity =
                    new EntityItem(
                            this, null, itemData, bornForm.getPosition().clone().addZ(.9f), amount);
            addEntity(entity);
        }
    }

    public void loadNpcForPlayerEnter(Player player) {
        this.npcBornEntrySet.addAll(loadNpcForPlayer(player));
    }

    private List<SceneNpcBornEntry> loadNpcForPlayer(Player player) {
        var pos = player.getPosition();
        var data = GameData.getSceneNpcBornData().get(getId());
        if (data == null) {
            return List.of();
        }

        var npcList =
                SceneIndexManager.queryNeighbors(
                        data.getIndex(),
                        pos.toDoubleArray(),
                        Grasscutter.getConfig().server.game.loadEntitiesForPlayerRange);

        var sceneNpcBornCanidates =
                npcList.stream().filter(i -> !this.npcBornEntrySet.contains(i)).toList();

        List<SceneNpcBornEntry> sceneNpcBornEntries = new ArrayList<>();
        sceneNpcBornCanidates.forEach(
                i -> {
                    var groupInstance = scriptManager.getGroupInstanceById(i.getGroupId());
                    if (groupInstance == null) return;
                    if (i.getSuiteIdList() != null
                            && !i.getSuiteIdList().contains(groupInstance.getActiveSuiteId())) return;
                    sceneNpcBornEntries.add(i);
                });

        if (sceneNpcBornEntries.size() > 0) {
            this.broadcastPacket(new PacketGroupSuiteNotify(sceneNpcBornEntries));
            Grasscutter.getLogger().trace("Loaded Npc Group Suite {}", sceneNpcBornEntries);
        }

        return npcList.stream()
                .filter(i -> this.npcBornEntrySet.contains(i) || sceneNpcBornEntries.contains(i))
                .toList();
    }

    public void loadGroupForQuest(List<QuestGroupSuite> sceneGroupSuite) {
        if (!scriptManager.isInit()) {
            return;
        }

        sceneGroupSuite.forEach(
                i -> {
                    var group = scriptManager.getGroupById(i.getGroup());
                    if (group == null) return;

                    var groupInstance = scriptManager.getGroupInstanceById(i.getGroup());
                    var suite = group.getSuiteByIndex(i.getSuite());
                    if (suite == null || groupInstance == null) {
                        return;
                    }

                    scriptManager.refreshGroup(groupInstance, i.getSuite(), false);
                });
    }

    public void unlockForce(int force) {
        this.unlockedForces.add(force);
        // Do not send PacketSceneForceUnlockNotify as it triggers an unintended exit screen on the client
    }

    public void lockForce(int force) {
        this.unlockedForces.remove(force);
        // Do not send PacketSceneForceLockNotify
    }

    public void selectWorktopOptionWith(SelectWorktopOptionReqOuterClass.SelectWorktopOptionReq req) {
        GameEntity entity = getEntityById(req.getGadgetEntityId());
        if (entity == null) {
            return;
        }

        if (entity instanceof EntityGadget gadget) {
            if (gadget.getContent() instanceof GadgetWorktop worktop) {
                boolean shouldDelete = worktop.onSelectWorktopOption(req);
                if (shouldDelete) {
                    entity.getScene().removeEntity(entity, VisionType.VisionType_VISION_REMOVE);
                }
            }
        }
    }

    public void saveGroups() {
        this.getScriptManager().getCachedGroupInstances().values().forEach(SceneGroupInstance::save);
    }
	
	public void hideIcewindSuitePresenceProp() {
		if (this.getId() != ICEWIND_SCENE_ID) {
			return;
		}

		var prop = this.getEntityByConfigId(ICEWIND_PROP_CONFIG_ID, ICEWIND_GROUP_ID);

		if (prop != null) {
			this.removeEntity(prop, VisionType.VisionType_VISION_REMOVE);

			Grasscutter.getLogger()
					.debug(
							"[IcewindSuiteFallback] Hid Icewind Suite presence prop: entityId={}, configId={}, groupId={}",
							prop.getId(),
							prop.getConfigId(),
							prop.getGroupId());
		}
	}

	private void restoreIcewindSuitePresencePropIfMissing() {
		if (this.getId() != ICEWIND_SCENE_ID) {
			return;
		}

		if (this.getEntityByConfigId(ICEWIND_PROP_CONFIG_ID, ICEWIND_GROUP_ID) != null) {
			return;
		}

		EntityGadget prop =
				new EntityGadget(
						this,
						ICEWIND_PROP_GADGET_ID,
						ICEWIND_PROP_POS.clone(),
						ICEWIND_PROP_ROT.clone());

		prop.setGroupId(ICEWIND_GROUP_ID);
		prop.setBlockId(ICEWIND_BLOCK_ID);
		prop.setConfigId(ICEWIND_PROP_CONFIG_ID);
		prop.setState(0);

		this.addEntity(prop);

		Grasscutter.getLogger()
				.debug(
						"[IcewindSuiteFallback] Restored Icewind Suite presence prop: entityId={}, configId={}, groupId={}",
						prop.getId(),
						prop.getConfigId(),
						prop.getGroupId());
	}

	private void checkIcewindSuiteFallbackReset() {
		if (this.getId() != ICEWIND_SCENE_ID) {
			return;
		}

		boolean hasIcewindBoss =
				this.getEntities().values().stream()
						.filter(e -> e instanceof EntityMonster)
						.map(e -> (EntityMonster) e)
						.anyMatch(m -> ICEWIND_FALLBACK_MONSTER_IDS.contains(m.getMonsterData().getId()));

		boolean propMissing =
				this.getEntityByConfigId(ICEWIND_PROP_CONFIG_ID, ICEWIND_GROUP_ID) == null;

		if (!hasIcewindBoss && !propMissing) {
			return;
		}

		boolean playerNearArena =
				this.getPlayers().stream()
						.anyMatch(p -> p.getPosition().computeDistance(ICEWIND_PROP_POS) <= 120.0);

		// Do not reset while the player is still near the arena.
		if (playerNearArena) {
			return;
		}

		if (hasIcewindBoss) {
			List<GameEntity> icewindBosses =
					this.getEntities().values().stream()
							.filter(e -> e instanceof EntityMonster)
							.filter(
									e ->
											ICEWIND_FALLBACK_MONSTER_IDS.contains(
													((EntityMonster) e).getMonsterData().getId()))
							.toList();

			this.removeEntities(icewindBosses, VisionType.VisionType_VISION_REMOVE);

			Grasscutter.getLogger()
					.debug(
							"[IcewindSuiteFallback] Removed active Icewind fallback boss after player left arena.");
		}

		var talkEntity = this.getEntityByConfigId(ICEWIND_TALK_CONFIG_ID, ICEWIND_GROUP_ID);

		if (talkEntity instanceof EntityGadget talkGadget && talkGadget.getState() != 0) {
			talkGadget.updateState(0);
		}
		
		this.icewindFallbackVirtualHp.clear();
		this.icewindFallbackVirtualMaxHp.clear();
		this.icewindFallbackSpawnTimes.clear();
		this.icewindFallbackLastHpRatios.clear();
		
		this.restoreIcewindSuitePresencePropIfMissing();
		this.resetIcewindSuiteFallbackWeather();
		this.restoreIcewindSuitePresencePropIfMissing();
	}
	
	private boolean isIcewindFallbackMonster(EntityMonster monster) {
		return this.getId() == ICEWIND_SCENE_ID
				&& monster.getGroupId() == ICEWIND_GROUP_ID
				&& ICEWIND_FALLBACK_MONSTER_IDS.contains(monster.getMonsterData().getId());
	}

	private float getIcewindHpRatio(EntityMonster monster) {
		float curHp = monster.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);
		float maxHp = monster.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);

		if (maxHp <= 0f) {
			return 1f;
		}

		return curHp / maxHp;
	}
	
	public void registerIcewindSuiteFallbackBoss(EntityMonster monster) {
		if (monster == null || !this.isIcewindFallbackMonster(monster)) {
			return;
		}

		float maxHp = monster.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);

		this.icewindFallbackSpawnTimes.put(monster.getId(), this.getSceneTimeSeconds());
		this.icewindFallbackLastHpRatios.put(monster.getId(), this.getIcewindHpRatio(monster));

		this.icewindFallbackVirtualMaxHp.putIfAbsent(monster.getId(), maxHp);
		this.icewindFallbackVirtualHp.putIfAbsent(monster.getId(), maxHp);

		this.setIcewindFallbackDisplayedHp(monster);
	}

	private void checkIcewindSuiteFallbackAntiStall(int sceneTime) {
		if (this.getId() != ICEWIND_SCENE_ID) {
			return;
		}

		var icewindBosses =
				this.getEntities().values().stream()
						.filter(e -> e instanceof EntityMonster)
						.map(e -> (EntityMonster) e)
						.filter(this::isIcewindFallbackMonster)
						.toList();

		if (icewindBosses.isEmpty()) {
			this.icewindFallbackVirtualHp.clear();
			this.icewindFallbackVirtualMaxHp.clear();
			this.icewindFallbackSpawnTimes.clear();
			this.icewindFallbackLastHpRatios.clear();
			return;
		}

		for (EntityMonster monster : icewindBosses) {
			if (!monster.isAlive()) {
				continue;
			}

			int bornTime =
					this.icewindFallbackSpawnTimes.computeIfAbsent(
							monster.getId(), id -> sceneTime);

			float currentRatio = this.getIcewindHpRatio(monster);
			float previousRatio =
					this.icewindFallbackLastHpRatios.getOrDefault(monster.getId(), currentRatio);

			boolean inClimaxHpZone = currentRatio <= ICEWIND_FALLBACK_CLIMAX_HP_RATIO;

			int refreshSeconds =
					inClimaxHpZone
							? ICEWIND_FALLBACK_LOW_HP_REFRESH_SECONDS
							: ICEWIND_FALLBACK_REFRESH_SECONDS;

			boolean timedRefresh = sceneTime - bornTime >= refreshSeconds;

			boolean crossedClimaxHpThreshold =
					previousRatio > ICEWIND_FALLBACK_CLIMAX_HP_RATIO
							&& currentRatio <= ICEWIND_FALLBACK_CLIMAX_HP_RATIO;

			this.icewindFallbackLastHpRatios.put(monster.getId(), currentRatio);

			if (timedRefresh || crossedClimaxHpThreshold) {
				this.refreshIcewindSuiteFallbackBoss(
						monster,
						sceneTime,
						timedRefresh
								? (inClimaxHpZone ? "low-hp-timer" : "timer")
								: "hp-threshold");
				return;
			}
		}

		var liveIds = icewindBosses.stream().map(EntityMonster::getId).collect(Collectors.toSet());
		this.icewindFallbackSpawnTimes.keySet().removeIf(id -> !liveIds.contains(id));
		this.icewindFallbackLastHpRatios.keySet().removeIf(id -> !liveIds.contains(id));
		this.icewindFallbackVirtualHp.keySet().removeIf(id -> !liveIds.contains(id));
		this.icewindFallbackVirtualMaxHp.keySet().removeIf(id -> !liveIds.contains(id));
	}
	
	private void refreshIcewindSuiteFallbackBoss(EntityMonster oldMonster, int sceneTime, String reason) {
		if (oldMonster == null || !oldMonster.isAlive()) {
			return;
		}

		int monsterId = oldMonster.getMonsterData().getId();

		var monsterData = GameData.getMonsterDataMap().get(monsterId);
		if (monsterData == null) {
			Grasscutter.getLogger()
					.warn("[IcewindSuiteFallback] Cannot refresh boss; missing monsterData for monsterId={}", monsterId);
			return;
		}

		float oldCurHp = oldMonster.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP);

		if (oldCurHp <= 0f) {
			return;
		}

		float oldMaxHp = oldMonster.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
		float virtualHp = this.icewindFallbackVirtualHp.getOrDefault(oldMonster.getId(), oldMaxHp);
		float virtualMaxHp = this.icewindFallbackVirtualMaxHp.getOrDefault(oldMonster.getId(), oldMaxHp);

		Position pos = oldMonster.getBornPos().clone();
		Position rot = oldMonster.getRotation().clone();

		EntityMonster replacement =
				new EntityMonster(
						this,
						monsterData,
						pos,
						rot,
						oldMonster.getLevel());

		replacement.setGroupId(oldMonster.getGroupId());
		replacement.setBlockId(oldMonster.getBlockId());
		replacement.setConfigId(oldMonster.getConfigId());
		replacement.setCampId(oldMonster.getCampId());
		replacement.setCampType(oldMonster.getCampType());
		replacement.setPoseId(oldMonster.getPoseId());
		replacement.setAiId(oldMonster.getAiId());
		replacement.setOwnerEntityId(oldMonster.getOwnerEntityId());
		replacement.setSummonedTag(oldMonster.getSummonedTag());

		float replacementMaxHp = replacement.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);

		this.icewindFallbackVirtualHp.remove(oldMonster.getId());
		this.icewindFallbackVirtualMaxHp.remove(oldMonster.getId());
		this.icewindFallbackSpawnTimes.remove(oldMonster.getId());
		this.icewindFallbackLastHpRatios.remove(oldMonster.getId());

		this.icewindFallbackVirtualHp.put(replacement.getId(), Math.min(virtualHp, virtualMaxHp));
		this.icewindFallbackVirtualMaxHp.put(replacement.getId(), virtualMaxHp);

		replacement.setFightProperty(
				FightProperty.FIGHT_PROP_CUR_HP,
				replacementMaxHp * ICEWIND_FALLBACK_SAFE_HP_RATIO);
				
		this.removeEntity(oldMonster, VisionType.VisionType_VISION_REMOVE);
		this.addEntities(List.of(replacement), VisionType.VisionType_VISION_BORN);

		this.registerIcewindSuiteFallbackBoss(replacement);
		this.setIcewindFallbackDisplayedHp(replacement);

		Grasscutter.getLogger()
				.debug(
						"[IcewindSuiteFallback] Refreshed boss to avoid broken Climax: reason={}, oldEntityId={}, newEntityId={}, monsterId={}, virtualHp={}/{}, actualHp={}, pos={}",
						reason,
						oldMonster.getId(),
						replacement.getId(),
						monsterId,
						virtualHp,
						virtualMaxHp,
						replacement.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP),
						pos);
	}
	
	public void activateIcewindSuiteFallbackWeather() {
		if (this.getId() != ICEWIND_SCENE_ID) {
			return;
		}

		for (Player player : this.getPlayers()) {
			player.setWeather(ICEWIND_WEATHER_ID, ClimateType.CLIMATE_SUNNY);
		}

		this.icewindSuiteFallbackWeatherActive = true;

		Grasscutter.getLogger()
				.debug("[IcewindSuiteFallback] Set arena weather to {}", ICEWIND_WEATHER_ID);
	}

	public void resetIcewindSuiteFallbackWeather() {
		if (this.getId() != ICEWIND_SCENE_ID) {
			return;
		}

		if (!this.icewindSuiteFallbackWeatherActive) {
			return;
		}

		for (Player player : this.getPlayers()) {
			this.resetIcewindSuiteFallbackWeather(player);
		}
		
		this.icewindSuiteFallbackWeatherActive = false;
	}

	private void resetIcewindSuiteFallbackWeather(Player player) {
		if (player == null) {
			return;
		}

		player.setWeather(ICEWIND_DEFAULT_WEATHER_ID, ClimateType.CLIMATE_SUNNY);
	}

	public void teleportPlayerToIcewindSuiteArena(Player player) {
		if (player == null || player.getWorld() == null || player.getScene() != this) {
			return;
		}

		var teleportProps =
				TeleportProperties.builder()
						.sceneId(this.getId())
						.teleportType(PlayerTeleportEvent.TeleportType.COMMAND)
						.enterReason(EnterReason.Gm)
						.enterType(EnterTypeOuterClass.EnterType.EnterType_ENTER_GOTO)
						.teleportTo(ICEWIND_PLAYER_START_POS.clone())
						.teleportRot(ICEWIND_PLAYER_START_ROT.clone())
						.build();

		if (player.getWorld().transferPlayerToScene(player, teleportProps)) {
			player.sendPacket(new PacketScenePlayerLocationNotify(this));

			Grasscutter.getLogger()
					.debug(
							"[IcewindSuiteFallback] Teleported player {} near Icewind Suite arena: pos={}",
							player.getUid(),
							ICEWIND_PLAYER_START_POS);
		}
	}
	
	public void queueIcewindSuiteArenaTeleport(Player player, long delayMs) {
		if (player == null || player.getScene() != this || this.getId() != ICEWIND_SCENE_ID) {
			return;
		}

		this.pendingIcewindSuiteArenaTeleports.put(
				player.getUid(),
				System.currentTimeMillis() + delayMs);

		Grasscutter.getLogger()
				.debug(
						"[IcewindSuiteFallback] Queued delayed arena teleport for player {} in {}ms",
						player.getUid(),
						delayMs);
	}

	private void processPendingIcewindSuiteArenaTeleports() {
		if (this.getId() != ICEWIND_SCENE_ID || this.pendingIcewindSuiteArenaTeleports.isEmpty()) {
			return;
		}

		long now = System.currentTimeMillis();

		var iterator = this.pendingIcewindSuiteArenaTeleports.entrySet().iterator();

		while (iterator.hasNext()) {
			var entry = iterator.next();

			if (entry.getValue() > now) {
				continue;
			}

			iterator.remove();

			Player targetPlayer =
					this.getPlayers().stream()
							.filter(p -> p.getUid() == entry.getKey())
							.findFirst()
							.orElse(null);

			if (targetPlayer == null || targetPlayer.getScene() != this) {
				continue;
			}

			this.teleportPlayerToIcewindSuiteArena(targetPlayer);
		}
	}
	
	private void setIcewindFallbackDisplayedHp(EntityMonster monster) {
		if (monster == null || !this.isIcewindFallbackMonster(monster)) {
			return;
		}

		float maxHp = monster.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
		float virtualMaxHp = this.icewindFallbackVirtualMaxHp.getOrDefault(monster.getId(), maxHp);
		float virtualHp = this.icewindFallbackVirtualHp.getOrDefault(monster.getId(), virtualMaxHp);

		if (maxHp <= 0f || virtualMaxHp <= 0f) {
			return;
		}

		float virtualRatio = Math.max(0f, Math.min(1f, virtualHp / virtualMaxHp));

		// Keep the actual monster HP above the broken Climax threshold.
		// The visible HP bar will move between 100% and ~78%, then the boss dies when virtual HP reaches 0.
		float displayRatio =
				ICEWIND_FALLBACK_MIN_DISPLAY_HP_RATIO
						+ ((1f - ICEWIND_FALLBACK_MIN_DISPLAY_HP_RATIO) * virtualRatio);

		displayRatio = Math.max(ICEWIND_FALLBACK_MIN_DISPLAY_HP_RATIO, displayRatio);

		monster.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, maxHp * displayRatio);
		this.broadcastPacket(new PacketEntityFightPropUpdateNotify(monster, FightProperty.FIGHT_PROP_CUR_HP));
	}

	private boolean handleIcewindSuiteVirtualDamage(
			EntityMonster monster, float amount, int attackerId) {
		if (monster == null || !this.isIcewindFallbackMonster(monster)) {
			return false;
		}

		if (amount <= 0f || !monster.isAlive()) {
			return true;
		}

		float maxHp = monster.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
		float virtualMaxHp = this.icewindFallbackVirtualMaxHp.getOrDefault(monster.getId(), maxHp);
		float virtualHp = this.icewindFallbackVirtualHp.getOrDefault(monster.getId(), virtualMaxHp);

		virtualHp = Math.max(0f, virtualHp - amount);

		this.icewindFallbackVirtualMaxHp.put(monster.getId(), virtualMaxHp);
		this.icewindFallbackVirtualHp.put(monster.getId(), virtualHp);

		if (virtualHp <= 0f) {
			monster.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0f);
			this.broadcastPacket(new PacketEntityFightPropUpdateNotify(monster, FightProperty.FIGHT_PROP_CUR_HP));

			this.icewindFallbackVirtualHp.remove(monster.getId());
			this.icewindFallbackVirtualMaxHp.remove(monster.getId());
			this.icewindFallbackSpawnTimes.remove(monster.getId());
			this.icewindFallbackLastHpRatios.remove(monster.getId());

			this.killEntity(monster, attackerId);
			return true;
		}

		this.setIcewindFallbackDisplayedHp(monster);

		Grasscutter.getLogger()
				.debug(
						"[IcewindSuiteFallback] Virtual damage: entityId={}, monsterId={}, damage={}, virtualHp={}/{}, actualHp={}",
						monster.getId(),
						monster.getMonsterData().getId(),
						amount,
						virtualHp,
						virtualMaxHp,
						monster.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));

		return true;
	}
	
	private boolean isGoldenWolflordMonster(EntityMonster monster) {
		return this.getId() == GOLDEN_WOLFLORD_SCENE_ID
				&& monster != null
				&& monster.getGroupId() == GOLDEN_WOLFLORD_GROUP_ID
				&& monster.getConfigId() == GOLDEN_WOLFLORD_CONFIG_ID
				&& monster.getMonsterData() != null
				&& monster.getMonsterData().getId() == GOLDEN_WOLFLORD_MONSTER_ID;
	}

	private boolean isPrematureGoldenWolflordBlossomSpawn(SpawnDataEntry entry) {
		if (entry == null || entry.getGroup() == null) {
			return false;
		}

		if (this.getId() != GOLDEN_WOLFLORD_SCENE_ID) {
			return false;
		}

		if (entry.getGroup().getGroupId() != GOLDEN_WOLFLORD_GROUP_ID) {
			return false;
		}

		return entry.getConfigId() == GOLDEN_WOLFLORD_BLOSSOM_CONFIG_ID
				&& entry.getGadgetId() == GOLDEN_WOLFLORD_BLOSSOM_GADGET_ID;
	}

	private void checkGoldenWolflordFallbackWeatherState() {
		if (this.getId() != GOLDEN_WOLFLORD_SCENE_ID) {
			return;
		}

		this.cleanupGoldenWolflordVirtualHp();

		boolean playerNearArena =
				this.getPlayers().stream()
						.anyMatch(
								player ->
										player.getPosition().computeDistance(GOLDEN_WOLFLORD_ARENA_POS)
												<= GOLDEN_WOLFLORD_WEATHER_RADIUS);

		boolean bossAlive =
				this.getEntities().values().stream()
						.anyMatch(
								entity ->
										entity instanceof EntityMonster monster
												&& this.isGoldenWolflordMonster(monster)
												&& monster.isAlive());

		if (playerNearArena && bossAlive) {
			this.activateGoldenWolflordFallbackWeather();
		} else {
			this.resetGoldenWolflordFallbackWeather();
		}
	}

	private void activateGoldenWolflordFallbackWeather() {
		if (this.getId() != GOLDEN_WOLFLORD_SCENE_ID) {
			return;
		}

		if (this.goldenWolflordWeatherActive) {
			return;
		}

		for (Player player : this.getPlayers()) {
			/*
			 * Golden Wolflord temporarily replaces the regional Tsurumi weather.
			 * Forget the previous regional state so it will be recalculated after
			 * the encounter weather ends.
			 */
			this.seiraiFallbackWeatherByUid.remove(player.getUid());

			player.setWeather(
					GOLDEN_WOLFLORD_WEATHER_ID,
					ClimateType.CLIMATE_SUNNY);
		}

		this.goldenWolflordWeatherActive = true;

		Grasscutter.getLogger()
				.debug("[GoldenWolflordFallback] Set arena weather to {}", GOLDEN_WOLFLORD_WEATHER_ID);
	}

	private void resetGoldenWolflordFallbackWeather() {
		if (this.getId() != GOLDEN_WOLFLORD_SCENE_ID) {
			return;
		}

		if (!this.goldenWolflordWeatherActive) {
			return;
		}

		/*
		 * Release boss-weather ownership before recalculating regional weather.
		 * Otherwise applySeiraiFallbackWeather() would correctly refuse to run.
		 */
		this.goldenWolflordWeatherActive = false;

		for (Player player : this.getPlayers()) {
			/*
			 * First restore the global default. The regional resolver below will
			 * replace it with 3073 when the player is still inside Tsurumi.
			 */
			this.resetGoldenWolflordFallbackWeather(player);

			this.seiraiFallbackWeatherByUid.remove(
					player.getUid());

			this.applySeiraiFallbackWeather(
					player,
					false);
		}
	}

	private void resetGoldenWolflordFallbackWeather(Player player) {
		if (player == null) {
			return;
		}

		player.setWeather(GOLDEN_WOLFLORD_DEFAULT_WEATHER_ID, ClimateType.CLIMATE_SUNNY);
	}

	private void cleanupGoldenWolflordVirtualHp() {
		var liveIds =
				this.getEntities().values().stream()
						.filter(entity -> entity instanceof EntityMonster)
						.map(entity -> (EntityMonster) entity)
						.filter(this::isGoldenWolflordMonster)
						.filter(EntityMonster::isAlive)
						.map(EntityMonster::getId)
						.collect(Collectors.toSet());

		this.goldenWolflordVirtualHp.keySet().removeIf(id -> !liveIds.contains(id));
		this.goldenWolflordVirtualMaxHp.keySet().removeIf(id -> !liveIds.contains(id));
	}

	private void setGoldenWolflordDisplayedHp(EntityMonster monster) {
		if (monster == null || !this.isGoldenWolflordMonster(monster)) {
			return;
		}

		float maxHp = monster.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
		float virtualMaxHp = this.goldenWolflordVirtualMaxHp.getOrDefault(monster.getId(), maxHp);
		float virtualHp = this.goldenWolflordVirtualHp.getOrDefault(monster.getId(), virtualMaxHp);

		if (maxHp <= 0f || virtualMaxHp <= 0f) {
			return;
		}

		float virtualRatio = Math.max(0f, Math.min(1f, virtualHp / virtualMaxHp));

		// Keep real HP above the broken 70% shield threshold.
		// Visible HP moves from 100% down to ~72%, then the boss dies when virtual HP reaches 0.
		float displayRatio =
				GOLDEN_WOLFLORD_MIN_DISPLAY_HP_RATIO
						+ ((1f - GOLDEN_WOLFLORD_MIN_DISPLAY_HP_RATIO) * virtualRatio);

		displayRatio = Math.max(GOLDEN_WOLFLORD_MIN_DISPLAY_HP_RATIO, displayRatio);

		monster.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, maxHp * displayRatio);
		this.broadcastPacket(new PacketEntityFightPropUpdateNotify(monster, FightProperty.FIGHT_PROP_CUR_HP));
	}

	private boolean handleGoldenWolflordVirtualDamage(
			EntityMonster monster, float amount, int attackerId) {
		if (monster == null || !this.isGoldenWolflordMonster(monster)) {
			return false;
		}

		if (!monster.isAlive()) {
			return true;
		}

		float maxHp = monster.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
		float virtualMaxHp = this.goldenWolflordVirtualMaxHp.getOrDefault(monster.getId(), maxHp);
		float virtualHp = this.goldenWolflordVirtualHp.getOrDefault(monster.getId(), virtualMaxHp);

		this.goldenWolflordVirtualMaxHp.putIfAbsent(monster.getId(), virtualMaxHp);
		this.goldenWolflordVirtualHp.putIfAbsent(monster.getId(), virtualHp);

		if (amount <= 0f) {
			this.setGoldenWolflordDisplayedHp(monster);
			return true;
		}

		virtualHp = Math.max(0f, virtualHp - amount);

		this.goldenWolflordVirtualMaxHp.put(monster.getId(), virtualMaxHp);
		this.goldenWolflordVirtualHp.put(monster.getId(), virtualHp);

		if (virtualHp <= 0f) {
			monster.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, 0f);
			this.broadcastPacket(new PacketEntityFightPropUpdateNotify(monster, FightProperty.FIGHT_PROP_CUR_HP));

			this.goldenWolflordVirtualHp.remove(monster.getId());
			this.goldenWolflordVirtualMaxHp.remove(monster.getId());

			this.killEntity(monster, attackerId);
			this.resetGoldenWolflordFallbackWeather();
			return true;
		}

		this.setGoldenWolflordDisplayedHp(monster);

		Grasscutter.getLogger()
				.debug(
						"[GoldenWolflordFallback] Virtual damage: entityId={}, damage={}, virtualHp={}/{}, actualHp={}",
						monster.getId(),
						amount,
						virtualHp,
						virtualMaxHp,
						monster.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP));

		return true;
	}
	
	private void checkSeiraiFallbackWeather() {
		if (this.getId() != SEIRAI_SCENE_ID) {
			return;
		}

		for (Player player : this.getPlayers()) {
			this.applySeiraiFallbackWeather(player, true);
		}
	}

	private int getDesiredSeiraiWeather(Position pos) {
		if (pos == null) {
			return SEIRAI_WEATHER_DEFAULT;
		}
		
		/*
		 * Hiisi Island participates in the Scene 3 regional-weather resolver.
		 * Its 6.6 weather profile provides the purple/mystic Nod-Krai
		 * atmosphere that is otherwise missing when Scene 3 remains on weather ID 0.
		 */
		if (this.isInHiisiWeatherZone(pos)) {
			return HIISI_WEATHER_GENERAL;
		}
		
		/*
		 * Tsurumi Island participates in the Scene 3 regional-weather resolver.
		 * Golden Wolflord weather priority is handled separately before this
		 * result is applied.
		 */
		if (this.isInTsurumiWeatherZone(pos)) {
			return TSURUMI_WEATHER_GENERAL;
		}

		 /*
		 * Watatsumi Island is geographically separate from every Seirai zone,
		 * so this can safely participate in the same Scene 3 regional resolver.
		 */
		if (this.isInSangonomiyaWeatherZone(pos)) {
			return SANGONOMIYA_WEATHER_GENERAL;
		}

		if (this.isInThunderManifestationWeatherZone(pos)) {
			return SEIRAI_WEATHER_THUNDER_MANIFESTATION;
		}

		/*
		 * Apply Asase Shrine's dedicated weather profile.
		 */
		if (this.isInAsaseShrineWeatherZone(pos)) {
			return SEIRAI_WEATHER_ASASE_SHRINE;
		}

		if (this.isNear2d(
				pos,
				SEIRAI_SEIRAIMARU_POS,
				SEIRAI_SEIRAIMARU_RADIUS)) {
			return SEIRAI_WEATHER_SEIRAIMARU;
		}

		if (this.isNear2d(
				pos,
				SEIRAI_INITIAL_ISLAND_POS,
				SEIRAI_INITIAL_ISLAND_RADIUS)) {
			return SEIRAI_WEATHER_INITIAL_ISLAND;
		}

		if (this.isNear2d(
				pos,
				SEIRAI_AMAKUMO_LOWER_POS,
				SEIRAI_AMAKUMO_LOWER_RADIUS)) {
			return SEIRAI_WEATHER_AMAKUMO_LOWER;
		}

		return SEIRAI_WEATHER_DEFAULT;
	}

	private boolean isInThunderManifestationWeatherZone(Position pos) {
		return pos.getY() >= THUNDER_MANIFESTATION_MIN_WEATHER_Y
				&& this.isNear2d(
						pos,
						THUNDER_MANIFESTATION_ARENA_POS,
						THUNDER_MANIFESTATION_WEATHER_RADIUS);
	}
	
	private boolean isInAsaseShrineWeatherZone(Position pos) {
		return pos != null
				&& this.isNear2d(
						pos,
						SEIRAI_ASASE_SHRINE_POS,
						SEIRAI_ASASE_SHRINE_RADIUS);
	}

	private boolean isInSangonomiyaWeatherZone(Position pos) {
		if (pos == null) {
			return false;
		}

		/*
		 * Only X and Z are evaluated. The player's Y coordinate is deliberately
		 * ignored so caves, elevated shrine platforms, waterfalls, and lower
		 * terrain inside the horizontal boundary receive the same weather.
		 */
		double x = pos.getX();
		double z = pos.getZ();
		boolean inside = false;

		for (int i = 0, j = SANGONOMIYA_WEATHER_PERIMETER_XZ.length - 1;
				i < SANGONOMIYA_WEATHER_PERIMETER_XZ.length;
				j = i++) {

			double xi = SANGONOMIYA_WEATHER_PERIMETER_XZ[i][0];
			double zi = SANGONOMIYA_WEATHER_PERIMETER_XZ[i][1];

			double xj = SANGONOMIYA_WEATHER_PERIMETER_XZ[j][0];
			double zj = SANGONOMIYA_WEATHER_PERIMETER_XZ[j][1];

			boolean crossesZ = (zi > z) != (zj > z);

			if (crossesZ) {
				double edgeX =
						(xj - xi) * (z - zi) / (zj - zi) + xi;

				if (x < edgeX) {
					inside = !inside;
				}
			}
		}

		return inside;
	}
	
	private boolean isInTsurumiWeatherZone(Position pos) {
		return this.isInsideWeatherPerimeterXZ(
				pos,
				TSURUMI_WEATHER_PERIMETER_XZ);
	}
	
	private boolean isInHiisiWeatherZone(Position pos) {
		return this.isInsideWeatherPerimeterXZ(
				pos,
				HIISI_WEATHER_PERIMETER_XZ);
	}

	private boolean isInsideWeatherPerimeterXZ(
			Position pos,
			double[][] perimeter) {

		if (pos == null
				|| perimeter == null
				|| perimeter.length < 3) {
			return false;
		}

		double x = pos.getX();
		double z = pos.getZ();
		boolean inside = false;

		for (int i = 0, j = perimeter.length - 1;
				i < perimeter.length;
				j = i++) {

			double xi = perimeter[i][0];
			double zi = perimeter[i][1];

			double xj = perimeter[j][0];
			double zj = perimeter[j][1];

			boolean crossesZ =
					(zi > z) != (zj > z);

			if (crossesZ) {
				double edgeX =
						(xj - xi)
								* (z - zi)
								/ (zj - zi)
								+ xi;

				if (x < edgeX) {
					inside = !inside;
				}
			}
		}

		return inside;
	}

	private boolean isNear2d(Position pos, Position center, float radius) {
		return distance2d(pos, center) <= radius;
	}

	private static float distance2d(Position a, Position b) {
		float dx = a.getX() - b.getX();
		float dz = a.getZ() - b.getZ();

		return (float) Math.sqrt((dx * dx) + (dz * dz));
	}
	
	private void applySeiraiFallbackWeather(Player player, boolean allowDefaultReset) {

		if (player == null || this.getId() != SEIRAI_SCENE_ID) {
			return;
		}
		
		/*
		 * Golden Wolflord encounter weather owns the entire Scene 3 weather state
		 * while active. Do not let Tsurumi's regional weather replace it.
		 *
		 * Remove the regional tracking entry so that Tsurumi weather is applied
		 * again after the encounter weather releases ownership.
		 */
		if (this.goldenWolflordWeatherActive) {
			this.seiraiFallbackWeatherByUid.remove(
					player.getUid());

			return;
		}
		
		Position pos = player.getPosition();

		boolean inAsaseShrineZone =
				this.isInAsaseShrineWeatherZone(pos);

		int desiredWeather =
				this.getDesiredSeiraiWeather(pos);

		boolean hadFallbackWeather =
				this.seiraiFallbackWeatherByUid.containsKey(
						player.getUid());

		int currentWeather =
				this.seiraiFallbackWeatherByUid.getOrDefault(
						player.getUid(),
						SEIRAI_WEATHER_DEFAULT);

		if (inAsaseShrineZone) {
			if (!hadFallbackWeather
					|| currentWeather != SEIRAI_WEATHER_ASASE_SHRINE) {

				player.setWeather(
						SEIRAI_WEATHER_ASASE_SHRINE,
						ClimateType.CLIMATE_SUNNY);
			}

			this.seiraiFallbackWeatherByUid.put(
					player.getUid(),
					SEIRAI_WEATHER_ASASE_SHRINE);

			return;
		}

		if (desiredWeather == SEIRAI_WEATHER_DEFAULT) {
			boolean dragonspineOwnsWeather =
					this.getDesiredDragonspineWeather(pos)
							!= DRAGONSPINE_WEATHER_DEFAULT;

			if (hadFallbackWeather && dragonspineOwnsWeather) {
				this.seiraiFallbackWeatherByUid.remove(
						player.getUid());

				return;
			}

			/*
			 * Reset to weather ID 0 when the player leaves every region owned by
			 * this Scene 3 regional-weather fallback, including Seirai, Watatsumi,
			 * Tsurumi, and Hiisi Island.
			 */
			if (allowDefaultReset && hadFallbackWeather) {
				player.setWeather(
						SEIRAI_WEATHER_DEFAULT,
						ClimateType.CLIMATE_SUNNY);

				this.seiraiFallbackWeatherByUid.remove(
						player.getUid());
			}

			return;
		}

		if (!hadFallbackWeather
				|| desiredWeather != currentWeather) {

			player.setWeather(
					desiredWeather,
					ClimateType.CLIMATE_SUNNY);

			this.seiraiFallbackWeatherByUid.put(
					player.getUid(),
					desiredWeather);
		}
	}
	
	private void checkDragonspineFallbackWeather() {
    	if (this.getId() != DRAGONSPINE_SCENE_ID) {
        	return;
   		}

    	for (Player player : this.getPlayers()) {
        	this.applyDragonspineFallbackWeather(player, true);
    	}	
	}

	private void applyDragonspineFallbackWeather(Player player, boolean allowDefaultReset) {
		if (player == null || this.getId() != DRAGONSPINE_SCENE_ID) {
			return;
		}

		int desiredWeather = this.getDesiredDragonspineWeather(player.getPosition());
		boolean hadFallbackWeather = this.dragonspineFallbackWeatherByUid.containsKey(player.getUid());
		int currentWeather =
				this.dragonspineFallbackWeatherByUid.getOrDefault(
						player.getUid(), DRAGONSPINE_WEATHER_DEFAULT);

		if (desiredWeather == DRAGONSPINE_WEATHER_DEFAULT) {
			boolean seiraiOwnsWeather = this.getDesiredSeiraiWeather(player.getPosition()) != SEIRAI_WEATHER_DEFAULT;

			if (hadFallbackWeather && seiraiOwnsWeather) {
				this.dragonspineFallbackWeatherByUid.remove(player.getUid());
				return;
			}

			if (allowDefaultReset && hadFallbackWeather) {
				player.setWeather(DRAGONSPINE_WEATHER_DEFAULT, ClimateType.CLIMATE_SUNNY);
				this.dragonspineFallbackWeatherByUid.remove(player.getUid());
			}

			return;
		}

		if (!hadFallbackWeather || desiredWeather != currentWeather) {
			player.setWeather(desiredWeather, ClimateType.CLIMATE_SUNNY);
			this.dragonspineFallbackWeatherByUid.put(player.getUid(), desiredWeather);
		}
	}

	private int getDesiredDragonspineWeather(Position pos) {
		if (pos == null) {
			return DRAGONSPINE_WEATHER_DEFAULT;
		}

		boolean inDragonspine =
				this.isInDragonspineWeatherZone(pos);

		/*
		 * The peak profile takes priority at high altitude.
		 *
		 * Y >= 438:
		 *     weather 2023
		 *
		 * Y < 438:
		 *     continue evaluating local Dragonspine weather
		 */
		if (inDragonspine
				&& pos.getY() >= DRAGONSPINE_PEAK_MIN_Y) {

			return DRAGONSPINE_WEATHER_PEAK;
		}

		/*
		 * Use the boss profile only inside the actual Cryo Hypostasis arena.
		 */
		if (this.isInCryoHypostasisWeatherSensitiveZone(pos)) {
			return DRAGONSPINE_WEATHER_CRYO_HYPOSTASIS;
		}

		if (inDragonspine) {
			return DRAGONSPINE_WEATHER_GENERAL;
		}

		return DRAGONSPINE_WEATHER_DEFAULT;
	}

	private boolean isInDragonspineWeatherZone(Position pos) {
		if (pos == null) {
			return false;
		}

		/*
		 * Standard ray-casting point-in-polygon test using only X and Z.
		 * The comparison form avoids division by zero for horizontal edges.
		 */
		double x = pos.getX();
		double z = pos.getZ();
		boolean inside = false;

		for (int i = 0, j = DRAGONSPINE_WEATHER_PERIMETER_XZ.length - 1;
				i < DRAGONSPINE_WEATHER_PERIMETER_XZ.length;
				j = i++) {

			double xi = DRAGONSPINE_WEATHER_PERIMETER_XZ[i][0];
			double zi = DRAGONSPINE_WEATHER_PERIMETER_XZ[i][1];
			double xj = DRAGONSPINE_WEATHER_PERIMETER_XZ[j][0];
			double zj = DRAGONSPINE_WEATHER_PERIMETER_XZ[j][1];

			boolean crossesZ = (zi > z) != (zj > z);

			if (crossesZ) {
				double edgeX = (xj - xi) * (z - zi) / (zj - zi) + xi;

				if (x < edgeX) {
					inside = !inside;
				}
			}
		}

		return inside;
	}

	private void updateDragonspineClimate(Player player) {
		if (player == null) {
			return;
		}

		int uid = player.getUid();
		long now = System.currentTimeMillis();

		Long lastUpdate = this.sheerColdLastUpdateByUid.put(uid, now);

		if (lastUpdate == null) {
			return;
		}

		/*
		 * PlayerSetPauseReq already propagates the pause state to World, Player and Scene.
		 * Keep refreshing lastUpdate while paused so the climate loop cannot deal queued
		 * damage or meter changes immediately after the player closes a menu.
		 */
		if (this.isPaused || player.isPaused() || this.getWorld().isPaused()) {
			return;
		}

		float elapsedSeconds = (now - lastUpdate) / 1000.0f;

		if (elapsedSeconds <= 0.0f) {
			return;
		}

		elapsedSeconds = Math.min(elapsedSeconds, 1.0f);

		boolean inSubzeroClimate =
				this.getId() == DRAGONSPINE_SCENE_ID
						&& this.isInDragonspineWeatherZone(player.getPosition());

		boolean nearWarmthSource =
				inSubzeroClimate && this.isNearDragonspineWarmthSource(player);

		int current = player.getProperty(PlayerProperty.PROP_CUR_CLIMATE_METER);

		float preciseMeter =
				this.sheerColdMeterByUid.getOrDefault(uid, (float) current);

		float nextMeter;

		if (inSubzeroClimate) {
			player.setProperty(PlayerProperty.PROP_CUR_CLIMATE_TYPE, 1, true);
			player.setProperty(PlayerProperty.PROP_CUR_CLIMATE_AREA_CLIMATE_TYPE, 1, true);

			if (nearWarmthSource) {
				nextMeter =
						Math.max(
								preciseMeter
										- SHEER_COLD_WARMTH_DRAIN_PER_SECOND * elapsedSeconds,
								0.0f);
			} else {
				nextMeter =
						Math.min(
								preciseMeter
										+ SHEER_COLD_GAIN_PER_SECOND * elapsedSeconds,
								SHEER_COLD_MAX);
			}
		} else {
			nextMeter =
					Math.max(
							preciseMeter
									- SHEER_COLD_DRAIN_PER_SECOND * elapsedSeconds,
							0.0f);

			if (nextMeter <= 0.0f) {
				player.setProperty(PlayerProperty.PROP_CUR_CLIMATE_TYPE, 0, true);
				player.setProperty(PlayerProperty.PROP_CUR_CLIMATE_AREA_CLIMATE_TYPE, 0, true);
			}
		}

		this.sheerColdMeterByUid.put(uid, nextMeter);

		int next = Math.round(nextMeter);

		if (next != current) {
			player.setProperty(PlayerProperty.PROP_CUR_CLIMATE_METER, next, true);
		}

		if (next >= SHEER_COLD_MAX) {
			this.applySheerColdDamage(player);
		} else {
			this.sheerColdLastDamageByUid.remove(uid);
		}
	}

	private boolean isNearDragonspineWarmthSource(Player player) {
		Position playerPos = player.getPosition();

		if (playerPos == null) {
			return false;
		}

		if (this.isNearDragonspineWarmScenePoint(playerPos)) {
			return true;
		}

		for (GameEntity entity : this.getEntities().values()) {
			if (!(entity instanceof EntityBaseGadget gadget)
					|| !entity.isAlive()
					|| gadget.getPosition() == null
					|| playerPos.computeDistance(gadget.getPosition())
							> SHEER_COLD_GADGET_WARMTH_RADIUS) {
				continue;
			}

			if (!this.isActiveDragonspineWarmthGadget(gadget)) {
				continue;
			}

			return true;
		}

		return false;
	}

	private boolean isNearDragonspineWarmScenePoint(Position playerPos) {
		List<Integer> pointIds = GameData.getScenePointsPerScene().get(this.getId());

		if (pointIds == null || pointIds.isEmpty()) {
			return false;
		}

		for (int pointId : pointIds) {
			var entry = GameData.getScenePointEntryById(this.getId(), pointId);

			if (entry == null || entry.getPointData() == null) {
				continue;
			}

			var pointData = entry.getPointData();
			String pointType = pointData.getType();

			if (pointType == null) {
				continue;
			}

			String normalizedType = pointType.toLowerCase(Locale.ROOT);

			/*
			 * SceneTransPoint covers normal Teleport Waypoints in the bin output.
			 * Keep the Statue check separate in case this resource set labels statues
			 * with a dedicated point type.
			 */
			if (!normalizedType.contains("transpoint")
					&& !normalizedType.contains("statue")) {
				continue;
			}

			Position pointPos =
					pointData.getTranPos() != null
							? pointData.getTranPos()
							: pointData.getPos();

			if (pointPos != null
					&& playerPos.computeDistance(pointPos)
							<= SHEER_COLD_SCENE_POINT_WARMTH_RADIUS) {
				return true;
			}
		}

		return false;
	}

	private boolean isActiveDragonspineWarmthGadget(EntityBaseGadget gadget) {
		int state = 0;
		ConfigEntityGadget configGadget = null;
		var gadgetData = GameData.getGadgetDataMap().get(gadget.getGadgetId());

		if (gadget instanceof EntityGadget serverGadget) {
			state = serverGadget.getState();
			configGadget = serverGadget.getConfigGadget();
		} else if (gadget instanceof EntityClientGadget clientGadget) {
			state = clientGadget.getGadgetState();
			configGadget = clientGadget.getConfigGadget();
		}

		/* GearStop is the normal inactive state for braziers and similar mechanisms. */
		if (state == ScriptGadgetState.GearStop) {
			return false;
		}

		/*
		 * Use exact IDs for sources confirmed by the runtime probe. This avoids
		 * depending on localized/display terminology or inconsistent internal names.
		 */
		if (DRAGONSPINE_CONFIRMED_WARMTH_GADGET_IDS.contains(gadget.getGadgetId())) {
			return true;
		}

		StringBuilder descriptor = new StringBuilder();

		if (gadgetData != null) {
			this.appendWarmthDescriptor(descriptor, gadgetData.getJsonName());
			this.appendWarmthDescriptor(descriptor, gadgetData.getItemJsonName());

			if (gadgetData.getTags() != null) {
				for (String tag : gadgetData.getTags()) {
					this.appendWarmthDescriptor(descriptor, tag);
				}
			}
		}

		if (configGadget != null && configGadget.getAbilities() != null) {
			configGadget
					.getAbilities()
					.forEach(
							ability -> {
								this.appendWarmthDescriptor(descriptor, ability.getAbilityName());
								this.appendWarmthDescriptor(descriptor, ability.getAbilityID());
								this.appendWarmthDescriptor(descriptor, ability.getAbilityOverride());
							});
		}

		String normalized = descriptor.toString().toLowerCase(Locale.ROOT);

		for (String hint : DRAGONSPINE_WARMTH_NAME_HINTS) {
			if (normalized.contains(hint)) {
				return true;
			}
		}

		return false;
	}

	private void appendWarmthDescriptor(StringBuilder descriptor, String value) {
		if (value == null || value.isBlank()) {
			return;
		}

		if (!descriptor.isEmpty()) {
			descriptor.append(' ');
		}

		descriptor.append(value);
	}

	private void applySheerColdDamage(Player player) {
		if (player == null
				|| this.isPaused
				|| player.isPaused()
				|| this.getWorld().isPaused()) {
			return;
		}

		int uid = player.getUid();
		int now = (int) (System.currentTimeMillis() / 1000L);

		int lastDamage = this.sheerColdLastDamageByUid.getOrDefault(uid, now - 1);

		if (now - lastDamage < 1) {
			return;
		}

		this.sheerColdLastDamageByUid.put(uid, now);

		var avatar = player.getTeamManager().getCurrentAvatarEntity();

		if (avatar == null || avatar.isDead()) {
			return;
		}

		float maxHp = avatar.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP);
		float damage = maxHp * 0.01f + 150.0f;

		avatar.damage(
				damage,
				PropChangeReason.PropChangeReason_PROP_CHANGE_ABILITY,
				ChangeHpReason.ChangeHpReason_CHANGE_HP_SUB_ABILITY);
	}

	private boolean isInCryoHypostasisWeatherSensitiveZone(Position pos) {
		return this.isNear2d(
				pos,
				DRAGONSPINE_CRYO_HYPOSTASIS_ARENA_POS,
				DRAGONSPINE_CRYO_HYPOSTASIS_WEATHER_RADIUS);
	}

	private void checkOceanidFallbackWeather() {
		if (this.getId() != OCEANID_SCENE_ID) {
			return;
		}

		for (Player player : this.getPlayers()) {
				this.applyOceanidFallbackWeather(player, true);
		}
	}

	private int getDesiredOceanidWeather(Position pos) {
		if (pos == null) {
			return OCEANID_WEATHER_DEFAULT;
		}

		// Once Oceanid is defeated and the blossom is present, keep the area sunny until the player leaves the reset radius.
		// Otherwise the normal position weather fallback would immediately reapply weather 2021.
		if (this.oceanidFallbackDefeatedUntilLeave
				|| this.oceanidFallbackWaitingForLeave
				|| this.hasOceanidRewardBlossom()) {
			return OCEANID_WEATHER_DEFAULT;
		}

		if (this.isNear2d(pos, OCEANID_ARENA_POS, OCEANID_WEATHER_RADIUS)) {
			return OCEANID_WEATHER_ID;
		}

		return OCEANID_WEATHER_DEFAULT;
	}

	private void applyOceanidFallbackWeather(Player player, boolean allowDefaultReset) {
		if (player == null || this.getId() != OCEANID_SCENE_ID) {
			return;
		}

		Position pos = player.getPosition();
		int desiredWeather = this.getDesiredOceanidWeather(pos);
		int uid = player.getUid();

		boolean hadOceanidWeather = this.oceanidFallbackWeatherByUid.containsKey(uid);
		int previousWeather = this.oceanidFallbackWeatherByUid.getOrDefault(uid, OCEANID_WEATHER_DEFAULT);

		if (desiredWeather == OCEANID_WEATHER_DEFAULT) {
			boolean seiraiOwnsWeather = this.getDesiredSeiraiWeather(pos) != SEIRAI_WEATHER_DEFAULT;
			boolean dragonspineOwnsWeather = this.getDesiredDragonspineWeather(pos) != DRAGONSPINE_WEATHER_DEFAULT;

			if (hadOceanidWeather && (seiraiOwnsWeather || dragonspineOwnsWeather)) {
				this.oceanidFallbackWeatherByUid.remove(uid);
				return;
			}

			if (allowDefaultReset && hadOceanidWeather) {
				player.setWeather(OCEANID_WEATHER_DEFAULT, ClimateType.CLIMATE_SUNNY);
				this.oceanidFallbackWeatherByUid.remove(uid);
			}

			return;
		}

		if (!hadOceanidWeather || previousWeather != desiredWeather) {
			player.setWeather(desiredWeather, ClimateType.CLIMATE_SUNNY);
			this.oceanidFallbackWeatherByUid.put(uid, desiredWeather);
		}
	}

	private boolean hasActiveOceanidFallbackEncounter() {
		EntityMonster body = this.getOceanidFallbackBody();
		return body != null && body.isAlive();
	}

	private void checkOceanidFallbackEncounter() {
		if (this.getId() != OCEANID_SCENE_ID) {
			return;
		}

		boolean playerNearArena =
				this.getPlayers().stream()
						.anyMatch(
								player ->
										player != null
												&& player.getPosition() != null
												&& this.isNear2d(
														player.getPosition(),
														OCEANID_ARENA_POS,
														OCEANID_ENCOUNTER_RADIUS));

		boolean playerStillInResetArea =
				this.getPlayers().stream()
						.anyMatch(
								player ->
										player != null
												&& player.getPosition() != null
												&& this.isNear2d(
														player.getPosition(),
														OCEANID_ARENA_POS,
														OCEANID_ENCOUNTER_RESET_RADIUS));

		if (!playerStillInResetArea) {
			if (this.hasOceanidRuntimeState()) {
				this.cleanupOceanidFallbackEncounter(true);

				var group = this.getScriptManager().getGroupById(OCEANID_GROUP_ID);
				if (group != null) {
					this.resetOceanidFallbackPlatforms(group);
				}

				this.resetOceanidFallbackWeather();
				this.oceanidFallbackLastStartMs = System.currentTimeMillis();

				Grasscutter.getLogger()
						.info("[OceanidDirectFallback] Reset encounter because all players left the arena.");
			}

			return;
		}

		if (this.hasOceanidRewardBlossom()
				|| this.oceanidFallbackWaitingForLeave
				|| this.oceanidFallbackDefeatedUntilLeave) {
			this.oceanidFallbackWaitingForLeave = true;
			this.oceanidFallbackDefeatedUntilLeave = true;
			this.cleanupOceanidPostDefeatOrphans();
			this.maintainOceanidFallbackPlatforms();
			this.resetOceanidFallbackWeather();
			return;
		}

		if (this.oceanidFallbackEncounterActive) {
			EntityMonster body = this.getOceanidFallbackBody();

			if (body == null || !body.isAlive()) {
				Grasscutter.getLogger()
						.warn("[OceanidDirectFallback] Active encounter lost its boss body; cleaning up and delaying restart.");

				this.cleanupOceanidFallbackEncounter(true);
				this.oceanidFallbackLastStartMs = System.currentTimeMillis();
				return;
			}

			this.cleanupDuplicateOceanidBodies();
			this.maintainOceanidFallbackPlatforms();
			return;
		}

		if (!playerNearArena) {
			return;
		}

		long now = System.currentTimeMillis();
		if (now - this.oceanidFallbackLastStartMs < 5000L) {
			return;
		}

		this.startOceanidFallbackEncounter();
	}

	private synchronized void startOceanidFallbackEncounter() {
		if (this.getId() != OCEANID_SCENE_ID) {
			return;
		}

		if (this.oceanidFallbackEncounterActive
				|| this.oceanidFallbackWaitingForLeave
				|| this.oceanidFallbackDefeatedUntilLeave
				|| this.hasOceanidRewardBlossom()) {
			return;
		}

		long now = System.currentTimeMillis();
		this.oceanidFallbackLastStartMs = now;

		var group = this.getScriptManager().getGroupById(OCEANID_GROUP_ID);
		if (group == null) {
			Grasscutter.getLogger()
					.warn("[OceanidDirectFallback] Could not load Oceanid group {}", OCEANID_GROUP_ID);
			return;
		}

		// Important: cleanup FIRST, while the encounter is still inactive.
		// cleanupOceanidFallbackEncounter(...) resets oceanidFallbackEncounterActive, so setting the active flag before cleanup causes a 5-second respawn loop.
		this.cleanupOceanidRuntimeEntitiesBeforeStart();
		this.resetOceanidFallbackPlatforms(group);

		this.oceanidFallbackBossEntityId = 0;
		this.oceanidFallbackWaveIndex = 0;
		this.oceanidFallbackFinishing = false;
		this.oceanidFallbackWaitingForLeave = false;
		this.oceanidFallbackDefeatedUntilLeave = false;
		this.oceanidFallbackVirtualHp.clear();
		this.oceanidFallbackVirtualMaxHp.clear();

		EntityMonster body = this.spawnOceanidFallbackBody(group);
		if (body == null) {
			this.oceanidFallbackEncounterActive = false;
			Grasscutter.getLogger().warn("[OceanidDirectFallback] Failed to spawn direct-combat Oceanid");
			return;
		}

		this.oceanidFallbackBossEntityId = body.getId();
		this.oceanidFallbackEncounterActive = true;
		this.maintainOceanidFallbackPlatforms();

		this.getPlayers().forEach(player -> this.applyOceanidFallbackWeather(player, false));

		Grasscutter.getLogger()
				.info(
						"[OceanidDirectFallback] Started direct-combat Oceanid encounter with monsterId={}, entityId={}",
						OCEANID_DIRECT_MONSTER_ID,
						body.getId());
	}

	private EntityMonster spawnOceanidFallbackBody(SceneGroup group) {
		if (group == null) {
			return null;
		}

		var data = GameData.getMonsterDataMap().get(OCEANID_DIRECT_MONSTER_ID);
		if (data == null) {
			Grasscutter.getLogger()
					.warn(
							"[OceanidDirectFallback] Missing MonsterData for direct Oceanid monsterId={}",
							OCEANID_DIRECT_MONSTER_ID);
			return null;
		}

		int level = 36;
		if (group.monsters != null && group.monsters.get(OCEANID_BOSS_CONFIG_ID) != null) {
			var metaMonster = group.monsters.get(OCEANID_BOSS_CONFIG_ID);
			level = this.getLevelForMonster(OCEANID_BOSS_CONFIG_ID, metaMonster.level);
		}

		EntityMonster body =
				new EntityMonster(
						this,
						data,
						OCEANID_DIRECT_BOSS_POS.clone(),
						OCEANID_DIRECT_BOSS_ROT.clone(),
						level);

		body.setGroupId(OCEANID_GROUP_ID);
		body.setBlockId(group.block_id);
		body.setConfigId(OCEANID_BOSS_CONFIG_ID);

		/*
		 * Do not attach the retail 20050101 SceneMonster metadata to this direct-combat
		 * body. The fallback should use normal monster combat/death and spawn the reward
		 * blossom manually when the entity dies.
		 */
		body.setMetaMonster(null);

		this.addEntity(body);
		return body;
	}

	private void handleOceanidFallbackMonsterDeath(EntityMonster monster, int attackerId) {
		if (monster == null) {
			return;
		}

		if (!this.isOceanidFallbackBody(monster)) {
			if (this.isOceanidFallbackMimic(monster)) {
				this.removeOceanidFallbackMimics();
			}
			return;
		}

		if (!this.oceanidFallbackEncounterActive || this.oceanidFallbackFinishing) {
			return;
		}

		this.finishOceanidFallbackEncounter(attackerId);
	}

	private boolean handleOceanidFallbackBodyDamage(EntityMonster body, float amount, int attackerId) {
		/*
		 * Direct-combat Oceanid should be damaged normally by Scene.handleAttack().
		 * Returning false here keeps this method harmless if an old call site remains.
		 */
		return false;
	}

	private boolean shouldBlockOceanidFallbackBodyKill(EntityMonster body) {
		/*
		 * Direct-combat Oceanid should die through the normal Scene.killEntity path.
		 * Returning false keeps this method harmless if an old call site remains.
		 */
		return false;
	}

	private void finishOceanidFallbackEncounter(int attackerId) {
		if (this.oceanidFallbackFinishing) {
			return;
		}

		this.oceanidFallbackFinishing = true;

		EntityMonster body = this.getOceanidFallbackBody();
		if (body != null && body.isAlive()) {
			this.removeEntity(body, VisionType.VisionType_VISION_DIE);
		}

		this.oceanidFallbackBossEntityId = 0;
		this.oceanidFallbackWaveIndex = 0;
		this.removeOceanidFallbackMimics();
		this.cleanupOceanidControlEntities();

		this.oceanidFallbackEncounterActive = false;
		this.oceanidFallbackWaitingForLeave = true;
		this.oceanidFallbackDefeatedUntilLeave = true;

		this.spawnOceanidFallbackRewardBlossom();
		this.cleanupOceanidPostDefeatOrphans();
		this.resetOceanidFallbackWeather();

		this.oceanidFallbackFinishing = false;

		Grasscutter.getLogger().info("[OceanidDirectFallback] Finished direct-combat Oceanid encounter");
	}

	private void spawnOceanidFallbackRewardBlossom() {
		if (this.getId() != OCEANID_SCENE_ID) {
			return;
		}

		this.oceanidFallbackWaitingForLeave = true;
		this.oceanidFallbackDefeatedUntilLeave = true;

		var group = this.getScriptManager().getGroupById(OCEANID_GROUP_ID);
		if (group == null || group.gadgets == null) {
			Grasscutter.getLogger().warn("[OceanidDirectFallback] Could not load Oceanid group for reward blossom");
			return;
		}

		this.resetOceanidFallbackPlatforms(group);
		this.removeOceanidEntityByConfigId(OCEANID_BLOSSOM_CONFIG_ID);

		var blossomMeta = group.gadgets.get(OCEANID_BLOSSOM_CONFIG_ID);
		if (blossomMeta == null) {
			Grasscutter.getLogger()
					.warn(
							"[OceanidDirectFallback] Missing Oceanid reward blossom config {}",
							OCEANID_BLOSSOM_CONFIG_ID);
			return;
		}

		EntityGadget blossom = this.getScriptManager().createGadget(group.id, group.block_id, blossomMeta);
		if (blossom == null) {
			Grasscutter.getLogger().warn("[OceanidDirectFallback] Failed to create Oceanid reward blossom");
			return;
		}

		blossom.getPosition().set(OCEANID_BLOSSOM_POS);
		blossom.getRotation().set(OCEANID_BLOSSOM_ROT);
		blossom.setState(0);

		this.addEntity(blossom);
		this.resetOceanidFallbackWeather();

		Grasscutter.getLogger()
				.info(
						"[OceanidDirectFallback] Spawned Oceanid reward blossom at {}",
						OCEANID_BLOSSOM_POS);
	}

	private void cleanupOceanidRuntimeEntitiesBeforeStart() {
		this.cleanupOceanidFallbackEncounter(true);
	}

	private void cleanupOceanidFallbackEncounter(boolean removeBlossom) {
		for (GameEntity entity : new ArrayList<>(this.getEntities().values())) {
			if (entity instanceof EntityMonster monster
					&& (this.isAnyOceanidBody(monster) || this.isOceanidFallbackMimic(monster))) {
				this.removeEntity(monster, VisionType.VisionType_VISION_REMOVE);
			}
		}

		this.oceanidFallbackEncounterActive = false;
		this.oceanidFallbackFinishing = false;
		this.oceanidFallbackBossEntityId = 0;
		this.oceanidFallbackWaveIndex = 0;

		this.cleanupOceanidControlEntities();

		this.oceanidFallbackVirtualHp.clear();
		this.oceanidFallbackVirtualMaxHp.clear();

		if (removeBlossom) {
			this.removeOceanidEntityByConfigId(OCEANID_BLOSSOM_CONFIG_ID);
			this.oceanidFallbackWaitingForLeave = false;
			this.oceanidFallbackDefeatedUntilLeave = false;
		}

		this.resetOceanidFallbackWeather();
	}

	private void cleanupOceanidPostDefeatOrphans() {
		for (GameEntity entity : new ArrayList<>(this.getEntities().values())) {
			if (entity instanceof EntityMonster monster
					&& (this.isAnyOceanidBody(monster) || this.isOceanidFallbackMimic(monster))) {
				this.removeEntity(monster, VisionType.VisionType_VISION_REMOVE);
			}
		}

		this.cleanupOceanidControlEntities();
	}

	private void cleanupOceanidControlEntities() {
		for (int configId : OCEANID_CONTROL_GADGET_CONFIGS) {
			this.removeOceanidEntityByConfigId(configId);
		}
	}

	private void cleanupDuplicateOceanidBodies() {
		EntityMonster activeBody = this.getOceanidFallbackBody();

		for (GameEntity entity : new ArrayList<>(this.getEntities().values())) {
			if (!(entity instanceof EntityMonster monster)) {
				continue;
			}

			if (!this.isAnyOceanidBody(monster)) {
				continue;
			}

			if (activeBody != null && monster.getId() == activeBody.getId()) {
				continue;
			}

			this.removeEntity(monster, VisionType.VisionType_VISION_REMOVE);
			Grasscutter.getLogger()
					.warn(
							"[OceanidDirectFallback] Removed duplicate/orphan Oceanid body entityId={}, monsterId={}, configId={}",
							monster.getId(),
							monster.getMonsterData().getId(),
							monster.getConfigId());
		}
	}

	private void removeOceanidFallbackMimics() {
		List<GameEntity> mimics =
				this.getEntities().values().stream()
						.filter(entity -> entity instanceof EntityMonster)
						.map(entity -> (EntityMonster) entity)
						.filter(this::isOceanidFallbackMimic)
						.map(entity -> (GameEntity) entity)
						.toList();

		if (!mimics.isEmpty()) {
			this.removeEntities(mimics, VisionType.VisionType_VISION_REMOVE);
		}
	}

	private void resetOceanidFallbackPlatforms(SceneGroup group) {
		if (group == null || group.gadgets == null) {
			return;
		}

		for (int configId : OCEANID_PLATFORM_CONFIGS) {
			GameEntity existing = this.getEntityByConfigId(configId, OCEANID_GROUP_ID);

			if (existing instanceof EntityGadget gadget) {
				this.hardenOceanidPlatform(gadget);
				continue;
			}

			var meta = group.gadgets.get(configId);
			if (meta == null) {
				continue;
			}

			EntityGadget platform = this.getScriptManager().createGadget(group.id, group.block_id, meta);
			if (platform != null) {
				platform.setState(0);
				this.hardenOceanidPlatform(platform);
				this.addEntity(platform);
				platform.updateState(0);
			}
		}
	}
	
	private boolean isOceanidPlatformEntity(GameEntity entity) {
		return this.getId() == OCEANID_SCENE_ID
				&& entity instanceof EntityGadget
				&& entity.getGroupId() == OCEANID_GROUP_ID
				&& OCEANID_PLATFORM_CONFIGS.contains(entity.getConfigId());
	}

	private void hardenOceanidPlatform(EntityGadget platform) {
		if (platform == null || !this.isOceanidPlatformEntity(platform)) {
			return;
		}

		// Keep Oceanid arena platforms from being destroyed by the direct-combat Oceanid.
		platform.setFightProperty(FightProperty.FIGHT_PROP_BASE_HP, Float.POSITIVE_INFINITY);
		platform.setFightProperty(FightProperty.FIGHT_PROP_MAX_HP, Float.POSITIVE_INFINITY);
		platform.setFightProperty(FightProperty.FIGHT_PROP_CUR_HP, Float.POSITIVE_INFINITY);

		// State 0 is the normal raised/usable platform state.
		if (platform.getState() != 0) {
			platform.updateState(0);
		}
	}

	private void maintainOceanidFallbackPlatforms() {
		if (this.getId() != OCEANID_SCENE_ID) {
			return;
		}

		// Only force platform stability while the custom direct Oceanid encounter/reward is active.
		if (!this.oceanidFallbackEncounterActive && !this.hasOceanidRewardBlossom()) {
			return;
		}

		var group = this.getScriptManager().getGroupById(OCEANID_GROUP_ID);
		if (group == null || group.gadgets == null) {
			return;
		}

		for (int configId : OCEANID_PLATFORM_CONFIGS) {
			GameEntity existing = this.getEntityByConfigId(configId, OCEANID_GROUP_ID);

			if (existing instanceof EntityGadget platform) {
				this.hardenOceanidPlatform(platform);
				continue;
			}

			// If a platform was somehow removed anyway, recreate it.
			var metaGadget = group.gadgets.get(configId);
			if (metaGadget == null) {
				continue;
			}

			EntityGadget platform = this.getScriptManager().createGadget(group.id, group.block_id, metaGadget);
			if (platform == null) {
				continue;
			}

			this.hardenOceanidPlatform(platform);
			this.addEntity(platform);
			platform.updateState(0);
		}
	}

	private void removeOceanidEntityByConfigId(int configId) {
		List<GameEntity> toRemove =
				this.getEntities().values().stream()
						.filter(entity -> entity.getGroupId() == OCEANID_GROUP_ID)
						.filter(entity -> entity.getConfigId() == configId)
						.toList();

		if (!toRemove.isEmpty()) {
			this.removeEntities(toRemove, VisionType.VisionType_VISION_REMOVE);
		}
	}

	private boolean isAnyOceanidBody(EntityMonster monster) {
		if (monster == null || monster.getMonsterData() == null) {
			return false;
		}

		int monsterId = monster.getMonsterData().getId();
		return monster.getGroupId() == OCEANID_GROUP_ID
				&& monster.getConfigId() == OCEANID_BOSS_CONFIG_ID
				&& (monsterId == OCEANID_LEGACY_MONSTER_ID
						|| monsterId == OCEANID_DIRECT_MONSTER_ID
						|| monsterId == OCEANID_DIRECT_MUTE_MONSTER_ID);
	}

	private boolean isOceanidFallbackBody(EntityMonster monster) {
		return this.isAnyOceanidBody(monster)
				&& monster.getMonsterData() != null
				&& monster.getMonsterData().getId() == OCEANID_DIRECT_MONSTER_ID
				&& monster.getId() == this.oceanidFallbackBossEntityId;
	}

	private EntityMonster getOceanidFallbackBody() {
		GameEntity entity = this.getEntities().get(this.oceanidFallbackBossEntityId);
		if (entity instanceof EntityMonster monster && this.isOceanidFallbackBody(monster)) {
			return monster;
		}

		return this.getEntities().values().stream()
				.filter(e -> e instanceof EntityMonster)
				.map(e -> (EntityMonster) e)
				.filter(this::isOceanidFallbackBody)
				.findFirst()
				.orElse(null);
	}

	private boolean isOceanidFallbackMimic(EntityMonster monster) {
		return monster != null
				&& monster.getGroupId() == OCEANID_GROUP_ID
				&& OCEANID_FALLBACK_MIMIC_CONFIGS.contains(monster.getConfigId());
	}

	private boolean hasOceanidRewardBlossom() {
		return this.getEntityByConfigId(OCEANID_BLOSSOM_CONFIG_ID, OCEANID_GROUP_ID) instanceof EntityGadget;
	}

	private boolean hasOceanidRuntimeState() {
		return this.oceanidFallbackEncounterActive
				|| this.oceanidFallbackWaitingForLeave
				|| this.oceanidFallbackDefeatedUntilLeave
				|| this.hasOceanidRewardBlossom()
				|| this.getEntities().values().stream()
						.anyMatch(
								entity ->
										entity instanceof EntityMonster monster
												&& (this.isAnyOceanidBody(monster)
														|| this.isOceanidFallbackMimic(monster)));
	}


private void resetOceanidFallbackWeather() {
		if (this.getId() != OCEANID_SCENE_ID) {
			return;
		}

		for (Player player : this.getPlayers()) {
			if (player != null) {
				player.setWeather(OCEANID_WEATHER_DEFAULT, ClimateType.CLIMATE_SUNNY);
				this.oceanidFallbackWeatherByUid.remove(player.getUid());
			}
		}
	}
}
