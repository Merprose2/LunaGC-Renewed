package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.FishPoolDataNotifyOuterClass.FishPoolDataNotify;

public class PacketFishPoolDataNotify extends BasePacket {
    public PacketFishPoolDataNotify(int poolEntityId, int todayFishNum) {
        super(PacketOpcodes.FishPoolDataNotify);

        var proto = FishPoolDataNotify.newBuilder()
                .setEntityId(poolEntityId)
                .setTodayFishNum(todayFishNum)
                .build();

        this.setData(proto);
    }
}