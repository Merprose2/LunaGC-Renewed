package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.EnterFishingReqOuterClass.EnterFishingReq;
import emu.grasscutter.net.proto.EnterFishingRspOuterClass.EnterFishingRsp;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.EnterFishingReq)
public class HandlerEnterFishingReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = EnterFishingReq.parseFrom(payload);
        // The req carries the fishing pool's ENTITY id; official servers echo it back in the rsp.
        int poolEntityId = req.getFishPoolId();

        session.getPlayer().getFishingManager().onEnterFishing(poolEntityId);

        var rsp = new BasePacket(PacketOpcodes.EnterFishingRsp);
        rsp.setData(EnterFishingRsp.newBuilder().setRetcode(0).setFishPoolId(poolEntityId).build());
        session.send(rsp);
    }
}