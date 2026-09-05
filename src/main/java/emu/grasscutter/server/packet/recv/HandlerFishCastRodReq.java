package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.FishCastRodReqOuterClass.FishCastRodReq;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.FishCastRodReq)
public class HandlerFishCastRodReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = FishCastRodReq.parseFrom(payload);

        int baitId = req.getNNOGJAJECDP();
        int rodId = req.getLMJDFOBAEPB();
        Position pos = new Position(req.getPos());

        session.getPlayer().getFishingManager().onCastRod(baitId, rodId, pos);
    }
}