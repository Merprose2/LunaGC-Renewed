package emu.grasscutter.game.stygian;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.leyline.*;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.LifeState;
import emu.grasscutter.game.props.WatcherTriggerType;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.game.world.Scene;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.net.proto.DungeonReviseLevelNotifyOuterClass.DungeonReviseLevelNotify;
import emu.grasscutter.net.proto.EntityFightPropUpdateNotifyOuterClass.EntityFightPropUpdateNotify;
import emu.grasscutter.net.proto.GalleryStartNotifyOuterClass.GalleryStartNotify;
import emu.grasscutter.net.proto.GalleryStopNotifyOuterClass.GalleryStopNotify;
import emu.grasscutter.net.proto.GalleryStopReasonOuterClass.GalleryStopReason;
import emu.grasscutter.net.proto.GalleryStageTypeOuterClass.GalleryStageType;
import emu.grasscutter.net.proto.LifeStateChangeNotifyOuterClass.LifeStateChangeNotify;
import emu.grasscutter.net.proto.SceneGalleryInfoOuterClass.SceneGalleryInfo;
import emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType;
import emu.grasscutter.server.packet.send.*;
import java.util.*;
import lombok.Getter;

/**
 * Stygian Onslaught (幽境危战 / Ley Line Challenge, activity 5269) - 7.0.
 *
 * <p>Recreated from an official server packet capture. The mode runs a "custom gallery" battle in
 * the dedicated Ley Line arena scenes (30002/30003/30004, reached through Ley Line dungeons
 * 20000-20005):
 *
 * <ul>
 *   <li>DPDAJBFEKAC (24439): client picks a difficulty -> enter the dungeon, prepare phase
 *       (prepare gallery: difficulty selection with the worktop blossom 73051002)
 *   <li>CAACHEGPEJD (24451): client confirms the team for a round -> settle the current dungeon
 *       instance and re-enter a fresh battle instance (battle gallery PRESTART)
 *   <li>GNLMAGJGKPF (6226): client is ready -> battle starts (blossom 70310054 is consumed, waves
 *       of special monsters spawn)
 *   <li>IBKMEGAJKAI (20795): client gives up -> battle gallery stops (CLIENT_INTERRUPT), back to
 *       the prepare gallery in place
 *   <li>Each difficulty has 3 rounds; completing all waves of a round finishes it.
 * </ul>
 *
 * <p>Challenge state is currently kept in memory (not persisted across relogs).
 */
public final class StygianOnslaughtManager extends BasePlayerManager {

    // =====================================================================
    // Constants observed on the official server (7.0)
    // =====================================================================

    public static final int ACTIVITY_ID = 5269;
    public static final int ACTIVITY_TYPE = 5701;

    /** EBMOODIKLDM value carried by the UI data query (NPBENBBMDBH). */
    private static final int UI_DATA_TYPE = 3;
    /** NNPHIFOPPKO value of the official data response (JJKBEPPIFIE). */
    private static final int OFFICIAL_CHALLENGE_COUNT = 14;
    /** KGPGPEFOLBP list of the official OBFLIEAJIMP response. */
    private static final int[] OFFICIAL_LEVEL_LIST = {14, 10, 9, 8, 7, 6, 5, 4, 3, 2, 1};
    /** first schedule ids listed in BFIDMPPFHHC on the official ActivityInfoNotify. */
    private static final int[] EARLIEST_SCHEDULE_IDS = {5269001, 5269002};
    /** DIEFHMGFNIJ value carried by the official ActivityInfo (uint32, field 1235). */
    private static final int OFFICIAL_INFO_FLAG = 6;
    /** OPCOMCJHGAJ values of the official ActivityInfo (repeated uint64, field 1719). */
    private static final long[] OFFICIAL_INFO_PARAMS = {2, 3};
    /** first_day_start_time = begin_time - 6h on the official ActivityInfo. */
    private static final int FIRST_DAY_START_OFFSET = 21600;
    /**
     * The excels only schedule a few weeks per entry (5269011 ends 2026-09-30 - the "12 days left"
     * the client showed), so every reported end time is pushed 5 years further out to keep the mode
     * open instead of letting the event expire.
     */
    private static final int SCHEDULE_DURATION_SECONDS = 5 * 365 * 86400;

    private static final int WATCHER_ID_TOTAL = 1526911;
    private static final int WATCHER_TOTAL_PROGRESS = 1200;

    // Arena (scene 30003, group 230003001)
    private static final int ARENA_GROUP_ID = 230003001;
    private static final int GADGET_PEDESTAL = 70330994; // config 1013 - center pedestal
    private static final int GADGET_PREPARE_BLOSSOM = 73051002; // config 1001 - worktop blossom
    private static final int GADGET_BATTLE_BLOSSOM = 70310054; // config 1007 - consumed on start
    private static final int GADGET_SKY = 70310042; // config 1014 - floating sky gadget
    private static final int CONFIG_PEDESTAL = 1013;
    private static final int CONFIG_PREPARE_BLOSSOM = 1001;
    private static final int CONFIG_BATTLE_BLOSSOM = 1007;
    private static final int CONFIG_SKY = 1014;

    private static final Position POS_PEDESTAL = new Position(499.994f, 249.873f, 500.002f);
    private static final Position POS_PREPARE_BLOSSOM =
            new Position(499.994f, 250.276f, 500.002f);
    private static final Position POS_BATTLE_BLOSSOM = new Position(512.11f, 249.784f, 500.204f);
    private static final Position POS_SKY = new Position(500.186f, 294.873f, 500.061f);
    private static final Position ROT_ARENA = new Position(0f, 267.019f, 0f);

    // Battle waves. One special monster per wave; extend when lineups are mapped.
    private static final int[] WAVE_MONSTERS = {26310505};
    private static final int MONSTER_LEVEL = 40;
    private static final int WAVES_PER_ROUND = 2;
    private static final Position POS_MONSTER = new Position(500.044f, 249.873f, 499.898f);
    private static final Position ROT_MONSTER = new Position(0f, 92.217f, 0f);
    private static final float[] WAVE_SPAWN_OFFSETS = {0f, 3f, -3f, 6f, -6f};

    // Custom gallery score boards / progress ids (CustomGalleryProgressExcelConfigData group 50702)
    private static final int PROGRESS_PREPARE_MAIN = 50702001;
    private static final int PROGRESS_PREPARE_TOTAL = 50702009;
    private static final int[] PROGRESS_PREPARE_NO_RECORD = {50702010, 50702011, 50702012};
    private static final int PROGRESS_BATTLE_MAIN = 50702007;
    private static final int PROGRESS_BATTLE_COUNTDOWN = 1002;
    private static final int PROGRESS_BATTLE_NOSCORE = 50702013;
    private static final int TARGET_BANNER_START = 50701001;
    private static final int BATTLE_TIME_LIMIT = 600;
    /** Lozenge ids hidden in the GlobalData UI hint (50702002-50702016). */
    private static final String HIDE_LOZENGE_IDS =
            "50702002,50702003,50702004,50702005,50702008,50702009,50702010,50702011,50702012,"
                    + "50702013,50702014,50702015,50702016";

