package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.stygian.StygianOnslaughtManager;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.CAACHEGPEJDOuterClass.CAACHEGPEJD;
import emu.grasscutter.server.game.GameSession;
import java.util.ArrayList;
import java.util.List;

/** Stygian Onslaught: the client confirms the team for the given round. */
@Opcodes(PacketOpcodes.CAACHEGPEJD)
public class HandlerCAACHEGPEJD extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        CAACHEGPEJD req = CAACHEGPEJD.parseFrom(payload);
        StygianOnslaughtManager manager = session.getPlayer().getStygianOnslaughtManager();
        if (manager == null) {
            return;
        }

        List<Integer> avatarIds = new ArrayList<>();
        if (req.hasKIHGJENFAIO()) {
            req.getKIHGJENFAIO().getAvatarListList().forEach(a -> avatarIds.add(a.getAvatarId()));
        }
        // fall back to the NKDNGHJJJOF list when no explicit team was sent
        if (avatarIds.isEmpty()) {
            req.getNKDNGHJJJOFList().forEach(id -> avatarIds.add(id.intValue()));
        }

        manager.onConfirmRound(req.getAAKAOLKKNGC(), avatarIds);
    }
}
