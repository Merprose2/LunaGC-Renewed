package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.SetWidgetSlotRspOuterClass.SetWidgetSlotRsp;
import java.util.List;

public class PacketSetWidgetSlotRsp extends BasePacket {

    public PacketSetWidgetSlotRsp(
            int materialId,
            int op,
            List<Integer> tagList) {
        super(PacketOpcodes.SetWidgetSlotRsp);

        SetWidgetSlotRsp.Builder proto =
                SetWidgetSlotRsp.newBuilder()
                        .setOpValue(op)
                        .setRetcode(0);

        if (materialId > 0) {
            proto.setMaterialId(materialId);
        }

        if (tagList != null && !tagList.isEmpty()) {
            proto.addAllTagListValue(tagList);
        }

        this.setData(proto.build());
    }
}