package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.DelBackupAvatarTeamReqOuterClass.DelBackupAvatarTeamReq;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.DelBackupAvatarTeamReq)
public class HandlerDelBackupAvatarTeamReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        // Read through the generated proto: backup_avatar_team_id = 9. The hand written decoder this
        // replaced preferred field 2 and only kept 9 as a fallback, so a delete could target the
        // wrong team.
        DelBackupAvatarTeamReq req = DelBackupAvatarTeamReq.parseFrom(payload);

        int teamId = req.getBackupAvatarTeamId();

        if (teamId <= 0) {
            return;
        }

        session.getPlayer().getTeamManager().removeCustomTeam(teamId);
    }
}