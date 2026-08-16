package emu.grasscutter.server.packet.recv;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
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
        PersonalSceneJumpReq req = null;
        try {
            req = PersonalSceneJumpReq.parseFrom(payload);
        } catch (Exception ignored) {}

        var player = session.getPlayer();
        var currentSceneId = player.getSceneId();

        int pointId = decodePointId(req, payload);

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

    private int decodePointId(PersonalSceneJumpReq req, byte[] payload) {
        if (req != null && req.getPointId() > 0) {
            return req.getPointId();
        }
        if (payload == null || payload.length == 0) {
            return 0;
        }
        try {
            CodedInputStream input = CodedInputStream.newInstance(payload);
            while (!input.isAtEnd()) {
                int tag = input.readTag();
                if (tag == 0) break;
                int wireType = WireFormat.getTagWireType(tag);
                if (wireType == WireFormat.WIRETYPE_VARINT) {
                    int val = input.readUInt32();
                    if (val > 0) return val;
                } else {
                    input.skipField(tag);
                }
            }
        } catch (Exception ignored) {}
        return 0;
    }
}