    private static final int REVISE_LEVEL_BASE = 39; // LPLCCACPIAI, ABEGKKOCBBE = base + 1

    // =====================================================================
    // State
    // =====================================================================

    private enum Phase {
        IDLE,
        PREPARE,
        BATTLE_READY,
        BATTLE
    }

    @Getter private Phase phase = Phase.IDLE;
    @Getter private int difficulty = 1;
    @Getter private int currentRound = 1;
    private int dungeonId = 0;
    private int prepareGalleryId = 83303;
    private int battleGalleryId = 83304;
    private int waveIndex = 0;
    private long battleStartMs = 0;
    private final List<Integer> activeWaveEntityIds = new ArrayList<>();
    /** rounds completed per difficulty (watcher progress / unlock data). */
    private final Map<Integer, Integer> completedRounds = new HashMap<>();
    /** confirmed team per round (round -> avatar ids), for the ActivityInfo round records. */
    private final Map<Integer, List<Integer>> roundTeams = new HashMap<>();

    public StygianOnslaughtManager(Player player) {
        super(player);
    }

    // =====================================================================
    // Data access
    // =====================================================================

    /** The Ley Line Challenge schedule active right now (falls back to the first one). */
    public LeyLineChallengeData getScheduleData() {
        var map = GameData.getLeyLineChallengeDataMap();
        if (map == null || map.isEmpty()) {
            return null;
        }
        int now = (int) (System.currentTimeMillis() / 1000L);
        LeyLineChallengeData current = null;
        for (var data : map.values()) {
            if (now >= data.getStartTime() && now < data.getEndTime()) {
                // Several schedules can overlap (5269011 and 5269012 both cover e.g. early
                // September 2026); the official capture reports the long-running one, so prefer
                // the matching schedule with the latest end time to stay deterministic.
                if (current == null || data.getEndTime() > current.getEndTime()) {
                    current = data;
                }
            }
        }
        return current != null
                ? current
                : map.get(5269011); // schedule from the capture; stable fallback
    }

    private LeyLineChallengeConstData getConstData() {
        var map = GameData.getLeyLineChallengeConstDataMap();
        return map == null || map.isEmpty() ? null : map.values().iterator().next();
    }

    /**
     * Difficulty (1-6) -> Ley Line dungeon (20001..20005, 20000). The capture proves difficulty 1
     * uses dungeon 20001 (scene 30003).
     */
    private int dungeonForDifficulty(int difficulty) {
        return 20000 + (difficulty % 6);
    }

    /** Gallery pair of a Ley Line dungeon via LeyLineDungeonExcelConfigData rows 1-3. */
    private LeyLineDungeonData galleryDataForDungeon(int dungeonId) {
        int row = ((dungeonId - 20000) % 3) + 1;
        return GameData.getLeyLineDungeonDataMap().get(row);
    }

    private int monsterLevel() {
        return REVISE_LEVEL_BASE + 1; // matches DungeonReviseLevelNotify 39 -> 40
    }

    // =====================================================================
    // Client requests
    // =====================================================================

    /**
     * ADMAHIPICMC (4599) - the client opens the Stygian Onslaught UI. Official reply: DBFJKIEBAJJ
     * (3796) carrying the challenge state (is_open = true). Without this the client shows the mode
     * as closed.
     */
    public void onOpenUi() {
        var schedule = getScheduleData();
        if (schedule == null) {
            player.sendPacket(
                    new BasePacket(PacketOpcodes.DBFJKIEBAJJ) {
                        {
                            setData(
                                    DBFJKIEBAJJOuterClass.DBFJKIEBAJJ.newBuilder()
                                            .setRetcode(-1)
                                            .build());
                        }
                    });
            return;
        }
        player.sendPacket(
                new BasePacket(PacketOpcodes.DBFJKIEBAJJ) {
                    {
                        setData(
                                DBFJKIEBAJJOuterClass.DBFJKIEBAJJ.newBuilder()
                                        .setALJFGLAPKKM(buildChallengeData(UI_DATA_TYPE))
                                        .build());
                    }
                });
    }

    /**
     * _WeekActiveDetailUpdateNotify (3935) - pushed once during the login batch. The official
     * server delivers the challenge state (is_open = true) through this packet before the player
     * ever opens the UI; the client's event panel consults it to render the mode as open.
     */
    public void sendWeekActiveDetailUpdate() {
        var schedule = getScheduleData();
        if (schedule == null) {
            return;
        }
        var detail =
                KHEHKOJFNKNOuterClass.KHEHKOJFNKN.newBuilder()
                        .setFIDIFABIGBH(schedule.getScheduleId())
                        .setNNPHIFOPPKO(OFFICIAL_CHALLENGE_COUNT)
                        .setEBMOODIKLDM(UI_DATA_TYPE)
                        .setKOMPLLJLEOL(true) // is open
                        .build();
        player.sendPacket(
                new BasePacket(PacketOpcodes._WeekActiveDetailUpdateNotify) {
                    {
                        setData(
                                _WeekActiveDetailUpdateNotifyOuterClass._WeekActiveDetailUpdateNotify
                                        .newBuilder()
                                        .setDMHLCICLIFJ(detail)
                                        .build());
                    }
                });
    }

    /** The shared JJKBEPPIFIE challenge state payload (schedule + open flag). */
    private JJKBEPPIFIEOuterClass.JJKBEPPIFIE.Builder buildChallengeData(int type) {
        var schedule = getScheduleData();
        return JJKBEPPIFIEOuterClass.JJKBEPPIFIE.newBuilder()
                .setEBMOODIKLDM(type)
                .setKOMPLLJLEOL(true) // is open
                .setFIDIFABIGBH(schedule.getScheduleId())
                .setOFCFHPFDGLD(
                        PPOBOBDKPHIOuterClass.PPOBOBDKPHI.newBuilder()
                                .setScheduleId(schedule.getScheduleId()))
                .setNNPHIFOPPKO(OFFICIAL_CHALLENGE_COUNT);
    }

