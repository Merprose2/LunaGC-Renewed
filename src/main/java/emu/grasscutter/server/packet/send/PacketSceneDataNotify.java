package emu.grasscutter.server.packet.send;

import emu.grasscutter.data.GameData;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.SceneDataNotifyOuterClass.SceneDataNotify;

public class PacketSceneDataNotify extends BasePacket {

    public PacketSceneDataNotify(int sceneId) {
        super(PacketOpcodes.SceneDataNotify);

        // Generated proto: map_layer_info = 1. This message has no scene_id field, so the scene only
        // decides whether the big world map layer info is part of the notification.
        var proto = SceneDataNotify.newBuilder();

        if (sceneId == 3) {
            proto.setMapLayerInfo(
                    MapLayerInfoProto66.build(
                            GameData.getMapLayerDataMap().keySet(),
                            GameData.getMapLayerFloorDataMap().keySet(),
                            GameData.getMapLayerGroupDataMap().keySet()));
        }

        this.setData(proto.build());
    }
}