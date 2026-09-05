package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.FishCastRodRspOuterClass.FishCastRodRsp;

public class PacketFishCastRodRsp extends BasePacket {
    public PacketFishCastRodRsp(int retcode) {
        super(PacketOpcodes.FishCastRodRsp);

        var proto = FishCastRodRsp.newBuilder()
                .setRetcode(retcode)
                .build();

        this.setData(proto);
    }
}