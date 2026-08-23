package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.SetWidgetSlotReqOuterClass.SetWidgetSlotReq;
import emu.grasscutter.net.proto.WidgetSlotOpOuterClass.WidgetSlotOp;
import emu.grasscutter.net.proto.WidgetSlotTagOuterClass.WidgetSlotTag;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketAllWidgetDataNotify;
import emu.grasscutter.server.packet.send.PacketGetWidgetSlotRsp;
import emu.grasscutter.server.packet.send.PacketSetWidgetSlotRsp;
import emu.grasscutter.server.packet.send.PacketWidgetSlotChangeNotify;

import java.util.ArrayList;
import java.util.List;

@Opcodes(PacketOpcodes.SetWidgetSlotReq)
public class HandlerSetWidgetSlotReq extends PacketHandler {
    private static final int MAX_QUICK_SLOTS = 4;

    @Override
    public void handle(
            GameSession session,
            byte[] header,
            byte[] payload)
            throws Exception {

        SetWidgetSlotReq req =
                SetWidgetSlotReq.parseFrom(payload);

        Player player = session.getPlayer();

        int materialId = req.getMaterialId();
        int op = req.getOpValue();

        List<Integer> tagList =
                new ArrayList<>(
                        req.getTagListValueList());

        if (tagList.isEmpty()) {
            tagList.add(
                    WidgetSlotTag
                            .WidgetSlotTag_WIDGET_SLOT_QUICK_USE_VALUE);
        }

        if (op == WidgetSlotOp.WidgetSlotOp_DETACH_VALUE) {
            handleDetach(
                    session,
                    player,
                    materialId,
                    tagList);

            return;
        }

        handleAttach(
                session,
                player,
                materialId,
                tagList);
    }

    private void handleAttach(
            GameSession session,
            Player player,
            int materialId,
            List<Integer> tagList) {

        if (materialId <= 0) {
            session.send(
                    new PacketSetWidgetSlotRsp(
                            materialId,
                            WidgetSlotOp.WidgetSlotOp_ATTACH_VALUE,
                            tagList));

            return;
        }

        /*
         * Directly equipping a gadget from the inventory replaces the
         * gadget in the CURRENT quickswap position.
         */
        updateCurrentQuickSlot(
                player,
                materialId);

        player.setWidgetId(materialId);
        player.save();

		session.send(
				new PacketWidgetSlotChangeNotify(
						WidgetSlotOp.WidgetSlotOp_DETACH));

		session.send(
				new PacketWidgetSlotChangeNotify(
						materialId));

		session.send(
				new PacketSetWidgetSlotRsp(
						materialId,
						WidgetSlotOp.WidgetSlotOp_ATTACH_VALUE,
						tagList));

		sendCurrentWidgetState(session, player);
    }

    private void handleDetach(
            GameSession session,
            Player player,
            int requestedMaterialId,
            List<Integer> tagList) {

        int oldMaterialId = player.getWidgetId();

        int responseMaterialId =
                requestedMaterialId > 0
                        ? requestedMaterialId
                        : oldMaterialId;

        /*
         * Empty only the CURRENT zero-based quickswap position.
         */
        updateCurrentQuickSlot(
                player,
                0);

        player.setWidgetId(0);
        player.save();

		session.send(
				new PacketWidgetSlotChangeNotify(
						WidgetSlotOp.WidgetSlotOp_DETACH));

		session.send(
				new PacketSetWidgetSlotRsp(
						responseMaterialId,
						WidgetSlotOp.WidgetSlotOp_DETACH_VALUE,
						tagList));

		sendCurrentWidgetState(session, player);
    }

    private void updateCurrentQuickSlot(
            Player player,
            int materialId) {

        List<Integer> slots =
                normalizeQuickSlots(
                        player.getWidgetQuickSlotList());

        int currentSlotNum =
                player.getWidgetQuickSlotCurrentSlotNum();

        if (currentSlotNum < 0
                || currentSlotNum >= MAX_QUICK_SLOTS) {
            currentSlotNum = 0;

            player.setWidgetQuickSlotCurrentSlotNum(0);
        }

        /*
         * Zero-based:
         * slot 0 modifies element 0,
         * slot 1 modifies element 1,
         * etc.
         */
        slots.set(
                currentSlotNum,
                materialId);

        player.setWidgetQuickSlotList(slots);
    }

    private List<Integer> normalizeQuickSlots(
            List<Integer> incoming) {

        List<Integer> slots =
                new ArrayList<>(MAX_QUICK_SLOTS);

        if (incoming != null) {
            int count =
                    Math.min(
                            incoming.size(),
                            MAX_QUICK_SLOTS);

            for (int i = 0; i < count; i++) {
                slots.add(incoming.get(i));
            }
        }

        while (slots.size() < MAX_QUICK_SLOTS) {
            slots.add(0);
        }

        return slots;
    }

	private void sendCurrentWidgetState(
			GameSession session,
			Player player) {

		session.send(
				new PacketGetWidgetSlotRsp(player));

		session.send(
				new PacketAllWidgetDataNotify(player));
	}
}