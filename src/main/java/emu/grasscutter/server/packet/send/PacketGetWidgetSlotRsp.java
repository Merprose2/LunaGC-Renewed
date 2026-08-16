package emu.grasscutter.server.packet.send;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class PacketGetWidgetSlotRsp extends BasePacket {

    public PacketGetWidgetSlotRsp(Player player) {
        super(PacketOpcodes.GetWidgetSlotRsp);

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            CodedOutputStream output = CodedOutputStream.newInstance(baos);

            /*
             * REL6.6 GetWidgetSlotRsp:
             * repeated WidgetSlotData slot_list = 14;
             * int32 retcode = 9;
             */
            int quickUseMaterialId = player.getWidgetId();

            if (quickUseMaterialId > 0) {
                output.writeByteArray(
                        14,
                        WidgetSlotPacketHelper.buildWidgetSlotData(
                                quickUseMaterialId,
                                WidgetSlotPacketHelper.WIDGET_SLOT_TAG_QUICK_USE,
                                true));
            }

            output.writeInt32(9, 0);
            output.flush();

            this.setData(baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode GetWidgetSlotRsp for REL6.6", e);
        }
    }
}