    /**
     * NPBENBBMDBH (2211) - the Stygian UI data poll. Official reply: NJKJLDFNPMC (state) +
     * DJBNNEIMEKG (retcode) + OBFLIEAJIMP (level list).
     */
    public void onDataQuery(NPBENBBMDBHOuterClass.NPBENBBMDBH req) {
        var schedule = getScheduleData();
        if (schedule == null) {
            player.sendPacket(
                    new BasePacket(PacketOpcodes.DJBNNEIMEKG) {
                        {
                            setData(
                                    DJBNNEIMEKGOuterClass.DJBNNEIMEKG.newBuilder()
                                            .setRetcode(-1)
                                            .build());
                        }
                    });
            return;
        }

        // NJKJLDFNPMC (7053)
        var data =
                buildChallengeData(
                        req.getEBMOODIKLDM() > 0 ? req.getEBMOODIKLDM() : UI_DATA_TYPE);
        player.sendPacket(
                new BasePacket(PacketOpcodes.NJKJLDFNPMC) {
                    {
                        setData(
                                NJKJLDFNPMCOuterClass.NJKJLDFNPMC.newBuilder()
                                        .setALJFGLAPKKM(data)
                                        .build());
                    }
                });

        // DJBNNEIMEKG (28579) - retcode 0
        player.sendPacket(
                new BasePacket(PacketOpcodes.DJBNNEIMEKG) {
                    {
                        setData(DJBNNEIMEKGOuterClass.DJBNNEIMEKG.getDefaultInstance());
                    }
                });

        // OBFLIEAJIMP (6377) - level list
        var levelRsp = OBFLIEAJIMPOuterClass.OBFLIEAJIMP.newBuilder();
        for (int level : OFFICIAL_LEVEL_LIST) {
            levelRsp.addKGPGPEFOLBP(level);
        }
        player.sendPacket(
                new BasePacket(PacketOpcodes.OBFLIEAJIMP) {
                    {
                        setData(levelRsp.build());
                    }
                });
    }

    /** DPDAJBFEKAC (24439) - the client starts the challenge with the chosen difficulty. */
    public void onStartChallenge(int difficulty) {
        if (difficulty < 1 || difficulty > 6) {
            difficulty = 1;
        }
        var schedule = getScheduleData();
        if (schedule == null) {
            player.sendPacket(
                    new BasePacket(PacketOpcodes.OGNHPDHDIMA) {
                        {
                            setData(
                                    OGNHPDHDIMAOuterClass.OGNHPDHDIMA.newBuilder()
                                            .setRetcode(-1)
                                            .build());
                        }
                    });
            return;
        }

        this.difficulty = difficulty;
        this.currentRound = 1;
        this.roundTeams.clear();
        this.phase = Phase.PREPARE;

        this.dungeonId = dungeonForDifficulty(difficulty);
        var row = galleryDataForDungeon(this.dungeonId);
        if (row != null) {
            this.prepareGalleryId = row.getPrepareGalleryId();
            this.battleGalleryId = row.getBattleGalleryId();
        }

        Grasscutter.getLogger()
                .debug(
                        "[Stygian] {} starts difficulty {} -> dungeon {} (galleries {}/{})",
                        player.getUid(),
                        difficulty,
                        this.dungeonId,
                        this.prepareGalleryId,
                        this.battleGalleryId);

        this.enterDungeonInstance(true);
    }

    /**
     * CAACHEGPEJD (24451) - the client confirms the team for a round. Official flow: settle the
     * current dungeon instance and re-enter a fresh battle instance.
     */
    public void onConfirmRound(int round, List<Integer> avatarIds) {
        if (this.phase != Phase.PREPARE && this.phase != Phase.BATTLE_READY) {
            return;
        }
        if (round < 1 || round > 3) {
            round = this.currentRound;
        }
        this.currentRound = round;
        if (avatarIds != null && !avatarIds.isEmpty()) {
            this.roundTeams.put(round, new ArrayList<>(avatarIds));
            this.applyTemporaryTeam(avatarIds);
        }

        var scene = player.getScene();
        int useTime = 0; // time spent in the previous (prepare) instance; client tolerates 0

        // Move to a fresh battle instance of the same dungeon.
        this.phase = Phase.BATTLE_READY;
        this.waveIndex = 0;
        this.activeWaveEntityIds.clear();

        // Officially the old instance is settled while the new one loads.
        var settle =
                DungeonSettleNotifyOuterClass.DungeonSettleNotify.newBuilder()
                        .setResult(3)
                        .setUseTime(useTime)
                        .setCloseTime((int) ((System.currentTimeMillis() + 30_000) / 1000L))
                        .addPlayerUidList(player.getUid())
                        .setCreatePlayerUid(player.getUid());
        player.sendPacket(
                new BasePacket(PacketOpcodes.DungeonSettleNotify) {
                    {
                        setData(settle.build());
                    }
                });

        if (scene != null && scene.getId() == 3) {
            this.enterDungeonInstance(false);
        } else {
            // already inside an arena scene: force a fresh instance like restartDungeon does
            var dungeonData = GameData.getDungeonDataMap().get(this.dungeonId);
            if (dungeonData != null) {
                scene.setDontDestroyWhenEmpty(false);
                scene.getPlayers().forEach(scene::removePlayer);
                player.getWorld().transferPlayerToScene(player, dungeonData.getSceneId(), dungeonData);
                this.sendEnterSequence(false);
                this.spawnArenaGadgets(false);
                this.sendBattleGalleryPreStart();
            }
        }
    }

    /** GNLMAGJGKPF (6226) - the client signals it is ready; the battle begins. */
    public void onBattleReady() {
        if (this.phase != Phase.BATTLE_READY) {
            return;
        }
        this.startBattle();
    }

    /** IBKMEGAJKAI (20795) - the client gives up the current battle. */
    public void onGiveUp() {
        if (this.phase != Phase.BATTLE && this.phase != Phase.BATTLE_READY) {
            return;
        }

        var scene = player.getScene();
        if (scene != null) {
            // monsters leave with VISION_REFRESH (official behaviour)
            for (int entityId : this.activeWaveEntityIds) {
                var entity = scene.getEntityById(entityId);
                if (entity != null) {
                    scene.removeEntity(entity, VisionType.VisionType_VISION_REFRESH);
                }
            }
        }
        this.activeWaveEntityIds.clear();

        // GalleryStopNotify - officially GALLERY_STOP_CLIENT_INTERRUPT
        player.sendPacket(
                new BasePacket(PacketOpcodes.GalleryStopNotify) {
                    {
                        setData(
                                GalleryStopNotify.newBuilder()
                                        .setGalleryId(battleGalleryId)
                                        .setStopReason(
                                                GalleryStopReason
                                                        .GalleryStopReason_GALLERY_STOP_CLIENT_INTERRUPT)
                                        .build());
                    }
                });

        player.sendPacket(this.buildActivityInfoNotify());
        player.sendPacket(this.buildStygianStateNotify());

        this.phase = Phase.PREPARE;
        this.waveIndex = 0;
        this.sendPrepareGalleryRestart();
        this.spawnPrepareBlossom();
        this.sendLevelList();
    }

    // =====================================================================
    // Battle progression
    // =====================================================================

    /** Called from EntityMonster.onDeath for Stygian wave monsters. */
    public void onMonsterDeath(EntityMonster monster) {
        if (this.phase != Phase.BATTLE) {
            return;
        }
        this.activeWaveEntityIds.remove(Integer.valueOf(monster.getId()));
        if (!this.activeWaveEntityIds.isEmpty()) {
            return; // wave still alive
        }
        this.waveIndex++;
        if (this.waveIndex < WAVES_PER_ROUND) {
            this.spawnWave();
        } else {
            this.finishRound();
        }
    }

