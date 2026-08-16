package emu.grasscutter.server.packet.recv;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketSetPlayerHeadImageRsp;
import emu.grasscutter.utils.ProfilePictureUtils;

@Opcodes(PacketOpcodes.SetPlayerHeadImageReq)
public class HandlerSetPlayerHeadImageReq extends PacketHandler {
    /*
     * Keep this tolerant because REL6.6 may send either old avatar/head-image ids or new profile_picture_id values depending on the UI path.
     */
    private static final int[] POSSIBLE_ID_FIELDS = {10, 9, 7, 4, 3, 1};

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        int requestedId = readFirstUInt32(payload, POSSIBLE_ID_FIELDS);
        int profilePictureId =
                ProfilePictureUtils.resolveProfilePictureId(session.getPlayer(), requestedId);

        if (profilePictureId != 0) {
            session.getPlayer().setHeadImage(profilePictureId);
        }
        session.send(new PacketSetPlayerHeadImageRsp(session.getPlayer()));
    }

    private static int readFirstUInt32(byte[] payload, int... fields) throws Exception {
        CodedInputStream input = CodedInputStream.newInstance(payload);

        while (!input.isAtEnd()) {
            int tag = input.readTag();

            if (tag == 0) {
                break;
            }

            int fieldNumber = WireFormat.getTagFieldNumber(tag);
            int wireType = WireFormat.getTagWireType(tag);

            if (contains(fields, fieldNumber) && wireType == WireFormat.WIRETYPE_VARINT) {
                return input.readUInt32();
            }

            if (!input.skipField(tag)) {
                break;
            }
        }

        return 0;
    }

    private static boolean contains(int[] values, int value) {
        for (int v : values) {
            if (v == value) {
                return true;
            }
        }

        return false;
    }
}