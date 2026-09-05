package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.FishBattleEndReqOuterClass.FishBattleEndReq;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.FishBattleEndReq)
public class HandlerFishBattleEndReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = FishBattleEndReq.parseFrom(payload);
        session.getPlayer().getFishingManager().onFishBattleEnd(req.getBattleResult());
    }
}