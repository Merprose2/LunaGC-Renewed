package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.BattlePassAllDataNotifyOuterClass.BattlePassAllDataNotify;

public class PacketBattlePassAllDataNotify extends BasePacket {
    public PacketBattlePassAllDataNotify(Player player) {
        super(PacketOpcodes.BattlePassAllDataNotify);

        var manager = player.getBattlePassManager();

        var proto = BattlePassAllDataNotify.newBuilder()
                .setHaveCurSchedule(true)
                .setIsViewed(true)
                .setBattlePassPlan(manager.getBattlePassPlan())
                .setCurSchedule(manager.getScheduleProto());

        for (var mission : manager.getMissions().values()) {
            if (mission.getData() != null) {
                proto.addMissionList(mission.toProto());
            }
        }

        this.setData(proto);
    }
}