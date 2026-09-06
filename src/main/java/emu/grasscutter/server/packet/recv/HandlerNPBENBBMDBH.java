package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.stygian.StygianOnslaughtManager;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.NPBENBBMDBHOuterClass.NPBENBBMDBH;
import emu.grasscutter.server.game.GameSession;

/** Stygian Onslaught UI data poll (official: reply NJKJLDFNPMC + DJBNNEIMEKG + OBFLIEAJIMP). */
@Opcodes(PacketOpcodes.NPBENBBMDBH)
public class HandlerNPBENBBMDBH extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        NPBENBBMDBH req = NPBENBBMDBH.parseFrom(payload);
        StygianOnslaughtManager manager = session.getPlayer().getStygianOnslaughtManager();
        if (manager != null) {
            manager.onDataQuery(req);
        }
    }
}
