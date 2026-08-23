package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto._SetWidgetQuickSlotListReqOuterClass._SetWidgetQuickSlotListReq;
import emu.grasscutter.net.proto.WidgetSlotOpOuterClass.WidgetSlotOp;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketAllWidgetDataNotify;
import emu.grasscutter.server.packet.send.PacketGetWidgetSlotRsp;
import emu.grasscutter.server.packet.send.PacketSetWidgetQuickSlotListRsp;
import emu.grasscutter.server.packet.send.PacketWidgetSlotChangeNotify;

import java.util.ArrayList;
import java.util.List;

@Opcodes(PacketOpcodes._SetWidgetQuickSlotListReq)
public class HandlerSetWidgetQuickSlotListReq extends PacketHandler {
    private static final int MAX_QUICK_SLOTS = 4;

    @Override
    public void handle(
            GameSession session,
            byte[] header,
            byte[] payload)
            throws Exception {

        _SetWidgetQuickSlotListReq req =
                _SetWidgetQuickSlotListReq.parseFrom(payload);

        Player player = session.getPlayer();

        List<Integer> materialIdList =
                normalizeQuickSlots(req.getMaterialIdListList());

        /*
         * REL7.0 current_slot_num is zero-based.
         *
         * 0 = first slot
         * 1 = second slot
         * 2 = third slot
         * 3 = fourth slot
         */
        int currentSlotNum = req.getCurrentSlotNum();

        if (currentSlotNum >= MAX_QUICK_SLOTS) {
            Grasscutter.getLogger()
                    .warn(
                            "[QuickSwap] uid={} received invalid slot index {}, forcing slot 0",
                            player.getUid(),
                            currentSlotNum);

            currentSlotNum = 0;
        }

        int newMaterialId =
                materialIdList.get(currentSlotNum);
		
        player.setWidgetQuickSlotList(materialIdList);
        player.setWidgetQuickSlotCurrentSlotNum(currentSlotNum);
        player.setWidgetId(newMaterialId);
        player.save();

        /*
         * Reset the client's active quick-use slot first.
         *
         * This is intentionally the same sequence historically used by
         * Grasscutter for widget equipment.
         */
        session.send(
                new PacketWidgetSlotChangeNotify(
                        WidgetSlotOp.WidgetSlotOp_DETACH));

        /*
         * If the selected quickswap position contains a gadget,
         * make that gadget active.
         */
        if (newMaterialId > 0) {
            session.send(
                    new PacketWidgetSlotChangeNotify(
                            newMaterialId));
        }

        session.send(
                new PacketSetWidgetQuickSlotListRsp(
                        materialIdList,
                        currentSlotNum));
		session.send(
				new PacketGetWidgetSlotRsp(
						player));

		session.send(
				new PacketAllWidgetDataNotify(
						player));
    }

    private List<Integer> normalizeQuickSlots(
            List<Integer> incoming) {

        List<Integer> slots =
                new ArrayList<>(MAX_QUICK_SLOTS);

        int count =
                Math.min(
                        incoming.size(),
                        MAX_QUICK_SLOTS);

        for (int i = 0; i < count; i++) {
            slots.add(incoming.get(i));
        }

        while (slots.size() < MAX_QUICK_SLOTS) {
            slots.add(0);
        }

        return slots;
    }
}