package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.EnterWorldAreaReqOuterClass.EnterWorldAreaReq;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.game.props.AreaType;
import emu.grasscutter.server.packet.send.PacketAbilityChangeNotify;
import emu.grasscutter.server.packet.send.PacketEnterWorldAreaRsp;

@Opcodes(PacketOpcodes.EnterWorldAreaReq)
public class HandlerEnterWorldAreaReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        PacketHead head = PacketHead.parseFrom(header);
        EnterWorldAreaReq enterWorld = EnterWorldAreaReq.parseFrom(payload);

        var player = session.getPlayer();
        player.setArea(enterWorld.getAreaId(), AreaType.getTypeByValue(enterWorld.getAreaType()));
        session.send(new PacketEnterWorldAreaRsp(head.getClientSequenceId(), enterWorld));

        // Toggle the phlogiston ability block in the team/avatar abilities on area boundary crossing.
        player.getTeamManager().updateTeamProperties();
        player.sendPacket(new PacketAbilityChangeNotify(player.getTeamManager().getEntity().getId(), player.getTeamManager().getAbilityControlBlock()));
    }
}
