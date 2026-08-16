package emu.grasscutter.server.packet.send;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class PacketGetWidgetQuickSlotListRsp extends BasePacket {

    public PacketGetWidgetQuickSlotListRsp(Player player) {
        super(PacketOpcodes.GetWidgetQuickSlotListRsp);

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            CodedOutputStream output = CodedOutputStream.newInstance(baos);

            /*
             * REL6.6 _GetWidgetQuickSlotListRsp:
             * repeated uint32 material_id_list = 4;
             * uint32 current_slot_num = 1;
             * int32 retcode = 14;
             */
            int quickUseMaterialId = player.getWidgetId();

            if (quickUseMaterialId > 0) {
                output.writeByteArray(
                        4,
                        WidgetSlotPacketHelper.buildPackedUInt32(quickUseMaterialId));
                output.writeUInt32(1, 1);
            } else {
                output.writeUInt32(1, 0);
            }

            output.writeInt32(14, 0);
            output.flush();

            this.setData(baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode GetWidgetQuickSlotListRsp for REL6.6", e);
        }
    }
}