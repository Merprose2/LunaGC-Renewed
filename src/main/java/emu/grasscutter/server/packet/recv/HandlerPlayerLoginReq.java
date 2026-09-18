package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarTypeOuterClass;
import emu.grasscutter.net.proto.DoSetPlayerBornDataNotifyOuterClass.DoSetPlayerBornDataNotify;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.game.GameSession.SessionState;
import emu.grasscutter.server.packet.send.*;

@Opcodes(PacketOpcodes.PlayerLoginReq)
public class HandlerPlayerLoginReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        if (session.getAccount() == null) {
            session.close();
            return;
        }

        Player player = session.getPlayer();

        /*
         * A brand new account has no character yet. The client runs through the born flow in that
         * case: it plays the opening cutscene, lets the player pick the main character, and reports
         * that choice back through SetPlayerBornDataReq. Handing out a main character here would
         * answer that flow before the client ever reaches it, so the session is only moved into
         * PICKING_CHARACTER (the state SetPlayerBornDataReq is accepted in) and the login response is
         * sent - HandlerSetPlayerBornDataReq creates the avatar the client asked for and logs the
         * player into the world.
         */
        if (player.getAvatars().getAvatarCount() == 0) {
            session.setState(SessionState.PICKING_CHARACTER);
            session.send(new PacketPlayerLoginRsp(session));

            /*
             * Nothing else is pushed for a new account: the client is told to run the born flow
             * instead. It only knows to play the opening cutscene and ask for a name because of this
             * notify - without it the client sits on the login screen and never sends
             * SetPlayerBornDataReq.
             */
            var bornNotify = new BasePacket(PacketOpcodes.DoSetPlayerBornDataNotify);
            bornNotify.setData(DoSetPlayerBornDataNotify.newBuilder().build());
            session.send(bornNotify);
            return;
        }

        session.getPlayer().onLogin();

        session.send(new PacketPlayerLoginRsp(session));

        // The official server pushes the full activity list right after PlayerLoginRsp (sniff
        // [142]); the 7.0 client never sends GetActivityInfoReq itself, so without this push it
        // never receives activity detail data and shows event modes (Stygian Onslaught) closed.
        session.send(new PacketGetActivityInfoRsp(player.getActivityManager()));
    }
}
