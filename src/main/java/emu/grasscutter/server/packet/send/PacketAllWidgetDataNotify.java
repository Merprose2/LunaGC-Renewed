package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.AllWidgetDataNotifyOuterClass.AllWidgetDataNotify;
import emu.grasscutter.net.proto.LunchBoxDataOuterClass.LunchBoxData;
import emu.grasscutter.net.proto.WidgetSlotDataOuterClass.WidgetSlotData;
import emu.grasscutter.net.proto.WidgetSlotTagOuterClass.WidgetSlotTag;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;

public class PacketAllWidgetDataNotify extends BasePacket {
    private static final int MAX_QUICK_SLOTS = 4;

    public PacketAllWidgetDataNotify(Player player) {
        super(PacketOpcodes.AllWidgetDataNotify);

        /*
         * Quick Swap configuration.
         */
        List<Integer> quickSlots =
                new ArrayList<>();

        if (player.getWidgetQuickSlotList() != null) {
            quickSlots.addAll(
                    player.getWidgetQuickSlotList());
        }

        if (quickSlots.size() > MAX_QUICK_SLOTS) {
            quickSlots =
                    new ArrayList<>(
                            quickSlots.subList(
                                    0,
                                    MAX_QUICK_SLOTS));
        }

        while (quickSlots.size() < MAX_QUICK_SLOTS) {
            quickSlots.add(0);
        }

        int currentSlotNum =
                player.getWidgetQuickSlotCurrentSlotNum();

        if (currentSlotNum < 0
                || currentSlotNum >= MAX_QUICK_SLOTS) {
            currentSlotNum = 0;
        }

        /*
         * NRE Menu 30 configuration.
         *
         * Do not manually encode this field.
         * REL7.0 AllWidgetDataNotify has a genuine
         * LunchBoxData field and the generated builder
         * knows its correct wire number.
         */
        Map<Integer, Integer> lunchBoxSlots =
                player.getLunchBoxSlotMaterialMap() == null
                        ? Collections.emptyMap()
                        : player.getLunchBoxSlotMaterialMap();

        LunchBoxData lunchBoxData =
                LunchBoxData.newBuilder()
                        .putAllSlotMaterialMap(lunchBoxSlots)
                        .build();

        AllWidgetDataNotify.Builder proto =
                AllWidgetDataNotify.newBuilder()
                        .addAllOneoffGatherPointDetectorDataList(
                                List.of())
                        .addAllCoolDownGroupDataList(
                                List.of())
                        .addAllAnchorPointList(
                                List.of())
                        .addAllClientCollectorDataList(
                                List.of())
                        .addAllNormalCoolDownDataList(
                                List.of())

                        /*
                         * REL7.0 Quick Swap fields.
                         */
                        .addAllMaterialIdList(quickSlots)
                        .setCurrentSlotNum(currentSlotNum)

                        /*
                         * REL7.0 LunchBoxData.
                         */
                        .setLunchBoxData(lunchBoxData);

        if (player.getWidgetId() > 0) {
            /*
             * Active quick-use gadget.
             */
            proto.addSlotList(
                    WidgetSlotData.newBuilder()
                            .setMaterialId(
                                    player.getWidgetId())
                            .setTag(
                                    WidgetSlotTag
                                            .WidgetSlotTag_WIDGET_SLOT_QUICK_USE)
                            .setIsActive(true)
                            .build());

            /*
             * Preserve the secondary widget slot entry
             * used by the normal Grasscutter widget state.
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