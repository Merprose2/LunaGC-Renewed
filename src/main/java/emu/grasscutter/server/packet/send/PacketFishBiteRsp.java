package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.FishBiteRspOuterClass.FishBiteRsp;

public class PacketFishBiteRsp extends BasePacket {
    public PacketFishBiteRsp(int retcode) {
        super(PacketOpcodes.FishBiteRsp);

        var proto = FishBiteRsp.newBuilder()
                .setRetcode(retcode)
                .build();

        this.setData(proto);
    }
}