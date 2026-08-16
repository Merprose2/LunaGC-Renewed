package emu.grasscutter.server.packet.send;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PlayerWorldSceneInfoListNotifyOuterClass.PlayerWorldSceneInfoListNotify;
import emu.grasscutter.net.proto.PlayerWorldSceneInfoOuterClass.PlayerWorldSceneInfo;
import java.util.Map;

public class PacketPlayerWorldSceneInfoListNotify extends BasePacket {

    public PacketPlayerWorldSceneInfoListNotify(Player player) {
        super(PacketOpcodes.PlayerWorldSceneInfoListNotify);

        var sceneTags = player.getSceneTags();

        PlayerWorldSceneInfoListNotify.Builder proto =
                PlayerWorldSceneInfoListNotify.newBuilder()
                        .addInfoList(
                                PlayerWorldSceneInfo.newBuilder()
                                        .setSceneId(1)
                                        .setIsLocked(false)
                                        .build());

        for (int scene : GameData.getSceneDataMap().keySet()) {
            var worldInfoBuilder =
                    PlayerWorldSceneInfo.newBuilder()
                            .setSceneId(scene)
                            .setIsLocked(false);

            if (sceneTags.keySet().contains(scene)) {
                worldInfoBuilder.addAllSceneTagIdList(
                        sceneTags.entrySet().stream()
                                .filter(e -> e.getKey().equals(scene))
                                .map(Map.Entry::getValue)
                                .toList()
                                .get(0));
            }

            // Big world map-layer unlock data.
            // Use REL6.6 unknown-field layout instead of the stale generated MapLayerInfo setters.
            if (scene == 3) {
                var layerIds = GameData.getMapLayerDataMap().keySet();
                var floorIds = GameData.getMapLayerFloorDataMap().keySet();
                var groupIds = GameData.getMapLayerGroupDataMap().keySet();

                worldInfoBuilder.setMapLayerInfo(
                        MapLayerInfoProto66.build(layerIds, floorIds, groupIds));

                Grasscutter.getLogger()
                        .info(
                                "MapLayerInfo66 in PlayerWorldSceneInfoListNotify: layers={}, floors={}, groups={}",
                                layerIds.size(),
                                floorIds.size(),
                                groupIds.size());
            }

            proto.addInfoList(worldInfoBuilder.build());
        }

        this.setData(proto);
    }
}