package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.ExitFishingRspOuterClass.ExitFishingRsp;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.ExitFishingReq)
public class HandlerExitFishingReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        session.getPlayer().getFishingManager().onExitFishing();

        var rsp = new BasePacket(PacketOpcodes.ExitFishingRsp);
        rsp.setData(ExitFishingRsp.newBuilder().setRetcode(0).build());
        session.send(rsp);
    }
}