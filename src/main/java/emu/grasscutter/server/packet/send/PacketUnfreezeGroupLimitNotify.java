package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.UnfreezeGroupLimitNotifyOuterClass.UnfreezeGroupLimitNotify;

public class PacketUnfreezeGroupLimitNotify extends BasePacket {

    public PacketUnfreezeGroupLimitNotify(int pointId, int sceneId) {
        super(PacketOpcodes.UnfreezeGroupLimitNotify);

        // Generated proto: point_id = 3 and scene_id = 5. The previous payload also wrote the two
        // fields under a second, guessed pair of numbers, which is pointless with a real schema.
        this.setData(
                UnfreezeGroupLimitNotify.newBuilder()
                        .setPointId(pointId)
                        .setSceneId(sceneId)
                        .build());
    }
}