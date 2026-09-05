package emu.grasscutter.game.battlepass;

import dev.morphia.annotations.*;
import emu.grasscutter.*;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.database.DatabaseHelper;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.proto.BattlePassCycleOuterClass.BattlePassCycle;
import emu.grasscutter.net.proto.BattlePassProductOuterClass.BattlePassProduct;
import emu.grasscutter.net.proto.BattlePassRewardPlanOptionOuterClass.BattlePassRewardPlanOption;
import emu.grasscutter.net.proto.BattlePassRewardTakeOptionOuterClass.BattlePassRewardTakeOption;
import emu.grasscutter.net.proto.BattlePassScheduleOuterClass.BattlePassSchedule;
import emu.grasscutter.net.proto.BattlePassUnlockStatusOuterClass.BattlePassUnlockStatus;
import emu.grasscutter.server.packet.send.*;
import java.time.*;
import java.time.temporal.TemporalAdjusters;
import java.util.*;
import lombok.Getter;
import lombok.Setter;
import org.bson.types.ObjectId;

@Entity(value = "battlepass", useDiscriminator = false)
public class BattlePassManager extends BasePlayerDataManager {
    public static final int CURRENT_SCHEDULE_ID = 7000;

    @Id @Getter private ObjectId id;

    @Indexed private int ownerUid;
    @Getter private int point;
    @Getter private int cyclePoints; // Weekly maximum cap
    @Getter private int level;

    @Getter private boolean viewed = true;
    private boolean paid;
    @Getter @Setter private int battlePassPlan = 4;

    private Map<Integer, BattlePassMission> missions;
    private Map<Integer, BattlePassReward> takenRewards;

    @Deprecated // Morphia only
    public BattlePassManager() {}

    public BattlePassManager(Player player) {
        super(player);
        this.ownerUid = player.getUid();
    }

    public void setPlayer(Player player) {
        this.player = player;
        this.ownerUid = player.getUid();
    }

    public void updateViewed() {
        this.viewed = true;
    }

    public boolean setLevel(int level) {
        if (level >= 0 && level <= GameConstants.BATTLE_PASS_MAX_LEVEL) {
            this.level = level;
            this.point = 0;
            this.player.sendPacket(new PacketBattlePassCurScheduleUpdateNotify(this.player));
            return true;
        }
        return false;
    }

    public void addPoints(int points) {
        this.addPointsDirectly(points, false);

        this.player.sendPacket(new PacketBattlePassCurScheduleUpdateNotify(player));
        this.save();
    }

    public void addPointsDirectly(int points, boolean isWeekly) {
        int amount = points;

        if (isWeekly) {
            amount = Math.min(amount, GameConstants.BATTLE_PASS_POINT_PER_WEEK - this.cyclePoints);
        }

        if (amount <= 0) {
            return;
        }

        this.point += amount;
        this.cyclePoints += amount;

        if (this.point >= GameConstants.BATTLE_PASS_POINT_PER_LEVEL
                && this.getLevel() < GameConstants.BATTLE_PASS_MAX_LEVEL) {
            int levelups = Math.floorDiv(this.point, GameConstants.BATTLE_PASS_POINT_PER_LEVEL);

            levelups = Math.min(levelups, GameConstants.BATTLE_PASS_MAX_LEVEL - levelups);

            this.point = this.point - (levelups * GameConstants.BATTLE_PASS_POINT_PER_LEVEL);
            this.level += levelups;
        }
    }

    /**
     * Checks and completes the daily login mission (ID: 72001) if unfinished.
     */
    public void checkLoginMission() {
        BattlePassMission loginMission = this.loadMissionById(72001);
        if (loginMission.getStatus() == BattlePassMissionStatus.MISSION_STATUS_UNFINISHED) {
            loginMission.setProgress(1);
            loginMission.setStatus(BattlePassMissionStatus.MISSION_STATUS_FINISHED);
            this.save();
        }
    }

