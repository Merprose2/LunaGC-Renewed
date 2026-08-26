package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.ClientSetGameTimeRspOuterClass.ClientSetGameTimeRsp;

public class PacketClientSetGameTimeRsp extends BasePacket {
    public PacketClientSetGameTimeRsp(int clientSequence, int clientGameTime, int gameTime) {
        super(PacketOpcodes.ClientSetGameTimeRsp, clientSequence);

        ClientSetGameTimeRsp proto =
                ClientSetGameTimeRsp.newBuilder()
                        .setClientGameTime(clientGameTime)
                        .setGameTime(gameTime)
                        .setRetcode(0)
                        .build();

        this.setData(proto);
    }
}