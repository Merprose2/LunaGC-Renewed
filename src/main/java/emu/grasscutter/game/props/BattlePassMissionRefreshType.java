package emu.grasscutter.game.props;

public enum BattlePassMissionRefreshType {
    BATTLE_PASS_MISSION_REFRESH_DAILY(0),
    BATTLE_PASS_MISSION_REFRESH_CYCLE_CROSS_SCHEDULE(2), // Weekly
    BATTLE_PASS_MISSION_REFRESH_SCHEDULE(3),            // BP Schedule
    BATTLE_PASS_MISSION_REFRESH_CYCLE(2);

    private final int value;

    BattlePassMissionRefreshType(int value) {
        this.value = value;
    }

    public int getValue() {
        return value;
    }
}