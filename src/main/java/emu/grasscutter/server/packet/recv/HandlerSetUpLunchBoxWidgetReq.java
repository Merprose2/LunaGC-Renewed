package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.LunchBoxDataOuterClass.LunchBoxData;
import emu.grasscutter.net.proto.SetUpLunchBoxWidgetReqOuterClass.SetUpLunchBoxWidgetReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketNreLunchBoxDataNotify;
import emu.grasscutter.server.packet.send.PacketSetUpLunchBoxWidgetRsp;

import java.util.HashMap;
import java.util.Map;

@Opcodes(PacketOpcodes.SetUpLunchBoxWidgetReq)
public class HandlerSetUpLunchBoxWidgetReq extends PacketHandler {

    @Override
    public void handle(
            GameSession session,
            byte[] header,
            byte[] payload
    ) throws Exception {
        var request = SetUpLunchBoxWidgetReq.parseFrom(payload);
        var player = session.getPlayer();

        Map<Integer, Integer> savedSlots =
                player.getLunchBoxSlotMaterialMap();

        if (savedSlots == null) {
            savedSlots = new HashMap<>();
        } else {
            savedSlots = new HashMap<>(savedSlots);
        }

        /*
         * REL6.6 sends the changed NRE slot rather than necessarily sending
         * both configured slots in every request.
         *
         * A zero material ID is treated as clearing that slot.
         */
        for (var entry :
                request.getLunchBoxData()
                        .getSlotMaterialMapMap()
                        .entrySet()) {
            int slot = entry.getKey();
            int materialId = entry.getValue();

            if (materialId == 0) {
                savedSlots.remove(slot);
            } else {
                savedSlots.put(slot, materialId);
            }
        }

        player.setLunchBoxSlotMaterialMap(savedSlots);
        player.save();

        LunchBoxData completeLunchBoxData =
                LunchBoxData.newBuilder()
                        .putAllSlotMaterialMap(savedSlots)
                        .build();

        Grasscutter.getLogger().info(
                "[NRE] Saved configuration for UID {}: {}",
                player.getUid(),
                savedSlots);

        /*
         * Respond with the complete configuration, not only the single slot
         * present in this particular request.
         */
        session.send(
                new PacketSetUpLunchBoxWidgetRsp(
                        completeLunchBoxData));

        /*
         * Also refresh the authoritative widget data immediately.
         */
        session.send(
                new PacketNreLunchBoxDataNotify(player));
    }
}