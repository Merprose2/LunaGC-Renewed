package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.GetWidgetQuickSlotListRspOuterClass.GetWidgetQuickSlotListRsp;

public class PacketGetWidgetQuickSlotListRsp extends BasePacket {

    public PacketGetWidgetQuickSlotListRsp(Player player) {
        super(PacketOpcodes._GetWidgetQuickSlotListRsp);

        GetWidgetQuickSlotListRsp.Builder builder =
                GetWidgetQuickSlotListRsp.newBuilder();

        int quickUseMaterialId =
                player.getWidgetId();

        if (quickUseMaterialId > 0) {
            builder.addMaterialIdList(quickUseMaterialId);
        }

        /*
         * Success.
         *
         * In the REL7.0 generated definition,
         * retcode is field 1.
         */
        builder.setRetcode(0);

        GetWidgetQuickSlotListRsp proto = builder.build();

        this.setData(proto);
    }
}