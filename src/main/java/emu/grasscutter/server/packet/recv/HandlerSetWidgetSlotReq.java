package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.SetWidgetSlotReqOuterClass.SetWidgetSlotReq;
import emu.grasscutter.net.proto.WidgetSlotOpOuterClass.WidgetSlotOp;
import emu.grasscutter.net.proto.WidgetSlotTagOuterClass.WidgetSlotTag;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketGetWidgetQuickSlotListRsp;
import emu.grasscutter.server.packet.send.PacketGetWidgetSlotRsp;
import emu.grasscutter.server.packet.send.PacketSetWidgetSlotRsp;
import emu.grasscutter.server.packet.send.PacketWidgetSlotChangeNotify;
import java.util.ArrayList;
import java.util.List;

@Opcodes(PacketOpcodes.SetWidgetSlotReq)
public class HandlerSetWidgetSlotReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        SetWidgetSlotReq req = SetWidgetSlotReq.parseFrom(payload);

        Player player = session.getPlayer();

        int materialId = req.getMaterialId();
        int op = req.getOpValue();

        List<Integer> tagList = new ArrayList<>(req.getTagListValueList());

        if (tagList.isEmpty()) {
            tagList.add(WidgetSlotTag.WidgetSlotTag_WIDGET_SLOT_QUICK_USE_VALUE);
        }

        int oldQuickUseId = player.getWidgetId();

        if (materialId == 0 && op == WidgetSlotOp.WidgetSlotOp_DETACH_VALUE) {
            materialId = oldQuickUseId;
        }

        if (op == WidgetSlotOp.WidgetSlotOp_DETACH_VALUE) {
            handleDetach(session, player, materialId, tagList);
            return;
        }

        handleAttach(session, player, materialId, oldQuickUseId, tagList);
    }

    private void handleDetach(
            GameSession session,
            Player player,
            int materialId,
            List<Integer> tagList) {
        int oldQuickUseId = player.getWidgetId();

        if (oldQuickUseId > 0) {
            session.send(
                    new PacketWidgetSlotChangeNotify(
                            oldQuickUseId,
                            WidgetSlotTag.WidgetSlotTag_WIDGET_SLOT_QUICK_USE_VALUE,
                            WidgetSlotOp.WidgetSlotOp_DETACH_VALUE,
                            false));
        }

        player.setWidgetId(0);
        player.save();

        session.send(
                new PacketSetWidgetSlotRsp(
                        materialId,
                        WidgetSlotOp.WidgetSlotOp_DETACH_VALUE,
                        tagList));
        sendCurrentWidgetState(session, player);
    }

    private void handleAttach(
            GameSession session,
            Player player,
            int materialId,
            int oldQuickUseId,
            List<Integer> tagList) {
        if (materialId <= 0) {
            session.send(
                    new PacketSetWidgetSlotRsp(
                            materialId,
                            WidgetSlotOp.WidgetSlotOp_ATTACH_VALUE,
                            tagList));
            sendCurrentWidgetState(session, player);
            return;
        }

        if (oldQuickUseId > 0 && oldQuickUseId != materialId) {
            session.send(
                    new PacketWidgetSlotChangeNotify(
                            oldQuickUseId,
                            WidgetSlotTag.WidgetSlotTag_WIDGET_SLOT_QUICK_USE_VALUE,
                            WidgetSlotOp.WidgetSlotOp_DETACH_VALUE,
                            false));
        }

        player.setWidgetId(materialId);
        player.save();

        session.send(
                new PacketWidgetSlotChangeNotify(
                        materialId,
                        WidgetSlotTag.WidgetSlotTag_WIDGET_SLOT_QUICK_USE_VALUE,
                        WidgetSlotOp.WidgetSlotOp_ATTACH_VALUE,
                        true));

        session.send(
                new PacketSetWidgetSlotRsp(
                        materialId,
                        WidgetSlotOp.WidgetSlotOp_ATTACH_VALUE,
                        tagList));

        sendCurrentWidgetState(session, player);
    }

    private void sendCurrentWidgetState(GameSession session, Player player) {
        session.send(new PacketGetWidgetSlotRsp(player));
        session.send(new PacketGetWidgetQuickSlotListRsp(player));
    }
}