    /** All waves of the round are cleared. */
    private void finishRound() {
        int costTime = (int) ((System.currentTimeMillis() - this.battleStartMs) / 1000L);
        this.completedRounds.merge(this.difficulty, 1, Integer::sum);

        // activity watcher rewards (difficulty completion rows of activity 5269)
        player
                .getActivityManager()
                .triggerWatcher(
                        WatcherTriggerType.TRIGGER_LEY_LINE_CHALLENGE_FINISH_DIFFICULTY,
                        String.valueOf(this.difficulty));

        // stop the battle gallery (successful completion)
        player.sendPacket(
                new BasePacket(PacketOpcodes.GalleryStopNotify) {
                    {
                        setData(
                                GalleryStopNotify.newBuilder()
                                        .setGalleryId(battleGalleryId)
                                        .setStopReason(
                                                GalleryStopReason
                                                        .GalleryStopReason_GALLERY_STOP_FINISHED)
                                        .build());
                    }
                });

        player.sendPacket(this.buildStygianStateNotify());

        // settle the dungeon (success)
        var settle =
                DungeonSettleNotifyOuterClass.DungeonSettleNotify.newBuilder()
                        .setResult(1)
                        .setUseTime(costTime)
                        .setCloseTime((int) ((System.currentTimeMillis() + 30_000) / 1000L))
                        .addPlayerUidList(player.getUid())
                        .setCreatePlayerUid(player.getUid());
        player.sendPacket(
                new BasePacket(PacketOpcodes.DungeonSettleNotify) {
                    {
                        setData(settle.build());
                    }
                });

        player.sendPacket(this.buildActivityInfoNotify());

        if (this.currentRound < 3) {
            // next round: prepare phase in place (same as the give-up flow)
            this.phase = Phase.PREPARE;
            this.waveIndex = 0;
            this.currentRound++;
            this.sendPrepareGalleryRestart();
            this.spawnPrepareBlossom();
            this.sendLevelList();
        } else {
            // challenge complete: leave the arena
            this.phase = Phase.IDLE;
            player.getTeamManager().cleanTemporaryTeam();
            player.getServer().getDungeonSystem().exitDungeon(player);
        }
    }

    // =====================================================================
    // Scene / dungeon handling
    // =====================================================================

    /**
     * Enter a fresh instance of the Stygian dungeon. Prepare instances host the difficulty
     * selection (worktop blossom); battle instances host the fight.
     */
    private void enterDungeonInstance(boolean prepare) {
        var dungeonData = GameData.getDungeonDataMap().get(this.dungeonId);
        if (dungeonData == null) {
            Grasscutter.getLogger()
                    .error("[Stygian] Dungeon {} is missing from DungeonExcelConfigData!", this.dungeonId);
            return;
        }

        this.phase = prepare ? Phase.PREPARE : Phase.BATTLE_READY;
        this.waveIndex = 0;
        this.activeWaveEntityIds.clear();

        if (player.getWorld().transferPlayerToScene(player, dungeonData.getSceneId(), dungeonData)) {
            var scene = player.getScene();
            scene.setDungeonManager(
                    new emu.grasscutter.game.dungeons.DungeonManager(scene, dungeonData));
            emu.grasscutter.game.dungeons.fallback.MissingDomainFallbackManager.install(
                    scene, dungeonData);

            this.sendEnterSequence(prepare);
            this.spawnArenaGadgets(prepare);
            if (prepare) {
                this.sendPrepareGalleryStart(false);
                this.sendBlossomUiHint("25", "false");
                // MIKMBPNBIIE follows the gallery info on the official server
            } else {
                this.sendBattleGalleryPreStart();
            }
        }
    }

    /** Packets the official server sends right after the dungeon transfer. */
    private void sendEnterSequence(boolean reentered) {
        int sceneId = player.getSceneId();

        // HBLFCPIECPP (20438)
        player.sendPacket(
                new BasePacket(PacketOpcodes.HBLFCPIECPP) {
                    {
                        setData(
                                HBLFCPIECPPOuterClass.HBLFCPIECPP.newBuilder()
                                        .setSceneId(sceneId)
                                        .build());
                    }
                });

        // DungeonReviseLevelNotify (24693)
        player.sendPacket(
                new BasePacket(PacketOpcodes.DungeonReviseLevelNotify) {
                    {
                        setData(
                                DungeonReviseLevelNotify.newBuilder()
                                        .setLPLCCACPIAI(REVISE_LEVEL_BASE)
                                        .setDungeonId(dungeonId)
                                        .setABEGKKOCBBE(monsterLevel())
                                        .build());
                    }
                });

        // FJIIANKJOIH (22946)
        player.sendPacket(
                new BasePacket(PacketOpcodes.FJIIANKJOIH) {
                    {
                        setData(
                                FJIIANKJOIHOuterClass.FJIIANKJOIH.newBuilder()
                                        .setFIELKKMFABD(
                                                CKIBIMMCMHDOuterClass.CKIBIMMCMHD.getDefaultInstance())
                                        .build());
                    }
                });

        // DungeonWayPointNotify (empty on the official server)
        player.sendPacket(new BasePacket(PacketOpcodes.DungeonWayPointNotify));

        // ActivityInfoNotify with the full ley line detail
        player.sendPacket(this.buildActivityInfoNotify());
    }

    /** Spawn the arena gadgets (pedestal, blossom, sky gadget) of group 230003001. */
    private void spawnArenaGadgets(boolean prepare) {
        var scene = player.getScene();
        if (scene == null) {
            return;
        }

        this.addGadget(scene, GADGET_PEDESTAL, CONFIG_PEDESTAL, POS_PEDESTAL);
        this.addGadget(scene, GADGET_SKY, CONFIG_SKY, POS_SKY);
        if (prepare) {
            this.addGadget(
                    scene, GADGET_PREPARE_BLOSSOM, CONFIG_PREPARE_BLOSSOM, POS_PREPARE_BLOSSOM);
        } else {
            this.addGadget(
                    scene, GADGET_BATTLE_BLOSSOM, CONFIG_BATTLE_BLOSSOM, POS_BATTLE_BLOSSOM);
        }
    }

    private void addGadget(Scene scene, int gadgetId, int configId, Position pos) {
        var entity = new EntityGadget(scene, gadgetId, pos, ROT_ARENA.clone());
        entity.setGroupId(ARENA_GROUP_ID);
        entity.setConfigId(configId);
        entity.buildContent();
        scene.addEntity(entity);
    }

