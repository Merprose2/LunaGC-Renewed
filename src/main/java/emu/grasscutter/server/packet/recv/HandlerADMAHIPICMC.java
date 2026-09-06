package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.stygian.StygianOnslaughtManager;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ADMAHIPICMCOuterClass.ADMAHIPICMC;
import emu.grasscutter.server.game.GameSession;

/**
 * Stygian Onslaught: the client opens the event UI (empty request). The official server answers
 * with DBFJKIEBAJJ carrying the challenge state - without it the client shows the mode as closed.
 */
@Opcodes(PacketOpcodes.ADMAHIPICMC)
public class HandlerADMAHIPICMC extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        ADMAHIPICMC.parseFrom(payload); // empty message
        StygianOnslaughtManager manager = session.getPlayer().getStygianOnslaughtManager();
        if (manager != null) {
            manager.onOpenUi();
        }
    }
}
