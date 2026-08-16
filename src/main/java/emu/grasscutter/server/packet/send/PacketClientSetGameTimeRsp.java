package emu.grasscutter.server.packet.send;

import com.google.protobuf.UnknownFieldSet;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.ChangeGameTimeRspOuterClass.ChangeGameTimeRsp;

public class PacketClientSetGameTimeRsp extends BasePacket {
    public PacketClientSetGameTimeRsp(int clientSequence, int clientGameTime, int serverTotalGameTime) {
        super(PacketOpcodes.ClientSetGameTimeRsp, clientSequence);

        ChangeGameTimeRsp proto =
                ChangeGameTimeRsp.newBuilder()
                        .setUnknownFields(
                                UnknownFieldSet.newBuilder()
                                        .addField(1, varint(Math.max(0, clientGameTime)))
                                        .addField(5, varint(0))
                                        .addField(12, varint(Math.max(0, serverTotalGameTime)))
                                        .build())
                        .build();

        this.setData(proto);
    }

    private static UnknownFieldSet.Field varint(int value) {
        return UnknownFieldSet.Field.newBuilder().addVarint(value).build();
    }
}