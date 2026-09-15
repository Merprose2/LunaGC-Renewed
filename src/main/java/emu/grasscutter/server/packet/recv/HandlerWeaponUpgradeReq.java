package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.WeaponUpgradeReqOuterClass.WeaponUpgradeReq;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.WeaponUpgradeReq)
public class HandlerWeaponUpgradeReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        // Read through the generated proto: target_weapon_guid = 6, food_weapon_guid_list = 2 and
        // item_param_list = 8. The compatibility reader this replaced guessed at three candidates for
        // each of those numbers, none of which match the schema.
        var req = WeaponUpgradeReq.parseFrom(payload);

        session
                .getServer()
                .getInventorySystem()
                .upgradeWeapon(
                        session.getPlayer(),
                        req.getTargetWeaponGuid(),
                        req.getFoodWeaponGuidListList(),
                        req.getItemParamListList());
    }
}