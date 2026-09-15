package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto.SetOpenStateRspOuterClass.SetOpenStateRsp;

public class PacketSetOpenStateRsp extends BasePacket {

    public PacketSetOpenStateRsp(int openState, int value) {
        super(PacketOpcodes.SetOpenStateRsp);

        // Generated proto: key = 6 and value = 12.
        this.setData(
                SetOpenStateRsp.newBuilder()
                        .setKey(openState)
                        .setValue(value)
                        .build());
    }

    public PacketSetOpenStateRsp(Retcode retcode) {
        super(PacketOpcodes.SetOpenStateRsp);

        // Generated proto: retcode = 9.
        this.setData(
                SetOpenStateRsp.newBuilder()
                        .setRetcode(retcode.getNumber())
                        .build());
    }
}
