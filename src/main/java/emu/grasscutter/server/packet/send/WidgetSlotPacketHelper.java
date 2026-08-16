package emu.grasscutter.server.packet.send;

import com.google.protobuf.CodedOutputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

final class WidgetSlotPacketHelper {
    static final int WIDGET_SLOT_TAG_QUICK_USE = 0;
    static final int WIDGET_SLOT_OP_ATTACH = 0;
    static final int WIDGET_SLOT_OP_DETACH = 1;

    private WidgetSlotPacketHelper() {}

    static byte[] buildWidgetSlotData(int materialId, int slotTag, boolean active) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CodedOutputStream output = CodedOutputStream.newInstance(baos);

        /*
         * REL6.6 WidgetSlotData:
         * uint32 material_id = 2;
         * bool is_active = 15;
         * uint32 cd_over_time = 11;
         * WidgetSlotTag tag = 1;
         */
        output.writeEnum(1, slotTag);

        if (materialId > 0) {
            output.writeUInt32(2, materialId);
        }

        output.writeBool(15, active);

        output.flush();
        return baos.toByteArray();
    }

    static byte[] buildPackedUInt32(int... values) throws IOException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        CodedOutputStream output = CodedOutputStream.newInstance(baos);

        for (int value : values) {
            if (value > 0) {
                output.writeUInt32NoTag(value);
            }
        }

        output.flush();
        return baos.toByteArray();
    }
}