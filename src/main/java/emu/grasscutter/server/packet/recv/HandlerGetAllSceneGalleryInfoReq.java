package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetAllSceneGalleryInfoRspOuterClass.GetAllSceneGalleryInfoRsp;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.GetAllSceneGalleryInfoReq)
public class HandlerGetAllSceneGalleryInfoReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var proto = GetAllSceneGalleryInfoRsp.newBuilder();

        // The Stygian Onslaught arena reports its gallery state through this response
        // (official: battle gallery 83304 GALLERY_PRESTART, pre_start_end_time = 9999).
        var manager = session.getPlayer().getStygianOnslaughtManager();
        if (manager != null) {
            proto.addAllGalleryInfoList(manager.getActiveGalleryInfos());
        }

        session.send(
                new BasePacket(PacketOpcodes.GetAllSceneGalleryInfoRsp) {
                    {
                        setData(proto.build());
                    }
                });
    }
}
