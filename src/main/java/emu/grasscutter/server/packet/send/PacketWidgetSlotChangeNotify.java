package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.WidgetSlotChangeNotifyOuterClass.WidgetSlotChangeNotify;
import emu.grasscutter.net.proto.WidgetSlotDataOuterClass.WidgetSlotData;
import emu.grasscutter.net.proto.WidgetSlotOpOuterClass.WidgetSlotOp;
import emu.grasscutter.net.proto.WidgetSlotTagOuterClass.WidgetSlotTag;

public class PacketWidgetSlotChangeNotify extends BasePacket {

    /**
     * Sends the slot reset/detach notification.
     *
     * This deliberately follows the original Grasscutter behavior:
     * DETACH + an active, otherwise-empty quick-use slot.
     */
    public PacketWidgetSlotChangeNotify(WidgetSlotOp op) {
        super(PacketOpcodes.WidgetSlotChangeNotify);

        WidgetSlotData slot =
                WidgetSlotData.newBuilder()
                        .setTag(
                                WidgetSlotTag
                                        .WidgetSlotTag_WIDGET_SLOT_QUICK_USE)
                        .setIsActive(true)
                        .build();

        WidgetSlotChangeNotify proto =
                WidgetSlotChangeNotify.newBuilder()
                        .setSlot(slot)
                        .setOp(op)
                        .build();

        this.setData(proto);
    }

    /**
     * Sends the active gadget notification.
     */
    public PacketWidgetSlotChangeNotify(int materialId) {
        super(PacketOpcodes.WidgetSlotChangeNotify);

        WidgetSlotData slot =
                WidgetSlotData.newBuilder()
                        .setMaterialId(materialId)
                        .setTag(
                                WidgetSlotTag
                                        .WidgetSlotTag_WIDGET_SLOT_QUICK_USE)
                        .setIsActive(true)
                        .build();

        WidgetSlotChangeNotify proto =
                WidgetSlotChangeNotify.newBuilder()
                        .setSlot(slot)
                        .setOp(WidgetSlotOp.WidgetSlotOp_ATTACH)
                        .build();

        this.setData(proto);
    }
}