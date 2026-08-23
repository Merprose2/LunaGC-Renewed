package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.GetWidgetSlotRspOuterClass.GetWidgetSlotRsp;
import emu.grasscutter.net.proto.WidgetSlotDataOuterClass.WidgetSlotData;
import emu.grasscutter.net.proto.WidgetSlotTagOuterClass.WidgetSlotTag;

public class PacketGetWidgetSlotRsp extends BasePacket {

    public PacketGetWidgetSlotRsp(Player player) {
        super(PacketOpcodes.GetWidgetSlotRsp);

        GetWidgetSlotRsp.Builder proto =
                GetWidgetSlotRsp.newBuilder()
                        .setRetcode(0);

        if (player.getWidgetId() > 0) {
            /*
             * Active quick-use gadget.
             */
            proto.addSlotList(
                    WidgetSlotData.newBuilder()
                            .setMaterialId(player.getWidgetId())
                            .setTag(
                                    WidgetSlotTag
                                            .WidgetSlotTag_WIDGET_SLOT_QUICK_USE)
                            .setIsActive(true)
                            .build());

            /*
             * Keep the second widget slot entry.
             *
             * Grasscutter historically sends this alongside the
             * quick-use slot and the client may expect the complete
             * slot table rather than only the active gadget.
             */
            proto.addSlotList(
                    WidgetSlotData.newBuilder()
                            .setTag(
                                    WidgetSlotTag
                                            .WidgetSlotTag_WIDGET_SLOT_ATTACH_AVATAR)
                            .build());
        }

        this.setData(proto.build());
    }
}