package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.BattlePassCurScheduleUpdateNotifyOuterClass.BattlePassCurScheduleUpdateNotify;

public class PacketBattlePassCurScheduleUpdateNotify extends BasePacket {
    public PacketBattlePassCurScheduleUpdateNotify(Player player) {
        super(PacketOpcodes.BattlePassCurScheduleUpdateNotify);

        var manager = player.getBattlePassManager();

        var proto = BattlePassCurScheduleUpdateNotify.newBuilder()
                .setHaveCurSchedule(true)
                .setIsViewed(true)
                .setBattlePassPlan(manager.getBattlePassPlan())
                .setCurSchedule(manager.getScheduleProto());

        this.setData(proto);
    }
}