    /** Spawn the worktop blossom used by the prepare phase (in place, scene stays loaded). */
    private void spawnPrepareBlossom() {
        var scene = player.getScene();
        if (scene == null) {
            return;
        }
        var existing =
                scene.getEntities().values().stream()
                        .filter(e -> e instanceof EntityGadget)
                        .map(e -> (EntityGadget) e)
                        .filter(
                                g ->
                                        g.getGadgetId() == GADGET_PREPARE_BLOSSOM
                                                && g.getGroupId() == ARENA_GROUP_ID)
                        .findFirst();
        if (existing.isEmpty()) {
            this.addGadget(
                    scene, GADGET_PREPARE_BLOSSOM, CONFIG_PREPARE_BLOSSOM, POS_PREPARE_BLOSSOM);
        }
    }

    // =====================================================================
    // Gallery packets
    // =====================================================================

    /** Called from HandlerPostEnterSceneReq: the client finished loading the arena. */
    public void onPostEnterScene() {
        if (this.phase == Phase.PREPARE) {
            this.sendPrepareGalleryStart(false);
            this.sendBlossomUiHint("25", "false");
        } else if (this.phase == Phase.BATTLE_READY) {
            this.sendBattleGalleryPreStart();
        }
    }

    /** SceneGalleryInfoNotify: prepare gallery, GALLERY_START, full prepare boards. */
    private void sendPrepareGalleryStart(boolean withStartNotify) {
        if (withStartNotify) {
            // official: _GalleryInitNotify(83303) + GalleryStartNotify(83303)
            player.sendPacket(
                    new BasePacket(PacketOpcodes._GalleryInitNotify) {
                        {
                            setData(
                                    GANBOELABIEOuterClass.GANBOELABIE.newBuilder()
                                            .setGalleryId(prepareGalleryId)
                                            .build());
                        }
                    });
            player.sendPacket(
                    new BasePacket(PacketOpcodes.GalleryStartNotify) {
                        {
                            setData(this.galleryStart(prepareGalleryId));
                        }

                        private GalleryStartNotify galleryStart(int id) {
                            return GalleryStartNotify.newBuilder()
                                    .setGalleryId(id)
                                    .setStartTime(6)
                                    .setEndTime(6)
                                    .setOwnerUid(player.getUid())
                                    .setPlayerCount(1)
                                    .build();
                        }
                    });
        }
        player.sendPacket(
                new BasePacket(PacketOpcodes.SceneGalleryInfoNotify) {
                    {
                        setData(
                                NCKODPJHOPCOuterClass.NCKODPJHOPC.newBuilder()
                                        .setGalleryInfo(
                                                buildPrepareGalleryInfo(null, null, null))
                                        .build());
                    }
                });
    }

    /**
     * The official server pushes the prepare boards progressively after a restart; we send the
     * accumulated snapshots (basic -> +no_record1..3 -> +main -> +total).
     */
    private void sendPrepareGalleryRestart() {
        player.sendPacket(
                new BasePacket(PacketOpcodes._GalleryInitNotify) {
                    {
                        setData(
                                GANBOELABIEOuterClass.GANBOELABIE.newBuilder()
                                        .setGalleryId(prepareGalleryId)
                                        .build());
                    }
                });
        player.sendPacket(
                new BasePacket(PacketOpcodes.GalleryStartNotify) {
                    {
                        setData(
                                GalleryStartNotify.newBuilder()
                                        .setGalleryId(prepareGalleryId)
                                        .setStartTime(6)
                                        .setEndTime(6)
                                        .setOwnerUid(player.getUid())
                                        .setPlayerCount(1)
                                        .build());
                    }
                });

        // basic (no boards)
        this.pushGalleryInfo(
                this.buildPrepareGalleryInfo(null, null, null));
        // + no_record1..3
        List<_CustomGalleryProgressInfoOuterClass._CustomGalleryProgressInfo> progress =
                new ArrayList<>();
        List<_CustomGalleryScoreBoardInfoOuterClass._CustomGalleryScoreBoardInfo> boards =
                new ArrayList<>();
        for (int i = 0; i < PROGRESS_PREPARE_NO_RECORD.length; i++) {
            String name = "prepare_customgallery_no_record" + (i + 1);
            progress.add(this.prepareProgress(name, PROGRESS_PREPARE_NO_RECORD[i]));
            boards.add(this.prepareBoard(name, PROGRESS_PREPARE_NO_RECORD[i]));
            this.pushPrepareSnapshot(progress, boards);
        }
        // + main
        progress.add(this.prepareProgress("prepare_customgallery.main", PROGRESS_PREPARE_MAIN));
        boards.add(this.prepareBoard("prepare_customgallery.main", PROGRESS_PREPARE_MAIN));
        this.pushPrepareSnapshot(progress, boards);
        // + no_record.total
        progress.add(this.prepareProgress("prepare_customgallery_no_record.total", PROGRESS_PREPARE_TOTAL));
        boards.add(this.prepareBoard("prepare_customgallery_no_record.total", PROGRESS_PREPARE_TOTAL));
        this.pushPrepareSnapshot(progress, boards);

        this.sendBlossomUiHint("31", "true");
        this.sendLevelList();
    }

    private void pushPrepareSnapshot(
            List<_CustomGalleryProgressInfoOuterClass._CustomGalleryProgressInfo> progress,
            List<_CustomGalleryScoreBoardInfoOuterClass._CustomGalleryScoreBoardInfo> boards) {
        this.pushGalleryInfo(this.buildPrepareGalleryInfo(progress, boards, null));
    }

    /** SceneGalleryInfoNotify: battle gallery, GALLERY_PRESTART (waiting for the player). */
    private void sendBattleGalleryPreStart() {
        this.pushGalleryInfoBattle(
                SceneGalleryInfo.newBuilder()
                        .setGalleryId(battleGalleryId)
                        .setStage(GalleryStageType.GalleryStageType_GALLERY_PRESTART)
                        .setOwnerUid(player.getUid())
                        .setPlayerCount(1)
                        .setPreStartEndTime(9999)
                        .build());
    }

    /**
     * SceneGalleryInfo list served through GetAllSceneGalleryInfoRsp while a challenge session is
     * active. Official capture (sniff [1318]): while waiting for the player the battle gallery is
     * reported as GALLERY_PRESTART with pre_start_end_time = 9999.
     */
    public List<SceneGalleryInfo> getActiveGalleryInfos() {
        return switch (this.phase) {
            case PREPARE -> List.of(this.buildPrepareGalleryInfo(null, null, null));
            case BATTLE_READY ->
                    List.of(
                            SceneGalleryInfo.newBuilder()
                                    .setGalleryId(battleGalleryId)
                                    .setStage(GalleryStageType.GalleryStageType_GALLERY_PRESTART)
                                    .setOwnerUid(player.getUid())
                                    .setPlayerCount(1)
                                    .setPreStartEndTime(9999)
                                    .build());
            case BATTLE ->
                    List.of(
                            SceneGalleryInfo.newBuilder()
                                    .setGalleryId(battleGalleryId)
                                    .setStage(GalleryStageType.GalleryStageType_GALLERY_START)
                                    .setOwnerUid(player.getUid())
                                    .setPlayerCount(1)
                                    .setCustomGalleryInfo(
                                            _CustomGalleryInfoOuterClass._CustomGalleryInfo
                                                    .newBuilder()
                                                    .setIsNeedUpdate(true))
                                    .build());
            default -> List.of();
        };
    }

