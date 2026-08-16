package emu.grasscutter.server.packet.send;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

/**
 * REL6.6 HomeSceneJumpRsp manual encoder.
 *
 * <p>The generated response class is stale. The supplied 6.6 proto identifies:
 *
 * <ul>
 *   <li>is_enter_room_scene-compatible bool = field 13
 *   <li>retcode = field 10
 * </ul>
 */
public class PacketHomeSceneJumpRsp extends BasePacket {

    public PacketHomeSceneJumpRsp(boolean enterRoomScene) {
        this(enterRoomScene, 0);
    }

    public PacketHomeSceneJumpRsp(boolean enterRoomScene, int retcode) {
        super(PacketOpcodes.HomeSceneJumpRsp);

        byte[] payload = encode(enterRoomScene, retcode);

        Grasscutter.getLogger()
                .debug(
                        "[HomeSceneJump66] response opcode={}, enterRoomScene={}, "
                                + "retcode={}, payloadLength={}",
                        PacketOpcodes.HomeSceneJumpRsp,
                        enterRoomScene,
                        retcode,
                        payload.length);

        this.setData(payload);
    }

    private static byte[] encode(boolean enterRoomScene, int retcode) {
        try {
            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            CodedOutputStream output = CodedOutputStream.newInstance(stream);

            // REL6.6 bool field = 13.
            output.writeBool(13, enterRoomScene);

            // REL6.6 retcode field = 10. Success (0) may be omitted.
            if (retcode != 0) {
                output.writeInt32(10, retcode);
            }

            output.flush();
            return stream.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Could not encode REL6.6 HomeSceneJumpRsp", exception);
        }
    }
}
