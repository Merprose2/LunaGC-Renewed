package emu.grasscutter.server.packet.send;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class PacketWidgetSlotChangeNotify extends BasePacket {

    public PacketWidgetSlotChangeNotify(int materialId, int slotTag, int op, boolean active) {
        super(PacketOpcodes.WidgetSlotChangeNotify);

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            CodedOutputStream output = CodedOutputStream.newInstance(baos);

            /*
             * REL6.6 WidgetSlotChangeNotify:
             * WidgetSlotData slot = 10;
             * WidgetSlotOp op = 4;
             */
            output.writeByteArray(
                    10,
                    WidgetSlotPacketHelper.buildWidgetSlotData(materialId, slotTag, active));
            output.writeEnum(4, op);

            output.flush();
            this.setData(baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode WidgetSlotChangeNotify for REL6.6", e);
        }
    }
}