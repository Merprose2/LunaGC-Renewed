package emu.grasscutter.server.packet.recv;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketSetPlayerSignatureRsp;

@Opcodes(PacketOpcodes.SetPlayerSignatureReq)
public class HandlerSetPlayerSignatureReq extends PacketHandler {
    /*
     * REL6.6:
     * SetPlayerSignatureReq
     * CmdID: 9039
     * string signature = 13;
     *
     * Fallback fields are kept for older/stale layouts.
     */
    private static final int[] SIGNATURE_FIELDS = {13, 14, 10};

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        String signature = readString(payload, SIGNATURE_FIELDS);

        session.getPlayer().setSignature(signature);
        session.send(new PacketSetPlayerSignatureRsp(session.getPlayer()));
    }

    private static String readString(byte[] payload, int... fields) throws Exception {
        CodedInputStream input = CodedInputStream.newInstance(payload);

        while (!input.isAtEnd()) {
            int tag = input.readTag();

            if (tag == 0) {
                break;
            }

            int fieldNumber = WireFormat.getTagFieldNumber(tag);
            int wireType = WireFormat.getTagWireType(tag);

            if (contains(fields, fieldNumber)
                    && wireType == WireFormat.WIRETYPE_LENGTH_DELIMITED) {
                return input.readStringRequireUtf8();
            }

            if (!input.skipField(tag)) {
                break;
            }
        }

        return "";
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