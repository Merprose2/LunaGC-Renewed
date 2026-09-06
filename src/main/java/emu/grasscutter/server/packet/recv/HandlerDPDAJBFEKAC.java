package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.stygian.StygianOnslaughtManager;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.DPDAJBFEKACOuterClass.DPDAJBFEKAC;
import emu.grasscutter.server.game.GameSession;

/** Stygian Onslaught: the client starts the challenge with the chosen difficulty. */
@Opcodes(PacketOpcodes.DPDAJBFEKAC)
public class HandlerDPDAJBFEKAC extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        DPDAJBFEKAC req = DPDAJBFEKAC.parseFrom(payload);
        StygianOnslaughtManager manager = session.getPlayer().getStygianOnslaughtManager();
        if (manager != null) {
            manager.onStartChallenge(req.getDifficulty());
        }
    }
}
