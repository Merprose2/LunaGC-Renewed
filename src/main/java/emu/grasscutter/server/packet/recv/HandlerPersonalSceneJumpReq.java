package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.ScenePointEntry;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PersonalSceneJumpReqOuterClass.PersonalSceneJumpReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketPersonalSceneJumpRsp;

@Opcodes(PacketOpcodes.PersonalSceneJumpReq)
public class HandlerPersonalSceneJumpReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        // Read through the generated proto: point_id = 5. The hand written reader this replaced took
        // the first positive varint in the payload as the point id, which is any field but that one.
        PersonalSceneJumpReq req;
        try {
            req = PersonalSceneJumpReq.parseFrom(payload);
        } catch (Exception exception) {
            Grasscutter.getLogger()
                    .warn("Could not read PersonalSceneJumpReq: {}", exception.getMessage());
            return;
        }

        var player = session.getPlayer();
        var currentSceneId = player.getSceneId();

        int pointId = req.getPointId();

        ScenePointEntry scenePointEntry = null;
        if (pointId > 0) {
            scenePointEntry = GameData.getScenePointEntryById(currentSceneId, pointId);

            if (scenePointEntry == null) {
                scenePointEntry = GameData.getScenePointEntryById(player.getPrevScene(), pointId);
            }

            if (scenePointEntry == null) {
                for (var entry : GameData.getScenePointEntryMap().values()) {
                    if (entry.getPointData() != null && entry.getPointData().getId() == pointId) {
                        scenePointEntry = entry;
                        break;
                    }
                }
            }
        }

        int targetSceneId = 0;
        Position targetPos = null;

        if (scenePointEntry != null && scenePointEntry.getPointData() != null) {
            var pointData = scenePointEntry.getPointData();
            targetSceneId = pointData.getTranSceneId();

            if (pointData.getTranPos() != null) {
                targetPos = pointData.getTranPos().clone();
            } else if (pointData.getPos() != null) {
                targetPos = pointData.getPos().clone();
            }
        }

        if (targetSceneId == 0) {
            if (currentSceneId == 3) {
                targetSceneId = 1004;
            } else if (currentSceneId == 1004) {
                targetSceneId = 3;
            } else {
                targetSceneId = player.getPrevScene() > 0 ? player.getPrevScene() : 3;
            }
        }

        var targetScene = player.getWorld().getSceneById(targetSceneId);
        if (targetScene == null) {
            return;
        }

        if (targetPos == null && targetScene.getScriptManager() != null && targetScene.getScriptManager().getConfig() != null) {
            targetPos = targetScene.getScriptManager().getConfig().born_pos;
        }
        if (targetPos == null) {
            targetPos = player.getPosition().clone();
        }

        player.getWorld().transferPlayerToScene(player, targetSceneId, targetPos);
        if (player.getScene() != null) {
            player.getScene().setPrevScene(currentSceneId);
            player.getScene().setPrevScenePoint(pointId);
        }

        session.send(new PacketPersonalSceneJumpRsp(targetSceneId, targetPos));
    }
}
