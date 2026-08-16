package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.home.GameHome;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.OtherPlayerEnterHomeNotifyOuterClass;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.*;
import java.util.ArrayList;
import java.util.List;

@Opcodes(PacketOpcodes.HomeSceneInitFinishReq)
public class HandlerHomeSceneInitFinishReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var player = session.getPlayer();
        var curHomeWorld = player.getCurHomeWorld();

        if (curHomeWorld == null
                || player.getWorld() != curHomeWorld
                || !GameHome.HOME_SCENE_IDS.contains(player.getSceneId())) {
            Grasscutter.getLogger()
                    .warn(
                            "[HomeSceneInitCompat] Ignored request outside active Home scene: "
                                    + "uid={}, sceneId={}, worldClass={}, payloadLength={}",
                            player.getUid(),
                            player.getSceneId(),
                            player.getWorld() != null
                                    ? player.getWorld().getClass().getSimpleName()
                                    : "null",
                            payload != null ? payload.length : 0);
            return;
        }

        if (!player.isHasSentInitPacketInHome()) {
            player.setHasSentInitPacketInHome(true);

            if (curHomeWorld.getHost().isOnline()
                    && !curHomeWorld.getHost().equals(player)) {
                curHomeWorld
                        .getHost()
                        .sendPacket(
                                new PacketOtherPlayerEnterOrLeaveHomeNotify(
                                        player,
                                        OtherPlayerEnterHomeNotifyOuterClass
                                                .OtherPlayerEnterHomeNotify
                                                .Reason
                                                .ENTER));
            }
        }

        /*
         * Send both the active outdoor scene and its indoor scene. REL6.6 did
         * not request arrangement data during the initial Home load, so this
         * compatibility path supplies it as part of the init handshake.
         */
        List<Integer> arrangementSceneIds = new ArrayList<>();
        arrangementSceneIds.add(player.getSceneId());

        int indoorSceneId = curHomeWorld.getActiveIndoorSceneId();
        if (indoorSceneId > 0 && indoorSceneId != player.getSceneId()) {
            arrangementSceneIds.add(indoorSceneId);
        }

        session.send(new PacketHomeGetArrangementInfoRsp(player, arrangementSceneIds));

        /*
         * Re-send the authoritative Home state after scene initialization.
         * Besides normal Home UI data, this gives the client another chance to
         * accept currentRealmId and unlockedModuleIdList after first selection.
         */
        session.send(new PacketHomeBasicInfoNotify(player, false));
        session.send(new PacketPlayerHomeCompInfoNotify(player));
        session.send(new PacketHomeComfortInfoNotify(player));
        session.send(new PacketHomeResourceNotify(player));
        session.send(new PacketHomeMarkPointNotify(player));

        curHomeWorld.ifHost(
                player,
                owner -> {
                    owner.sendPacket(new PacketHomeAvatarRewardEventNotify(owner));
                    owner.sendPacket(new PacketHomeAvatarSummonAllEventNotify(owner));
                });

        Grasscutter.getLogger()
                .debug(
                        "[HomeSceneInitCompat] Processed Home scene init: "
                                + "uid={}, sceneId={}, indoorSceneId={}, currentRealmId={}, "
                                + "realmList={}, arrangementSceneIds={}, payloadLength={}",
                        player.getUid(),
                        player.getSceneId(),
                        indoorSceneId,
                        player.getCurrentRealmId(),
                        player.getRealmList(),
                        arrangementSceneIds,
                        payload != null ? payload.length : 0);

        /*
         * HomeSceneInitFinishRsp is deliberately NOT sent yet.
         *
         * Its REL6.6 opcode is still unknown and PacketOpcodes currently holds
         * the placeholder value 1. Sending opcode 1 would be more dangerous
         * than omitting the empty response during this compatibility test.
         */
    }
}
