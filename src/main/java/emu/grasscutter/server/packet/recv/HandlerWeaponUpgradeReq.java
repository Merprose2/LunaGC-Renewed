package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.WeaponUpgradeReq)
public class HandlerWeaponUpgradeReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        WeaponUpgradeReqCompat req = WeaponUpgradeReqCompat.parse(payload);

        session
                .getServer()
                .getInventorySystem()
                .upgradeWeapon(
                        session.getPlayer(),
                        req.getTargetWeaponGuid(),
                        req.getFoodWeaponGuidList(),
                        req.getItemParamList());
    }
}