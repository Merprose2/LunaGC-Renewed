package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.ScenePointEntry;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.SceneTransToPointReqOuterClass.SceneTransToPointReq;
import emu.grasscutter.server.event.player.PlayerTeleportEvent.TeleportType;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketSceneTransToPointRsp;

@Opcodes(PacketOpcodes.SceneTransToPointReq)
public class HandlerSceneTransToPointReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        SceneTransToPointReq req = SceneTransToPointReq.parseFrom(payload);
        var player = session.getPlayer();

        Grasscutter.getLogger()
                .debug(
                        "[HomeExit] SceneTransToPointReq: uid={}, sceneId={}, pointId={}, "
                                + "currentSceneId={}, previousSceneId={}, worldClass={}, "
                                + "isMultiplayer={}, sceneLoadState={}, peerId={}, payloadLength={}",
                        player.getUid(),
                        req.getSceneId(),
                        req.getPointId(),
                        player.getSceneId(),
                        player.getPrevScene(),
                        player.getWorld() != null
                                ? player.getWorld().getClass().getSimpleName()
                                : "null",
                        player.getWorld() != null && player.getWorld().isMultiplayer(),
                        player.getSceneLoadState(),
                        player.getPeerId(),
                        payload != null ? payload.length : 0);

        ScenePointEntry scenePointEntry =
                GameData.getScenePointEntryById(req.getSceneId(), req.getPointId());

        if (scenePointEntry == null
                || scenePointEntry.getPointData() == null
                || scenePointEntry.getPointData().getTranPos() == null) {
            Grasscutter.getLogger()
                    .warn(
                            "[HomeExit] SceneTransToPointReq has no valid destination: "
                                    + "uid={}, sceneId={}, pointId={}, entryFound={}",
                            player.getUid(),
                            req.getSceneId(),
                            req.getPointId(),
                            scenePointEntry != null);
            session.send(new PacketSceneTransToPointRsp());
            return;
        }

        var destination = scenePointEntry.getPointData().getTranPos().clone();
        boolean isInHome =
                player.getCurHomeWorld() != null
                        && player.getCurHomeWorld().isInHome(player);

        if (isInHome) {
            boolean leftHome =
                    session
                            .getServer()
                            .getHomeWorldMPSystem()
                            .leaveCoop(player, req.getSceneId(), destination);

            Grasscutter.getLogger()
                    .debug(
                            "[HomeExit] leaveCoop result: uid={}, success={}, "
                                    + "requestedSceneId={}, requestedPointId={}, worldClass={}, "
                                    + "isMultiplayer={}, currentSceneId={}, sceneLoadState={}, peerId={}",
                            player.getUid(),
                            leftHome,
                            req.getSceneId(),
                            req.getPointId(),
                            player.getWorld() != null
                                    ? player.getWorld().getClass().getSimpleName()
                                    : "null",
                            player.getWorld() != null && player.getWorld().isMultiplayer(),
                            player.getSceneId(),
                            player.getSceneLoadState(),
                            player.getPeerId());

            /*
             * Do not tell the client that the teleport succeeded if leaveCoop
             * refused to perform the Home-world transition.
             */
            if (leftHome) {
                session.send(
                        new PacketSceneTransToPointRsp(
                                player, req.getPointId(), req.getSceneId()));
            } else {
                session.send(new PacketSceneTransToPointRsp());
            }

            return;
        }

        if (player.getWorld() != null
                && player
                        .getWorld()
                        .transferPlayerToScene(
                                player,
                                req.getSceneId(),
                                TeleportType.WAYPOINT,
                                destination)) {
            session.send(
                    new PacketSceneTransToPointRsp(
                            player, req.getPointId(), req.getSceneId()));
            return;
        }

        Grasscutter.getLogger()
                .warn(
                        "[HomeExit] Normal scene transfer failed: uid={}, "
                                + "sceneId={}, pointId={}, worldClass={}",
                        player.getUid(),
                        req.getSceneId(),
                        req.getPointId(),
                        player.getWorld() != null
                                ? player.getWorld().getClass().getSimpleName()
                                : "null");

        session.send(new PacketSceneTransToPointRsp());
    }
}
