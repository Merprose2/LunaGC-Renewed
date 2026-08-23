package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.LunchBoxDataOuterClass.LunchBoxData;
import emu.grasscutter.net.proto.SetUpLunchBoxWidgetReqOuterClass.SetUpLunchBoxWidgetReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketAllWidgetDataNotify;
import emu.grasscutter.server.packet.send.PacketSetUpLunchBoxWidgetRsp;

import java.util.HashMap;
import java.util.Map;

@Opcodes(PacketOpcodes.SetUpLunchBoxWidgetReq)
public class HandlerSetUpLunchBoxWidgetReq extends PacketHandler {

    @Override
    public void handle(
            GameSession session,
            byte[] header,
            byte[] payload)
            throws Exception {

        SetUpLunchBoxWidgetReq request =
                SetUpLunchBoxWidgetReq.parseFrom(payload);

        var player =
                session.getPlayer();

        Map<Integer, Integer> savedSlots =
                player.getLunchBoxSlotMaterialMap();

        if (savedSlots == null) {
            savedSlots =
                    new HashMap<>();
        } else {
            savedSlots =
                    new HashMap<>(savedSlots);
        }

        /*
         * The client may send only the NRE slot that changed.
         * Merge the request into the saved complete configuration.
         */
        for (var entry :
                request.getLunchBoxData()
                        .getSlotMaterialMapMap()
                        .entrySet()) {

            int slot =
                    entry.getKey();

            int materialId =
                    entry.getValue();

            if (materialId == 0) {
                savedSlots.remove(slot);
            } else {
                savedSlots.put(
                        slot,
                        materialId);
            }
        }

        /*
         * Persist the complete NRE setup.
         */
        player.setLunchBoxSlotMaterialMap(
                savedSlots);

        player.save();

        LunchBoxData completeLunchBoxData =
                LunchBoxData.newBuilder()
                        .putAllSlotMaterialMap(
                                savedSlots)
                        .build();

        /*
         * Acknowledge the setup request.
         */
        session.send(
                new PacketSetUpLunchBoxWidgetRsp(
                        completeLunchBoxData));

        /*
         * Refresh the complete widget state using the
         * real REL7.0 AllWidgetDataNotify definition.
         */
        session.send(
                new PacketAllWidgetDataNotify(
                        player));
    }
}