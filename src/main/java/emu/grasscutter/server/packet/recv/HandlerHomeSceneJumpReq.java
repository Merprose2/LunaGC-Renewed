package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.home.GameHome;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.HomeSceneJumpReqOuterClass.HomeSceneJumpReq;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketHomeSceneJumpRsp;

@Opcodes(PacketOpcodes.HomeSceneJumpReq)
public class HandlerHomeSceneJumpReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var player = session.getPlayer();

        // Read through the generated proto: is_enter_room_scene = 11. The hand written decoder this
        // replaced read field 15, which is a different field of the message, so the room/mansion
        // choice it made was arbitrary.
        HomeSceneJumpReq req;
        try {
            req = HomeSceneJumpReq.parseFrom(payload);
        } catch (Exception exception) {
            Grasscutter.getLogger()
                    .warn("Could not read HomeSceneJumpReq: {}", exception.getMessage());
            return;
        }

        boolean enterRoomScene = req.getIsEnterRoomScene();

        Grasscutter.getLogger()
                .debug(
                        "[HomeSceneJump] request uid={}, enterRoomScene={}, "
                                + "currentSceneId={}, previousSceneId={}, currentRealmId={}, "
                                + "worldClass={}, payloadLength={}",
                        player.getUid(),
                        enterRoomScene,
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
                            "[HomeSceneJump] rejected outside active HomeWorld: uid={}, "
                                    + "sceneId={}, worldClass={}",
                            player.getUid(),
                            player.getSceneId(),
                            player.getWorld() != null
                                    ? player.getWorld().getClass().getSimpleName()
                                    : "null");

            session.send(new PacketHomeSceneJumpRsp(enterRoomScene, Retcode.RET_FAIL_VALUE));
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
                            "[HomeSceneJump] missing outdoor arrangement: uid={}, moduleId={}, "
                                    + "outdoorSceneId={}",
                            player.getUid(),
                            moduleId,
                            outdoorSceneId);

            session.send(new PacketHomeSceneJumpRsp(enterRoomScene, Retcode.RET_FAIL_VALUE));
            return;
        }

        int indoorSceneId = outdoorArrangement.getRoomSceneId();
        int targetSceneId = enterRoomScene ? indoorSceneId : outdoorSceneId;
        var targetArrangement = home.getHomeSceneItem(targetSceneId);
        var targetScene = world.getSceneById(targetSceneId);

        if (targetSceneId <= 0 || targetArrangement == null || targetScene == null) {
            Grasscutter.getLogger()
                    .warn(
                            "[HomeSceneJump] missing target Home scene: uid={}, enterRoomScene={}, "
                                    + "outdoorSceneId={}, indoorSceneId={}, targetSceneId={}, "
                                    + "arrangementFound={}, sceneFound={}",
                            player.getUid(),
                            enterRoomScene,
                            outdoorSceneId,
                            indoorSceneId,
                            targetSceneId,
                            targetArrangement != null,
                            targetScene != null);

            session.send(new PacketHomeSceneJumpRsp(enterRoomScene, Retcode.RET_FAIL_VALUE));
            return;
        }

        Position targetPos;
        Position targetRot;
        String positionSource;

        if (enterRoomScene) {
            /*
             * Indoor HomeworldDefaultSave bornPos is an arrangement/editing anchor and places the
             * avatar in a side room. The scene script contains the actual entrance spawn.
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
        boolean transferred = world.transferPlayerToScene(player, targetSceneId, targetPos);

        Grasscutter.getLogger()
                .debug(
                        "[HomeSceneJump] transfer result uid={}, success={}, enterRoomScene={}, "
                                + "fromSceneId={}, targetSceneId={}, outdoorSceneId={}, "
                                + "indoorSceneId={}, positionSource={}, targetPos={}, targetRot={}",
                        player.getUid(),
                        transferred,
                        enterRoomScene,
                        sourceSceneId,
                        targetSceneId,
                        outdoorSceneId,
                        indoorSceneId,
                        positionSource,
                        targetPos,
                        targetRot);

        session.send(
                new PacketHomeSceneJumpRsp(
                        enterRoomScene,
                        transferred ? Retcode.RET_SUCC_VALUE : Retcode.RET_FAIL_VALUE));
    }
}