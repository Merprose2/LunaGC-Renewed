package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.SetPlayerHeadImageReqOuterClass.SetPlayerHeadImageReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketSetPlayerHeadImageRsp;
import emu.grasscutter.utils.ProfilePictureUtils;

@Opcodes(PacketOpcodes.SetPlayerHeadImageReq)
public class HandlerSetPlayerHeadImageReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        SetPlayerHeadImageReq req = SetPlayerHeadImageReq.parseFrom(payload);
        int profilePictureId = ProfilePictureUtils.resolveProfilePictureId(session.getPlayer(), req.getProfilePictureId());
        if (profilePictureId != 0) {
            session.getPlayer().setHeadImage(profilePictureId);
        }
        session.send(new PacketSetPlayerHeadImageRsp(session.getPlayer()));
    }
}