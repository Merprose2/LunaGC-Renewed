package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.FishEscapeNotifyOuterClass.FishEscapeNotify;
import emu.grasscutter.net.proto.FishEscapeReasonOuterClass.FishEscapeReason;
import emu.grasscutter.game.world.Position;
import java.util.List;

public class PacketFishEscapeNotify extends BasePacket {
    public PacketFishEscapeNotify(int uid, FishEscapeReason reason, Position pos, List<Integer> fishIdList) {
        super(PacketOpcodes.FishEscapeNotify);

        var proto = FishEscapeNotify.newBuilder()
                .setUid(uid)
                .setReason(reason)
                .setPos(pos.toProto())
                .addAllFishIdList(fishIdList)
                .build();

        this.setData(proto);
    }
}