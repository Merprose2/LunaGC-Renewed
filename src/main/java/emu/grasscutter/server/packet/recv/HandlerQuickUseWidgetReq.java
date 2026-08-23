package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.inventory.InventoryTab;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.QuickUseWidgetRspOuterClass.QuickUseWidgetRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.QuickUseWidgetReq)
public class HandlerQuickUseWidgetReq extends PacketHandler {

    @Override
    public void handle(
            GameSession session,
            byte[] header,
            byte[] payload)
            throws Exception {

        Player player = session.getPlayer();
        int materialId = player.getWidgetId();

        QuickUseWidgetRsp.Builder rsp =
                QuickUseWidgetRsp.newBuilder();

        /*
         * No currently active quick-use gadget.
         */
        if (materialId <= 0) {
            rsp.setRetcode(Retcode.RET_FAIL_VALUE);

            sendResponse(session, rsp.build());
            return;
        }

        InventoryTab materialTab =
                player.getInventory()
                        .getInventoryTab(ItemType.ITEM_MATERIAL);

        GameItem item =
                materialTab.getItemById(materialId);

        /*
         * The client is claiming an active gadget that the player
         * no longer possesses.
         */
        if (item == null || item.getCount() <= 0) {
            rsp.setMaterialId(materialId);
            rsp.setRetcode(
                    Retcode.RET_ITEM_NOT_EXIST_VALUE);

            sendResponse(session, rsp.build());
            return;
        }

        /*
         * Do NOT generically consume quick-use gadgets here.
         *
         * Many widgets (Kamera, NRE, compasses, etc.) are reusable.
         * Consumable widget behavior must be implemented according
         * to the particular gadget instead of blindly deleting one
         * inventory item every time Z is pressed.
         */
        rsp.setMaterialId(materialId);
        rsp.setRetcode(Retcode.RET_SUCC_VALUE);

        sendResponse(session, rsp.build());
    }

    private void sendResponse(
            GameSession session,
            QuickUseWidgetRsp proto) {

        BasePacket packet =
                new BasePacket(PacketOpcodes.QuickUseWidgetRsp);

        packet.setData(proto);
        session.send(packet);
    }
}