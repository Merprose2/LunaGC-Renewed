package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.EnterFishingRspOuterClass.EnterFishingRsp;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.EnterFishingReq)
public class HandlerEnterFishingReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        session.getPlayer().getFishingManager().onEnterFishing();

        var rsp = new BasePacket(PacketOpcodes.EnterFishingRsp);
        rsp.setData(EnterFishingRsp.newBuilder().setRetcode(0).build());
        session.send(rsp);
    }
}