    public Map<Integer, BattlePassMission> getMissions() {
        if (this.missions == null) this.missions = new HashMap<>();

        if (this.missions.isEmpty() && GameData.getBattlePassMissionDataMap() != null) {
            for (BattlePassMissionData data : GameData.getBattlePassMissionDataMap().values()) {
                if (data.isValidRefreshType()) {
                    this.missions.put(data.getId(), new BattlePassMission(data.getId()));
                }
            }
        }

        // Auto-complete login quest so it unlocks 1/1
        checkLoginMission();

        return this.missions;
    }

    public BattlePassMission loadMissionById(int id) {
        if (this.missions == null) this.missions = new HashMap<>();
        return this.missions.computeIfAbsent(id, BattlePassMission::new);
    }

    public boolean hasMission(int id) {
        return getMissions().containsKey(id);
    }

    public boolean isPaid() {
        return true;
    }

    public Map<Integer, BattlePassReward> getTakenRewards() {
        if (this.takenRewards == null) this.takenRewards = new HashMap<>();
        return this.takenRewards;
    }

    public void triggerMission(WatcherTriggerType triggerType) {
        getPlayer().getServer().getBattlePassSystem().triggerMission(getPlayer(), triggerType);
    }

    public void triggerMission(WatcherTriggerType triggerType, int param, int progress) {
        getPlayer()
                .getServer()
                .getBattlePassSystem()
                .triggerMission(getPlayer(), triggerType, param, progress);
    }

    public void takeMissionPoint(List<Integer> missionIdList) {
        if (missionIdList == null || missionIdList.isEmpty() 
                || missionIdList.size() > GameData.getBattlePassMissionDataMap().size()) {
            return;
        }

        List<BattlePassMission> updatedMissions = new ArrayList<>(missionIdList.size());

        for (int id : missionIdList) {
            if (!this.hasMission(id)) {
                continue;
            }

            BattlePassMission mission = this.loadMissionById(id);

            if (mission.getData() == null) {
                this.getMissions().remove(mission.getId());
                continue;
            }

            if (mission.getStatus() == BattlePassMissionStatus.MISSION_STATUS_FINISHED) {
                this.addPointsDirectly(mission.getData().getAddPoint(), mission.getData().isCycleRefresh());
                mission.setStatus(BattlePassMissionStatus.MISSION_STATUS_POINT_TAKEN);
                updatedMissions.add(mission);
            }
        }

        if (!updatedMissions.isEmpty()) {
            this.save();
            getPlayer().sendPacket(new PacketBattlePassMissionUpdateNotify(updatedMissions));
            getPlayer().sendPacket(new PacketBattlePassCurScheduleUpdateNotify(getPlayer()));
        }
    }

    private void takeRewardsFromSelectChest(
            ItemData rewardItemData, int index, ItemParamData entry, List<GameItem> rewardItems) {
        if (rewardItemData.getItemUse().size() < 1) {
            return;
        }

        String[] choices = rewardItemData.getItemUse().get(0).getUseParam()[0].split(",");
        if (choices.length < index || index <= 0) {
            return;
        }

        int chosenId = Integer.parseInt(choices[index - 1]);

        if (rewardItemData.getItemUse().get(0).getUseOp() == ItemUseOp.ITEM_USE_ADD_SELECT_ITEM) {
            GameItem rewardItem =
                    new GameItem(GameData.getItemDataMap().get(chosenId), entry.getItemCount());
            rewardItems.add(rewardItem);
        } else if (rewardItemData.getItemUse().get(0).getUseOp()
                == ItemUseOp.ITEM_USE_GRANT_SELECT_REWARD) {
            RewardData selectedReward = GameData.getRewardDataMap().get(chosenId);

            for (var r : selectedReward.getRewardItemList()) {
                GameItem rewardItem =
                        new GameItem(GameData.getItemDataMap().get(r.getItemId()), r.getItemCount());
                rewardItems.add(rewardItem);
            }
        } else {
            Grasscutter.getLogger().error("Invalid chest type for BP reward.");
        }
    }

