package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.FishChosenNotifyOuterClass.FishChosenNotify;

public class PacketFishChosenNotify extends BasePacket {
    public PacketFishChosenNotify(int fishEntityId) {
        super(PacketOpcodes.FishChosenNotify);

        var proto = FishChosenNotify.newBuilder()
                .setFishId(fishEntityId)
                .build();

        this.setData(proto);
    }
}