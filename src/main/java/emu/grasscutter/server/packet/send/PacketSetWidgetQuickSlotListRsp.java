package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto._SetWidgetQuickSlotListRspOuterClass._SetWidgetQuickSlotListRsp;
import java.util.List;

public class PacketSetWidgetQuickSlotListRsp extends BasePacket {

    public PacketSetWidgetQuickSlotListRsp(
            List<Integer> materialIdList,
            int currentSlotNum) {
        super(PacketOpcodes._SetWidgetQuickSlotListRsp);

        _SetWidgetQuickSlotListRsp proto =
                _SetWidgetQuickSlotListRsp.newBuilder()
                        .addAllMaterialIdList(materialIdList)
                        .setRetcode(0)
                        .setCurrentSlotNum(currentSlotNum)
                        .build();

        this.setData(proto);
    }
}