    /** GNLMAGJGKPF handler internals: start the battle gallery and spawn the first wave. */
    private void startBattle() {
        this.phase = Phase.BATTLE;
        this.battleStartMs = System.currentTimeMillis();
        this.waveIndex = 0;

        // GalleryStartNotify
        player.sendPacket(
                new BasePacket(PacketOpcodes.GalleryStartNotify) {
                    {
                        setData(
                                GalleryStartNotify.newBuilder()
                                        .setGalleryId(battleGalleryId)
                                        .setStartTime(6)
                                        .setEndTime(6)
                                        .setOwnerUid(player.getUid())
                                        .setPlayerCount(1)
                                        .build());
                    }
                });

        // SceneGalleryInfoNotify (START, no boards yet)
        this.pushGalleryInfoBattle(
                SceneGalleryInfo.newBuilder()
                        .setGalleryId(battleGalleryId)
                        .setStage(GalleryStageType.GalleryStageType_GALLERY_START)
                        .setOwnerUid(player.getUid())
                        .setStartTime(6)
                        .setPlayerCount(1)
                        .setEndTime(6)
                        .build());

        // _CustomGalleryTargetNotify - BANNER START
        player.sendPacket(
                new BasePacket(PacketOpcodes._CustomGalleryTargetNotify) {
                    {
                        setData(
                                _CustomGalleryTargetNotifyOuterClass._CustomGalleryTargetNotify
                                        .newBuilder()
                                        .setGalleryId(battleGalleryId)
                                        .setTargetId(TARGET_BANNER_START)
                                        .build());
                    }
                });

        // progressive board snapshots: main -> +countdown -> +noscore
        this.pushBattleBoards(
                this.battleBoard("battle_customgallery.main",
                        _CustomGalleryScoreBoardTypeOuterClass._CustomGalleryScoreBoardType
                                ._CustomGalleryScoreBoardType_CUSTOM_GALLERY_SCORE_BOARD_TIMER,
                        0,
                        PROGRESS_BATTLE_MAIN,
                        true),
                null);
        this.pushBattleBoards(
                this.battleBoard("battle_customgallery.main",
                        _CustomGalleryScoreBoardTypeOuterClass._CustomGalleryScoreBoardType
                                ._CustomGalleryScoreBoardType_CUSTOM_GALLERY_SCORE_BOARD_TIMER,
                        0,
                        PROGRESS_BATTLE_MAIN,
                        true),
                this.battleBoard("battle_customgallery.countdown",
                        _CustomGalleryScoreBoardTypeOuterClass._CustomGalleryScoreBoardType
                                ._CustomGalleryScoreBoardType_CUSTOM_GALLERY_SCORE_BOARD_COUNTDOWN,
                        BATTLE_TIME_LIMIT,
                        PROGRESS_BATTLE_COUNTDOWN,
                        true));
        this.pushBattleBoards(
                this.battleBoard("battle_customgallery.main",
                        _CustomGalleryScoreBoardTypeOuterClass._CustomGalleryScoreBoardType
                                ._CustomGalleryScoreBoardType_CUSTOM_GALLERY_SCORE_BOARD_TIMER,
                        0,
                        PROGRESS_BATTLE_MAIN,
                        true),
                this.battleBoard("battle_customgallery.countdown",
                        _CustomGalleryScoreBoardTypeOuterClass._CustomGalleryScoreBoardType
                                ._CustomGalleryScoreBoardType_CUSTOM_GALLERY_SCORE_BOARD_COUNTDOWN,
                        BATTLE_TIME_LIMIT,
                        PROGRESS_BATTLE_COUNTDOWN,
                        true),
                this.battleBoard("battle_customgallery.noscore",
                        _CustomGalleryScoreBoardTypeOuterClass._CustomGalleryScoreBoardType
                                ._CustomGalleryScoreBoardType_CUSTOM_GALLERY_SCORE_BOARD_NORMAL,
                        0,
                        PROGRESS_BATTLE_NOSCORE,
                        false));

        // consume the battle blossom (officially: HP -> 0, death, VISION_DIE)
        var scene = player.getScene();
        if (scene != null) {
            scene.getEntities().values().stream()
                    .filter(e -> e instanceof EntityGadget)
                    .map(e -> (EntityGadget) e)
                    .filter(
                            g ->
                                    g.getGadgetId() == GADGET_BATTLE_BLOSSOM
                                            && g.getGroupId() == ARENA_GROUP_ID)
                    .findFirst()
                    .ifPresent(this::consumeBattleBlossom);
        }

        this.spawnWave();
    }

    private void consumeBattleBlossom(EntityGadget blossom) {
        var fightProp =
                EntityFightPropUpdateNotify.newBuilder()
                        .setEntityId(blossom.getId())
                        .putFightPropMap(1010, 0.0f); // FIGHT_PROP_CUR_HP -> 0
        player.sendPacket(
                new BasePacket(PacketOpcodes.EntityFightPropUpdateNotify) {
                    {
                        setData(fightProp.build());
                    }
                });
        player.sendPacket(
                new PacketLifeStateChangeNotify(blossom, LifeState.LIFE_DEAD));
        player.getScene().removeEntity(blossom, VisionType.VisionType_VISION_DIE);
    }

    /** Spawn the current wave of Stygian monsters. */
    private void spawnWave() {
        var scene = player.getScene();
        if (scene == null) {
            return;
        }
        int monsterId = WAVE_MONSTERS[Math.min(this.waveIndex, WAVE_MONSTERS.length - 1)];
        var monsterData = GameData.getMonsterDataMap().get(monsterId);
        if (monsterData == null) {
            Grasscutter.getLogger().error("[Stygian] Monster {} not found!", monsterId);
            return;
        }

        this.activeWaveEntityIds.clear();
        int count = 1 + this.waveIndex; // wave 0: 1 monster, wave 1: 2 monsters, ...
        for (int i = 0; i < count; i++) {
            float offset = WAVE_SPAWN_OFFSETS[i % WAVE_SPAWN_OFFSETS.length];
            var pos = new Position(POS_MONSTER.getX() + offset, POS_MONSTER.getY(), POS_MONSTER.getZ() + offset * 0.5f);
            var monster =
                    new EntityMonster(scene, monsterData, pos, ROT_MONSTER.clone(), monsterLevel());
            scene.addEntity(monster);
            this.activeWaveEntityIds.add(monster.getId());
        }
    }

    // =====================================================================
    // Packet builders
    // =====================================================================

