package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.FishAttractNotifyOuterClass.FishAttractNotify;
import emu.grasscutter.game.world.Position;
import java.util.List;

public class PacketFishAttractNotify extends BasePacket {
    public PacketFishAttractNotify(int uid, Position pos, List<Integer> fishIdList) {
        super(PacketOpcodes.FishAttractNotify);

        var proto = FishAttractNotify.newBuilder()
                .setUid(uid)
                .setPos(pos.toProto())
                .addAllFishIdList(fishIdList)
                .build();

        this.setData(proto);
    }
}