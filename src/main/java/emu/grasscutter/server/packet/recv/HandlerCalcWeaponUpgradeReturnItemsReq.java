package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketCalcWeaponUpgradeReturnItemsRsp;
import java.util.List;

@Opcodes(PacketOpcodes.CalcWeaponUpgradeReturnItemsReq)
public class HandlerCalcWeaponUpgradeReturnItemsReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        WeaponUpgradeReqCompat req = WeaponUpgradeReqCompat.parse(payload);

        List<ItemParam> returnOres =
                session
                        .getServer()
                        .getInventorySystem()
                        .calcWeaponUpgradeReturnItems(
                                session.getPlayer(),
                                req.getTargetWeaponGuid(),
                                req.getFoodWeaponGuidList(),
                                req.getItemParamList());

        if (returnOres != null) {
            session.send(
                    new PacketCalcWeaponUpgradeReturnItemsRsp(
                            req.getTargetWeaponGuid(),
                            returnOres));
        }
    }
}