    public void takeReward(List<BattlePassRewardTakeOption> takeOptionList) {
        if (takeOptionList == null || takeOptionList.isEmpty()) {
            getPlayer().sendPacket(new PacketTakeBattlePassRewardRsp(takeOptionList, null));
            return;
        }

        int plan = this.getBattlePassPlan();
        if (plan <= 0 || plan > 4) {
            plan = 4;
        }

        List<BattlePassRewardTakeOption> acceptedOptions = new ArrayList<>();
        List<GameItem> rewardItems = new ArrayList<>();

        for (BattlePassRewardTakeOption option : takeOptionList) {
            var tag = option.getTag();
            if (tag == null) continue;

            int level = tag.getLevel();
            if (level <= 0 || level > this.getLevel()) {
                continue;
            }

            boolean isPaidReward = tag.getUnlockStatus() == BattlePassUnlockStatus.BattlePassUnlockSTATUS_BATTLE_PASS_UNLOCK_PAID;
            if (isPaidReward && !this.isPaid()) {
                continue;
            }

            int lookupKey = plan * 100 + level;
            BattlePassRewardData rewardData = GameData.getBattlePassRewardDataMap().get(lookupKey);
            if (rewardData == null) {
                rewardData = GameData.getBattlePassRewardDataMap().get(100 + level);
                if (rewardData == null) continue;
            }

            List<Integer> rewardIdsToGrant = new ArrayList<>();
            List<Integer> candidateList = isPaidReward ? rewardData.getPaidRewardIdList() : rewardData.getFreeRewardIdList();

            if (candidateList == null || candidateList.isEmpty()) {
                continue;
            }

            if (tag.getRewardId() != 0 && candidateList.contains(tag.getRewardId())) {
                if (!this.getTakenRewards().containsKey(tag.getRewardId())) {
                    rewardIdsToGrant.add(tag.getRewardId());
                }
            } else {
                for (int rid : candidateList) {
                    if (rid != 0 && !this.getTakenRewards().containsKey(rid)) {
                        rewardIdsToGrant.add(rid);
                    }
                }
            }

            if (rewardIdsToGrant.isEmpty()) {
                continue;
            }

            boolean grantedAny = false;
            for (int rid : rewardIdsToGrant) {
                RewardData reward = GameData.getRewardDataMap().get(rid);
                if (reward == null) continue;

                for (var entry : reward.getRewardItemList()) {
                    ItemData itemData = GameData.getItemDataMap().get(entry.getItemId());
                    if (itemData == null) continue;

                    if (itemData.getMaterialType() == MaterialType.MATERIAL_SELECTABLE_CHEST) {
                        this.takeRewardsFromSelectChest(itemData, option.getOptionIdx(), entry, rewardItems);
                    } else {
                        rewardItems.add(new GameItem(itemData, entry.getItemCount()));
                    }
                }

                BattlePassReward bpReward = new BattlePassReward(level, rid, isPaidReward);
                this.getTakenRewards().put(rid, bpReward);
                grantedAny = true;
            }

            if (grantedAny) {
                acceptedOptions.add(option);
            }
        }

        if (!rewardItems.isEmpty()) {
            this.save();
            getPlayer().getInventory().addItems(rewardItems);
            getPlayer().sendPacket(new PacketBattlePassCurScheduleUpdateNotify(getPlayer()));
        }

        getPlayer().sendPacket(new PacketTakeBattlePassRewardRsp(acceptedOptions, rewardItems));
    }

    public int buyLevels(int buyLevel) {
        int boughtLevels = Math.min(buyLevel, GameConstants.BATTLE_PASS_MAX_LEVEL - buyLevel);

        if (boughtLevels > 0) {
            int price = GameConstants.BATTLE_PASS_LEVEL_PRICE * boughtLevels;

            if (getPlayer().getPrimogems() < price) {
                return 0;
            }

            this.level += boughtLevels;
            this.save();

            getPlayer().sendPacket(new PacketBattlePassCurScheduleUpdateNotify(getPlayer()));
        }

        return boughtLevels;
    }

