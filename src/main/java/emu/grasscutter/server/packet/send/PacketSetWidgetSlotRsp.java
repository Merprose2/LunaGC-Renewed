package emu.grasscutter.server.packet.send;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

public class PacketSetWidgetSlotRsp extends BasePacket {

    public PacketSetWidgetSlotRsp(int materialId, int op, List<Integer> tagList) {
        super(PacketOpcodes.SetWidgetSlotRsp);

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            CodedOutputStream output = CodedOutputStream.newInstance(baos);

            /*
             * REL6.6 SetWidgetSlotRsp:
             * repeated WidgetSlotTag tag_list = 15;
             * uint32 material_id = 11;
             * int32 retcode = 7;
             * WidgetSlotOp op = 3;
             */
            output.writeEnum(3, op);
            output.writeInt32(7, 0);

            if (materialId > 0) {
                output.writeUInt32(11, materialId);
            }

            if (tagList != null && !tagList.isEmpty()) {
                for (int tag : tagList) {
                    output.writeEnum(15, tag);
                }
            } else {
                output.writeEnum(15, WidgetSlotPacketHelper.WIDGET_SLOT_TAG_QUICK_USE);
            }

            output.flush();
            this.setData(baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode SetWidgetSlotRsp for REL6.6", e);
        }
    }
}