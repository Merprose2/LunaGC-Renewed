package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.net.proto.PingReqOuterClass.PingReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketPingRsp;

@Opcodes(PacketOpcodes.PingReq)
public class HandlerPingReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        PacketHead head = PacketHead.parseFrom(header);

        // Read through the generated proto: client_time = 1 and seq = 15. Field 7, which this handler
        // used to hand read as the sequence number, is _cur_fps - which is why the echoed sequence
        // always looked like a frame rate.
        PingReq req = PingReq.parseFrom(payload);

        session.updateLastPingTime(req.getClientTime());

        session.send(new PacketPingRsp(head.getClientSequenceId(), req.getClientTime(), req.getSeq()));
    }
}
