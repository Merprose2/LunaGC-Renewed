package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.FishBaitGoneNotifyOuterClass.FishBaitGoneNotify;

/** Sent when the bait is consumed (officially at bite time, together with FishBiteRsp). */
public class PacketFishBaitGoneNotify extends BasePacket {
    public PacketFishBaitGoneNotify(int uid) {
        super(PacketOpcodes.FishBaitGoneNotify);

        var proto = FishBaitGoneNotify.newBuilder().setUid(uid).build();
        this.setData(proto);
    }
}
