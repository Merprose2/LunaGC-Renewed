package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.SetPlayerSignatureReqOuterClass.SetPlayerSignatureReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketSetPlayerSignatureRsp;

@Opcodes(PacketOpcodes.SetPlayerSignatureReq)
public class HandlerSetPlayerSignatureReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        // Read through the generated proto: signature = 13. The two extra candidate numbers the
        // hand written reader kept would have read the signature out of an unrelated field.
        var req = SetPlayerSignatureReq.parseFrom(payload);

        session.getPlayer().setSignature(req.getSignature());
        session.send(new PacketSetPlayerSignatureRsp(session.getPlayer()));
    }
}