package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.PlayerQuitDungeonReq)
public class HandlerPlayerQuitDungeonReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        // reset the Stygian Onslaught state if the player quits an arena instance
        var player = session.getPlayer();
        if (player.getStygianOnslaughtManager() != null) {
            player.getStygianOnslaughtManager().reset();
        }
        session.getPlayer().getServer().getDungeonSystem().exitDungeon(session.getPlayer());
        session.getPlayer().sendPacket(new BasePacket(PacketOpcodes.PlayerQuitDungeonRsp));
    }
}
