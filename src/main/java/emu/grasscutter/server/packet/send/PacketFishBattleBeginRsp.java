package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.FishBattleBeginRspOuterClass.FishBattleBeginRsp;

public class PacketFishBattleBeginRsp extends BasePacket {
    public PacketFishBattleBeginRsp(int retcode) {
        super(PacketOpcodes.FishBattleBeginRsp);

        var proto = FishBattleBeginRsp.newBuilder()
                .setRetcode(retcode)
                .build();

        this.setData(proto);
    }
}