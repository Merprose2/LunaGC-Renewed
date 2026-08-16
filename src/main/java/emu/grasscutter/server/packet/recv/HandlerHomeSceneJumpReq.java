package emu.grasscutter.server.packet.recv;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.home.GameHome;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketHomeSceneJumpRsp;
import java.io.IOException;

@Opcodes(PacketOpcodes.HomeSceneJumpReq)
public class HandlerHomeSceneJumpReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var player = session.getPlayer();
        var decoded = decodeRequest(payload);

        Grasscutter.getLogger()
                .debug(
                        "[HomeSceneJump66] request uid={}, enterRoomScene={}, field15Found={}, "
                                + "currentSceneId={}, previousSceneId={}, currentRealmId={}, "
                                + "worldClass={}, payloadLength={}",
                        player.getUid(),
                        decoded.enterRoomScene,
                        decoded.field15Found,
                        player.getSceneId(),
                        player.getPrevScene(),
                        player.getCurrentRealmId(),
                        player.getWorld() != null
                                ? player.getWorld().getClass().getSimpleName()
                                : "null",
                        payload != null ? payload.length : 0);

        var world = player.getCurHomeWorld();

        if (world == null
                || player.getWorld() != world
                || !GameHome.HOME_SCENE_IDS.contains(player.getSceneId())) {
            Grasscutter.getLogger()
                    .warn(
                            "[HomeSceneJump66] rejected outside active HomeWorld: uid={}, "
                                    + "sceneId={}, worldClass={}",
                            player.getUid(),
                            player.getSceneId(),
                            player.getWorld() != null
                                    ? player.getWorld().getClass().getSimpleName()
                                    : "null");

            session.send(
                    new PacketHomeSceneJumpRsp(
                            decoded.enterRoomScene, Retcode.RET_FAIL_VALUE));
            return;
        }

        var home = world.getHome();
        var owner = world.getHost();

        int moduleId =
                owner.getCurrentRealmId() > 0
                        ? owner.getCurrentRealmId()
                        : player.getCurrentRealmId();
        int outdoorSceneId = 2000 + moduleId;

        var outdoorArrangement = home.getHomeSceneItem(outdoorSceneId);

        if (outdoorArrangement == null) {
            Grasscutter.getLogger()
                    .warn(
                            "[HomeSceneJump66] missing outdoor arrangement: uid={}, "
                                    + "moduleId={}, outdoorSceneId={}",
                            player.getUid(),
                            moduleId,
                            outdoorSceneId);

            session.send(
                    new PacketHomeSceneJumpRsp(
                            decoded.enterRoomScene, Retcode.RET_FAIL_VALUE));
            return;
        }

        int indoorSceneId = outdoorArrangement.getRoomSceneId();
        int targetSceneId = decoded.enterRoomScene ? indoorSceneId : outdoorSceneId;
        var targetArrangement = home.getHomeSceneItem(targetSceneId);
        var targetScene = world.getSceneById(targetSceneId);

        if (targetSceneId <= 0 || targetArrangement == null || targetScene == null) {
            Grasscutter.getLogger()
                    .warn(
                            "[HomeSceneJump66] missing target Home scene: uid={}, "
                                    + "enterRoomScene={}, outdoorSceneId={}, indoorSceneId={}, "
                                    + "targetSceneId={}, arrangementFound={}, sceneFound={}",
                            player.getUid(),
                            decoded.enterRoomScene,
                            outdoorSceneId,
                            indoorSceneId,
                            targetSceneId,
                            targetArrangement != null,
                            targetScene != null);

            session.send(
                    new PacketHomeSceneJumpRsp(
                            decoded.enterRoomScene, Retcode.RET_FAIL_VALUE));
            return;
        }

        Position targetPos;
        Position targetRot;
        String positionSource;

        if (decoded.enterRoomScene) {
            /*
             * Indoor HomeworldDefaultSave bornPos is an arrangement/editing
             * anchor and places the avatar in a side room. The scene script
             * contains the actual entrance spawn.
             */
            var scriptConfig = targetScene.getScriptManager().getConfig();

            if (scriptConfig != null && scriptConfig.born_pos != null) {
                targetPos = scriptConfig.born_pos.clone();
                positionSource = "scene-script";

                targetRot =
                        scriptConfig.born_rot != null
                                ? scriptConfig.born_rot.clone()
                                : targetArrangement.getBornRot().clone();
            } else {
                targetPos = targetArrangement.getBornPos().clone();
                targetRot = targetArrangement.getBornRot().clone();
                positionSource = "arrangement-fallback";
            }
        } else {
            // The outdoor arrangement bornPos is the mansion doorstep.
            targetPos = outdoorArrangement.getBornPos().clone();
            targetRot = outdoorArrangement.getBornRot().clone();
            positionSource = "outdoor-arrangement";
        }

        player.getRotation().set(targetRot);
        home.save();

        int sourceSceneId = player.getSceneId();
        boolean transferred =
                world.transferPlayerToScene(player, targetSceneId, targetPos);

        Grasscutter.getLogger()
                .debug(
                        "[HomeSceneJump66] transfer result uid={}, success={}, "
                                + "enterRoomScene={}, fromSceneId={}, targetSceneId={}, "
                                + "outdoorSceneId={}, indoorSceneId={}, positionSource={}, "
                                + "targetPos={}, targetRot={}",
                        player.getUid(),
                        transferred,
                        decoded.enterRoomScene,
                        sourceSceneId,
                        targetSceneId,
                        outdoorSceneId,
                        indoorSceneId,
                        positionSource,
                        targetPos,
                        targetRot);

        session.send(
                new PacketHomeSceneJumpRsp(
                        decoded.enterRoomScene,
                        transferred ? Retcode.RET_SUCC_VALUE : Retcode.RET_FAIL_VALUE));
    }

    private static DecodedRequest decodeRequest(byte[] payload) throws IOException {
        if (payload == null || payload.length == 0) {
            // Proto3 false is omitted, which is the normal Leave Mansion request.
            return new DecodedRequest(false, false);
        }

        CodedInputStream input = CodedInputStream.newInstance(payload);
        boolean enterRoomScene = false;
        boolean field15Found = false;

        while (!input.isAtEnd()) {
            int tag = input.readTag();

            if (tag == 0) {
                break;
            }

            int fieldNumber = WireFormat.getTagFieldNumber(tag);
            int wireType = WireFormat.getTagWireType(tag);

            if (fieldNumber == 15 && wireType == WireFormat.WIRETYPE_VARINT) {
                enterRoomScene = input.readBool();
                field15Found = true;
            } else {
                input.skipField(tag);
            }
        }

        return new DecodedRequest(enterRoomScene, field15Found);
    }

    private static final class DecodedRequest {
        private final boolean enterRoomScene;
        private final boolean field15Found;

        private DecodedRequest(boolean enterRoomScene, boolean field15Found) {
            this.enterRoomScene = enterRoomScene;
            this.field15Found = field15Found;
        }
    }
}