    private _CustomGalleryProgressInfoOuterClass._CustomGalleryProgressInfo prepareProgress(
            String boardName, int progressId) {
        return _CustomGalleryProgressInfoOuterClass._CustomGalleryProgressInfo.newBuilder()
                .addScoreBoardList(boardName)
                .setMFCCKKAPLML(progressId)
                .build();
    }

    private _CustomGalleryScoreBoardInfoOuterClass._CustomGalleryScoreBoardInfo prepareBoard(
            String name, int progressId) {
        return _CustomGalleryScoreBoardInfoOuterClass._CustomGalleryScoreBoardInfo.newBuilder()
                .setName(name)
                .addProgressIdList(progressId)
                .build();
    }

    private _CustomGalleryScoreBoardInfoOuterClass._CustomGalleryScoreBoardInfo battleBoard(
            String name,
            _CustomGalleryScoreBoardTypeOuterClass._CustomGalleryScoreBoardType type,
            int currentScore,
            int progressId,
            boolean withDetail) {
        var board =
                _CustomGalleryScoreBoardInfoOuterClass._CustomGalleryScoreBoardInfo.newBuilder()
                        .setName(name)
                        .setType(type)
                        .setCurrentScore(currentScore)
                        .setTargetScore(BATTLE_TIME_LIMIT)
                        .addProgressIdList(progressId);
        if (withDetail) {
            if (type
                    == _CustomGalleryScoreBoardTypeOuterClass._CustomGalleryScoreBoardType
                            ._CustomGalleryScoreBoardType_CUSTOM_GALLERY_SCORE_BOARD_TIMER) {
                board.setTimer(
                        _CustomGalleryTimerScoreBoardOuterClass._CustomGalleryTimerScoreBoard
                                .newBuilder()
                                .setStartTime(6)
                                .setPassedTime(-1));
            } else if (type
                    == _CustomGalleryScoreBoardTypeOuterClass._CustomGalleryScoreBoardType
                            ._CustomGalleryScoreBoardType_CUSTOM_GALLERY_SCORE_BOARD_COUNTDOWN) {
                board.setCountdown(
                        _CustomGalleryCountdownScoreBoardOuterClass._CustomGalleryCountdownScoreBoard
                                .newBuilder()
                                .setStartTime(6)
                                .setPassedTime(-1));
            }
        }
        return board.build();
    }

    private SceneGalleryInfo buildPrepareGalleryInfo(
            List<_CustomGalleryProgressInfoOuterClass._CustomGalleryProgressInfo> progress,
            List<_CustomGalleryScoreBoardInfoOuterClass._CustomGalleryScoreBoardInfo> boards,
            INDDEBLOKBMOuterClass.INDDEBLOKBM uiData) {
        var info =
                SceneGalleryInfo.newBuilder()
                        .setGalleryId(prepareGalleryId)
                        .setStage(GalleryStageType.GalleryStageType_GALLERY_START)
                        .setOwnerUid(player.getUid())
                        .setPlayerCount(1);
        if (progress != null && boards != null) {
            var custom =
                    _CustomGalleryInfoOuterClass._CustomGalleryInfo.newBuilder()
                            .setIsNeedUpdate(true);
            custom.addAllProgressList(progress);
            custom.addAllScoreBoardList(boards);
            info.setCustomGalleryInfo(custom);
        }
        if (uiData != null) {
            info.setNNOPJGMIOKB(uiData);
        }
        return info.build();
    }

    private void pushGalleryInfo(SceneGalleryInfo info) {
        player.sendPacket(
                new BasePacket(PacketOpcodes.SceneGalleryInfoNotify) {
                    {
                        setData(NCKODPJHOPCOuterClass.NCKODPJHOPC.newBuilder().setGalleryInfo(info).build());
                    }
                });
    }

    private void pushGalleryInfoBattle(SceneGalleryInfo info) {
        this.pushGalleryInfo(info);
    }

    private void pushBattleBoards(
            _CustomGalleryScoreBoardInfoOuterClass._CustomGalleryScoreBoardInfo... boards) {
        var custom =
                _CustomGalleryInfoOuterClass._CustomGalleryInfo.newBuilder().setIsNeedUpdate(true);
        for (var board : boards) {
            if (board != null) {
                custom.addScoreBoardList(board);
            }
        }
        var info =
                SceneGalleryInfo.newBuilder()
                        .setGalleryId(battleGalleryId)
                        .setStage(GalleryStageType.GalleryStageType_GALLERY_START)
                        .setOwnerUid(player.getUid())
                        .setStartTime(6)
                        .setPlayerCount(1)
                        .setEndTime(6)
                        .setCustomGalleryInfo(custom);
        this.pushGalleryInfo(info.build());
    }

    /** MIKMBPNBIIE (1869) - BlossomInfo / GlobalData UI hints. */
    private void sendBlossomUiHint(String fadeTime, String isAllReady) {
        var blossomInfo =
                FLHLALHGIOJOuterClass.FLHLALHGIOJ.newBuilder()
                        .setTitle("BlossomInfo")
                        .addELFOBGIDMPG(
                                _GalleryContextEntryOuterClass._GalleryContextEntry.newBuilder()
                                        .putGHONGHOHFCC("id", "0")
                                        .putGHONGHOHFCC("fade_time", fadeTime))
                        .build();
        var globalData =
                FLHLALHGIOJOuterClass.FLHLALHGIOJ.newBuilder()
                        .setTitle("GlobalData")
                        .addELFOBGIDMPG(
                                _GalleryContextEntryOuterClass._GalleryContextEntry.newBuilder()
                                        .putGHONGHOHFCC("hideLozengeIds", HIDE_LOZENGE_IDS)
                                        .putGHONGHOHFCC("isAllReady", isAllReady))
                        .build();
        player.sendPacket(
                new BasePacket(PacketOpcodes.MIKMBPNBIIE) {
                    {
                        setData(blossomInfo);
                    }
                });
        player.sendPacket(
                new BasePacket(PacketOpcodes.MIKMBPNBIIE) {
                    {
                        setData(globalData);
                    }
                });
    }

    private void sendLevelList() {
        var rsp = OBFLIEAJIMPOuterClass.OBFLIEAJIMP.newBuilder();
        for (int level : OFFICIAL_LEVEL_LIST) {
            rsp.addKGPGPEFOLBP(level);
        }
        player.sendPacket(
                new BasePacket(PacketOpcodes.OBFLIEAJIMP) {
                    {
                        setData(rsp.build());
                    }
                });
    }

