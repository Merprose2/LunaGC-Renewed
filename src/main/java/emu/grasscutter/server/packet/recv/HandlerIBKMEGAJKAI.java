package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.stygian.StygianOnslaughtManager;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.IBKMEGAJKAIOuterClass.IBKMEGAJKAI;
import emu.grasscutter.server.game.GameSession;

/** Stygian Onslaught: the client gives up the current battle (gallery CLIENT_INTERRUPT). */
@Opcodes(PacketOpcodes.IBKMEGAJKAI)
public class HandlerIBKMEGAJKAI extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        IBKMEGAJKAI.parseFrom(payload); // empty message
        StygianOnslaughtManager manager = session.getPlayer().getStygianOnslaughtManager();
        if (manager != null) {
            manager.onGiveUp();
        }
    }
}
