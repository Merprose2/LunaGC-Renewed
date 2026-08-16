package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ClientLockGameTimeNotifyOuterClass.ClientLockGameTimeNotify;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.ClientLockGameTimeNotify)
public final class HandlerClientLockGameTimeNotify extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var packet = ClientLockGameTimeNotify.parseFrom(payload);

        Grasscutter.getLogger()
                .info(
                        "[ClientLockGameTimeNotify] uid={}, isLock={}",
                        session.getPlayer() != null ? session.getPlayer().getUid() : 0,
                        packet.getIsLock());

        if (session.getPlayer() != null && session.getPlayer().getWorld() != null) {
            session.getPlayer().getWorld().lockTime(packet.getIsLock());
        }
    }
}