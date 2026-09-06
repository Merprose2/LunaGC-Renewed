package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.stygian.StygianOnslaughtManager;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GNLMAGJGKPFOuterClass.GNLMAGJGKPF;
import emu.grasscutter.server.game.GameSession;

/** Stygian Onslaught: the client is ready in the arena - start the battle gallery. */
@Opcodes(PacketOpcodes.GNLMAGJGKPF)
public class HandlerGNLMAGJGKPF extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        GNLMAGJGKPF.parseFrom(payload);
        StygianOnslaughtManager manager = session.getPlayer().getStygianOnslaughtManager();
        if (manager != null) {
            manager.onBattleReady();
        }
    }
}
