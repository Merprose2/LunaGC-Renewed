package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.CalcWeaponUpgradeReturnItemsReqOuterClass.CalcWeaponUpgradeReturnItemsReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketCalcWeaponUpgradeReturnItemsRsp;

@Opcodes(PacketOpcodes.CalcWeaponUpgradeReturnItemsReq)
public class HandlerCalcWeaponUpgradeReturnItemsReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        // Read through the generated proto: target_weapon_guid = 1, food_weapon_guid_list = 6 and
        // item_param_list = 12.
        var req = CalcWeaponUpgradeReturnItemsReq.parseFrom(payload);

        var returnOres =
                session
                        .getServer()
                        .getInventorySystem()
                        .calcWeaponUpgradeReturnItems(
                                session.getPlayer(),
                                req.getTargetWeaponGuid(),
                                req.getFoodWeaponGuidListList(),
                                req.getItemParamListList());

        if (returnOres != null) {
            session.send(
                    new PacketCalcWeaponUpgradeReturnItemsRsp(
                            req.getTargetWeaponGuid(),
                            returnOres));
        }
    }
}