    public void resetDailyMissions() {
        var resetMissions = new ArrayList<BattlePassMission>();

        for (var mission : this.missions.values()) {
            if (mission.getData() == null || mission.getData().getRefreshType() == null
                    || mission.getData().getRefreshType()
                            == BattlePassMissionRefreshType.BATTLE_PASS_MISSION_REFRESH_DAILY) {
                mission.setStatus(BattlePassMissionStatus.MISSION_STATUS_UNFINISHED);
                mission.setProgress(0);

                resetMissions.add(mission);
            }
        }

        this.getPlayer().sendPacket(new PacketBattlePassMissionUpdateNotify(resetMissions));
        this.getPlayer().sendPacket(new PacketBattlePassCurScheduleUpdateNotify(this.getPlayer()));
    }

    public void resetWeeklyMissions() {
        var resetMissions = new ArrayList<BattlePassMission>();

        for (var mission : this.missions.values()) {
            if (mission.getData() != null && mission.getData().getRefreshType()
                    == BattlePassMissionRefreshType.BATTLE_PASS_MISSION_REFRESH_CYCLE_CROSS_SCHEDULE) {
                mission.setStatus(BattlePassMissionStatus.MISSION_STATUS_UNFINISHED);
                mission.setProgress(0);

                resetMissions.add(mission);
            }
        }

        this.getPlayer().sendPacket(new PacketBattlePassMissionUpdateNotify(resetMissions));
        this.getPlayer().sendPacket(new PacketBattlePassCurScheduleUpdateNotify(this.getPlayer()));
    }

    public BattlePassSchedule getScheduleProto() {
        int now = (int) (System.currentTimeMillis() / 1000);
        var currentDate = LocalDate.now();
        var nextSundayDate =
                (currentDate.getDayOfWeek() == DayOfWeek.SUNDAY)
                        ? currentDate
                        : LocalDate.now().with(TemporalAdjusters.next(DayOfWeek.SUNDAY));
        var nextSundayTime =
                LocalDateTime.of(
                        nextSundayDate.getYear(),
                        nextSundayDate.getMonthValue(),
                        nextSundayDate.getDayOfMonth(),
                        23,
                        59,
                        59);

        int cycleEnd = (int) nextSundayTime.atZone(ZoneId.systemDefault()).toEpochSecond();
        int cycleBegin = cycleEnd - (7 * 86400);

        BattlePassProduct productInfo = BattlePassProduct.newBuilder()
                .setNormalProductId("ys_glb_bp_normal_tier10")
                .setExtraProductId("ys_glb_bp_extra_tier20")
                .setUpgradeProductId("ys_glb_bp_upgrade_tier12")
                .setLJKGANJLFNJ("ysglbbpextradiscounttierbp15")
                .setLGOGOFAFJBK("ysglbbpnormaldiscounttierbp5")
                .build();

        BattlePassSchedule.Builder schedule =
                BattlePassSchedule.newBuilder()
                        .setScheduleId(CURRENT_SCHEDULE_ID)
                        .setLevel(this.getLevel())
                        .setPoint(this.getPoint())
                        .setCurCyclePoints(this.getCyclePoints())
                        .setBeginTime(now - (7 * 86400))
                        .setEndTime(now + (35 * 86400))
                        .setIsViewed(this.isViewed())
                        .setProductInfo(productInfo)
                        .setUnlockStatus(
                                this.isPaid()
                                        ? BattlePassUnlockStatus.BattlePassUnlockSTATUS_BATTLE_PASS_UNLOCK_PAID
                                        : BattlePassUnlockStatus.BattlePassUnlockSTATUS_BATTLE_PASS_UNLOCK_FREE)
                        .setCurCycle(
                                BattlePassCycle.newBuilder()
                                        .setBeginTime(cycleBegin)
                                        .setEndTime(cycleEnd)
                                        .setCycleIdx(4));

        for (int i = 1; i <= 5; i++) {
            schedule.addRewardPlanOptionList(
                    BattlePassRewardPlanOption.newBuilder()
                            .setBattlePassPlan(this.getBattlePassPlan())
                            .setFBHFDJJIDBD(i)
                            .build());
        }

        for (BattlePassReward reward : getTakenRewards().values()) {
            schedule.addRewardTakenList(reward.toProto());
        }

        return schedule.build();
    }

    public void save() {
        DatabaseHelper.saveBattlePass(this);
    }
}