    /** GCPDKENEPKO (5444) - the Stygian challenge state (round records). */
    private BasePacket buildStygianStateNotify() {
        var proto = emu.grasscutter.net.proto.GCPDKENEPKOOuterClass.GCPDKENEPKO.newBuilder();
        proto.setDifficulty(this.difficulty);
        proto.setCurRoundNum(this.currentRound);
        for (int round = 1; round <= 3; round++) {
            var record =
                    DDBNDHNGEMFOuterClass.DDBNDHNGEMF.newBuilder()
                            .setILLJNLJAADM(
                                    LNHPDECBPMOOuterClass.LNHPDECBPMO.getDefaultInstance())
                            .setAAKAOLKKNGC(round);
            proto.addKDFMEPMGGHJ(record);
        }
        return new BasePacket(PacketOpcodes.GCPDKENEPKO) {
            {
                setData(proto.build());
            }
        };
    }

    /**
     * ActivityInfo proto for activity 5269 carrying the ley_line_challenge_detail_info payload.
     * Field-for-field aligned with the official server capture: the same proto is delivered through
     * the ActivityInfoNotify packet and through the activity handler at login
     * (GetActivityInfoRsp), which is what makes the client treat the mode as open.
     */
    public ActivityInfoOuterClass.ActivityInfo buildActivityInfo() {
        var schedule = getScheduleData();
        int scheduleId = schedule != null ? schedule.getScheduleId() : 5269011;
        int beginTime = schedule != null ? schedule.getStartTime() : 0;
        int endTime = schedule != null ? schedule.getEndTime() + SCHEDULE_DURATION_SECONDS : 0;

        var detail =
                _LeyLineChallengeDetailInfoOuterClass._LeyLineChallengeDetailInfo.newBuilder()
                        .setLBCGNOOJDIC(true)
                        .setALJFGLAPKKM(
                                JJKBEPPIFIEOuterClass.JJKBEPPIFIE.newBuilder()
                                        .setEBMOODIKLDM(UI_DATA_TYPE)
                                        .setKOMPLLJLEOL(true)
                                        .setFIDIFABIGBH(scheduleId)
                                        .setOFCFHPFDGLD(
                                                PPOBOBDKPHIOuterClass.PPOBOBDKPHI.newBuilder()
                                                        .setScheduleId(scheduleId))
                                        .setNNPHIFOPPKO(OFFICIAL_CHALLENGE_COUNT))
                        .setLEEHFFLICIK(endTime > 0 ? endTime - 1 : 0);

        // current challenge record (HDAKHICDPPN) for the active difficulty
        var currentRecord =
                HDHLLCCCLJMOuterClass.HDHLLCCCLJM.newBuilder()
                        .setDifficulty(this.difficulty)
                        .setTimestamp(System.currentTimeMillis())
                        .setScheduleId(scheduleId);
        for (int round = 1; round <= 3; round++) {
            currentRecord.addKDFMEPMGGHJ(
                    DDBNDHNGEMFOuterClass.DDBNDHNGEMF.newBuilder()
                            .setILLJNLJAADM(LNHPDECBPMOOuterClass.LNHPDECBPMO.getDefaultInstance())
                            .setAAKAOLKKNGC(round));
        }
        detail.addHDAKHICDPPN(currentRecord);

        // per-round confirmed teams (AKLPNJCALKO)
        for (var entry : this.roundTeams.entrySet()) {
            var teamRecord =
                    FAAOMPMEBKIOuterClass.FAAOMPMEBKI.newBuilder()
                            .setDifficulty(this.difficulty)
                            .setAAKAOLKKNGC(entry.getKey());
            teamRecord.addAllAvatarIdList(entry.getValue());
            detail.addAKLPNJCALKO(teamRecord);
        }

        // difficulty_info_list (1..6)
        for (int d = 1; d <= 6; d++) {
            detail.addDifficultyInfoList(
                    MKADIBABGKHOuterClass.MKADIBABGKH.newBuilder().setDifficulty(d));
        }

        var activityInfo =
                ActivityInfoOuterClass.ActivityInfo.newBuilder()
                        .setActivityType(ACTIVITY_TYPE)
                        .setActivityId(ACTIVITY_ID)
                        .setScheduleId(scheduleId)
                        .setBeginTime(beginTime)
                        .setEndTime(endTime)
                        .setFirstDayStartTime(
                                beginTime > FIRST_DAY_START_OFFSET
                                        ? beginTime - FIRST_DAY_START_OFFSET
                                        : beginTime)
                        .setDIEFHMGFNIJ(OFFICIAL_INFO_FLAG)
                        .setLeyLineChallengeDetailInfo(detail);

        // watcher progress: total (1526911) + per-level watchers 1526901-1526907. The official
        // capture carries total_progress; cur_progress is mirrored so both UI paths render.
        addWatcherInfo(activityInfo, WATCHER_ID_TOTAL, WATCHER_TOTAL_PROGRESS);
        for (int d = 1; d <= 6; d++) {
            addWatcherInfo(activityInfo, 1526900 + d, this.completedRounds.getOrDefault(d, 0));
        }
        // 1526907 (param "6"): shares the difficulty-6 completion counter
        addWatcherInfo(activityInfo, 1526907, this.completedRounds.getOrDefault(6, 0));

        for (int id : EARLIEST_SCHEDULE_IDS) {
            activityInfo.addBFIDMPPFHHC(id);
        }
        for (long value : OFFICIAL_INFO_PARAMS) {
            activityInfo.addOPCOMCJHGAJ(value);
        }

        return activityInfo.build();
    }

    private static void addWatcherInfo(
            ActivityInfoOuterClass.ActivityInfo.Builder activityInfo, int watcherId, int progress) {
        activityInfo.addWatcherInfoList(
                ActivityWatcherInfoOuterClass.ActivityWatcherInfo.newBuilder()
                        .setWatcherId(watcherId)
                        .setCurProgress(progress)
                        .setTotalProgress(progress));
    }

    /** ActivityInfoNotify packet for activity 5269 with the ley_line_challenge_detail_info payload. */
    private BasePacket buildActivityInfoNotify() {
        return new BasePacket(PacketOpcodes.ActivityInfoNotify) {
            {
                setData(
                        ActivityInfoNotifyOuterClass.ActivityInfoNotify.newBuilder()
                                .setActivityInfo(buildActivityInfo())
                                .build());
            }
        };
    }

    private void applyTemporaryTeam(List<Integer> avatarIds) {
        try {
            List<Long> guids = new ArrayList<>();
            for (int avatarId : avatarIds) {
                var avatar = player.getAvatars().getAvatarById(avatarId);
                if (avatar != null) {
                    guids.add(avatar.getGuid());
                }
            }
            if (guids.isEmpty()) {
                return;
            }
            List<List<Long>> teams = new ArrayList<>();
            teams.add(guids);
            player.getTeamManager().setupTemporaryTeam(teams);
            player.getTeamManager().useTemporaryTeam(0);
        } catch (Exception e) {
            Grasscutter.getLogger().warn("[Stygian] failed to apply temporary team", e);
        }
    }

    /** Reset to idle (player left the dungeon through the normal quit flow). */
    public void reset() {
        this.phase = Phase.IDLE;
        this.activeWaveEntityIds.clear();
        this.waveIndex = 0;
    }
}