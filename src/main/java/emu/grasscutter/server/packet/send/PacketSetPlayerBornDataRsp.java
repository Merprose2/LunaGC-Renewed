package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.SetPlayerBornDataRspOuterClass.SetPlayerBornDataRsp;

public class PacketSetPlayerBornDataRsp extends BasePacket {

    public PacketSetPlayerBornDataRsp() {
        this(0);
    }

    public PacketSetPlayerBornDataRsp(int retcode) {
        super(PacketOpcodes.SetPlayerBornDataRsp);

        this.setData(SetPlayerBornDataRsp.newBuilder().setRetcode(retcode).build());
    }
}