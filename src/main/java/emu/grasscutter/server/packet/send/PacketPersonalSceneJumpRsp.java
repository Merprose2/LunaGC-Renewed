package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PersonalSceneJumpRspOuterClass.PersonalSceneJumpRsp;

public class PacketPersonalSceneJumpRsp extends BasePacket {
    public PacketPersonalSceneJumpRsp(int sceneId, Position pos) {
        super(PacketOpcodes.PersonalSceneJumpRsp);
        var proto = PersonalSceneJumpRsp.newBuilder().setDestSceneId(sceneId);
        if (pos != null) {
            proto.setDestPos(pos.toProto());
        }
        this.setData(proto.build());
    }
}
