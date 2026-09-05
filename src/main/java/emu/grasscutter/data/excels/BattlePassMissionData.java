package emu.grasscutter.data.excels;

import emu.grasscutter.data.*;
import emu.grasscutter.game.props.*;
import emu.grasscutter.net.proto.BattlePassMissionOuterClass.BattlePassMission.MissionStatus;
import java.util.*;
import java.util.stream.Collectors;
import lombok.Getter;

@ResourceType(name = {"BattlePassMissionExcelConfigData.json"})
@Getter
public class BattlePassMissionData extends GameResource {
    @Getter(onMethod_ = @Override)
    private int id;

    private int addPoint;
    private int scheduleId;
    private int progress;
    private TriggerConfig triggerConfig;
    private BattlePassMissionRefreshType refreshType;

    private transient Set<Integer> mainParams = new HashSet<>();

    public WatcherTriggerType getTriggerType() {
        return this.getTriggerConfig() != null ? this.getTriggerConfig().getTriggerType() : null;
    }

    public boolean isCycleRefresh() {
        return getRefreshType() == null
                || getRefreshType() == BattlePassMissionRefreshType.BATTLE_PASS_MISSION_REFRESH_CYCLE_CROSS_SCHEDULE;
    }

    public boolean isValidRefreshType() {
        // Daily missions have refreshType == null; Weekly have CROSS_SCHEDULE; Schedule missions have scheduleId == 7000
        return getRefreshType() == null
                || getRefreshType() == BattlePassMissionRefreshType.BATTLE_PASS_MISSION_REFRESH_CYCLE_CROSS_SCHEDULE
                || this.getScheduleId() == 0
                || this.getScheduleId() == 7000
                || this.getScheduleId() == 7001;
    }

    @Override
    public void onLoad() {
        this.mainParams = new HashSet<>();
        if (this.getTriggerConfig() != null && this.getTriggerConfig().getParamList() != null) {
            for (String param : this.getTriggerConfig().getParamList()) {
                if (param != null && !param.isEmpty()) {
                    Arrays.stream(param.split("[:;,]"))
                            .filter(s -> s.matches("\\d+"))
                            .map(Integer::parseInt)
                            .forEach(this.mainParams::add);
                }
            }
        }
    }

    public emu.grasscutter.net.proto.BattlePassMissionOuterClass.BattlePassMission toProto() {
        var protoBuilder =
                emu.grasscutter.net.proto.BattlePassMissionOuterClass.BattlePassMission.newBuilder();

        protoBuilder
                .setMissionId(getId())
                .setTotalProgress(this.getProgress())
                .setRewardBattlePassPoint(this.getAddPoint())
                .setMissionStatus(MissionStatus.MISSION_UNFINISHED)
                .setMissionType(this.getRefreshType() == null ? 0 : this.getRefreshType().getValue());

        return protoBuilder.build();
    }

    @Getter
    public static class TriggerConfig {
        private WatcherTriggerType triggerType;
        private String[] paramList;
    }
}