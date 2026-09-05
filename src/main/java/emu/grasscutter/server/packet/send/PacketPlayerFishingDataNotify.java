package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.PlayerFishingDataNotifyOuterClass.PlayerFishingDataNotify;

public class PacketPlayerFishingDataNotify extends BasePacket {
    public PacketPlayerFishingDataNotify(int lastFishRodId) {
        super(PacketOpcodes.PlayerFishingDataNotify);

        var proto = PlayerFishingDataNotify.newBuilder()
                .setLastFishRodId(lastFishRodId)
                .build();

        this.setData(proto);
    }
}