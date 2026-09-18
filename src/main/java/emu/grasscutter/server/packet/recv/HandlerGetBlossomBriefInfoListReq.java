package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.GetBlossomBriefInfoListReqOuterClass.GetBlossomBriefInfoListReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketGetBlossomBriefInfoListRsp;
import java.util.ArrayList;
import java.util.List;

/** Lists the ley line outcrops of the requested cities for the map / handbook. */
@Opcodes(PacketOpcodes.GetBlossomBriefInfoListReq)
public class HandlerGetBlossomBriefInfoListReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var player = session.getPlayer();
        if (player == null || player.getScene() == null) {
            return;
        }

        /*
         * The city list is only used to narrow the answer down.
         *
         * The generated request reads the list from field 14 while some client protos use field 4,
         * so an unreadable or empty list is handled as "every city" - the client filters the answer
         * itself and this way the list is never silently empty.
         */
        List<Integer> cityIds = new ArrayList<>();
        try {
            cityIds.addAll(GetBlossomBriefInfoListReq.parseFrom(payload).getCityIdListList());
        } catch (Exception ignored) {
            // Not fatal: fall back to every city.
        }

        session.send(
                new PacketGetBlossomBriefInfoListRsp(
                        player.getScene().getBlossomManager().buildBriefInfos(cityIds)));
    }
}