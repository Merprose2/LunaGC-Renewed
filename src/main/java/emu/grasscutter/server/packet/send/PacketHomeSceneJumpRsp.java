package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.HomeSceneJumpRspOuterClass.HomeSceneJumpRsp;

public class PacketHomeSceneJumpRsp extends BasePacket {

    public PacketHomeSceneJumpRsp(boolean enterRoomScene) {
        this(enterRoomScene, 0);
    }

    public PacketHomeSceneJumpRsp(boolean enterRoomScene, int retcode) {
        super(PacketOpcodes.HomeSceneJumpRsp);

        // Generated proto: is_enter_room_scene = 6 and retcode = 12. Those numbers are not stable
        // between game versions, so they must never be hand written.
        this.setData(
                HomeSceneJumpRsp.newBuilder()
                        .setIsEnterRoomScene(enterRoomScene)
                        .setRetcode(retcode)
                        .build());
    }
}
