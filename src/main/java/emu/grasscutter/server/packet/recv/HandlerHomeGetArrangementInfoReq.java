package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.home.GameHome;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.HomeGetArrangementInfoReqOuterClass;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketHomeGetArrangementInfoRsp;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Opcodes(PacketOpcodes.HomeGetArrangementInfoReq)
public class HandlerHomeGetArrangementInfoReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        // Read through the generated proto: scene_id_list = 8. The payload scan this handler used to
        // run on top of the parse only existed because the list came back empty from the wrong number.
        var req = HomeGetArrangementInfoReqOuterClass.HomeGetArrangementInfoReq.parseFrom(payload);
        var player = session.getPlayer();

        List<Integer> requestedSceneIds = req.getSceneIdListList();
        List<Integer> resolvedSceneIds =
                this.resolveRequestedSceneIds(
                        player.getSceneId(), player.getCurrentRealmId(), requestedSceneIds);

        Grasscutter.getLogger()
                .debug(
                        "[HomeArrangement] request uid={}, opcode={}, currentSceneId={}, "
                                + "currentRealmId={}, requestedSceneIds={}, resolvedSceneIds={}",
                        player.getUid(),
                        PacketOpcodes.HomeGetArrangementInfoReq,
                        player.getSceneId(),
                        player.getCurrentRealmId(),
                        requestedSceneIds,
                        resolvedSceneIds);

        session.send(new PacketHomeGetArrangementInfoRsp(player, resolvedSceneIds));
    }

    private List<Integer> resolveRequestedSceneIds(
            int currentSceneId, int currentRealmId, List<Integer> requestedSceneIds) {
        Set<Integer> resolved = new LinkedHashSet<>();

        if (requestedSceneIds != null) {
            requestedSceneIds.stream()
                    .filter(GameHome.HOME_SCENE_IDS::contains)
                    .forEach(resolved::add);
        }

        /*
         * An empty list would produce an empty arrangement response, leaving the realm without its
         * house, Tubby position or furniture, so fall back to the scenes of the current realm.
         */
        if (resolved.isEmpty()) {
            if (GameHome.HOME_SCENE_IDS.contains(currentSceneId)) {
                resolved.add(currentSceneId);
            }

            int outdoorSceneId = currentRealmId > 0 ? currentRealmId + 2000 : 0;
            if (GameHome.HOME_SCENE_IDS.contains(outdoorSceneId)) {
                resolved.add(outdoorSceneId);
            }
        }

        return new ArrayList<>(resolved);
    }
}
