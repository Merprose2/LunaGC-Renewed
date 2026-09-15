package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.DelBackupAvatarTeamRspOuterClass.DelBackupAvatarTeamRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

public class PacketDelBackupAvatarTeamRsp extends BasePacket {

    public PacketDelBackupAvatarTeamRsp(Retcode retcode, int id) {
        super(PacketOpcodes.DelBackupAvatarTeamRsp);

        // Generated proto: backup_avatar_team_id = 5 and retcode = 9. Writing the two values on the
        // numbers of an older layout (8 and 15) put them on fields this version of the message does
        // not have, so the client could not read the response at all.
        DelBackupAvatarTeamRsp proto =
                DelBackupAvatarTeamRsp.newBuilder()
                        .setBackupAvatarTeamId(Math.max(0, id))
                        .setRetcode(retcode.getNumber())
                        .build();

        this.setData(proto);
    }

    public PacketDelBackupAvatarTeamRsp(int id) {
        this(Retcode.RET_SUCC, id);
    }
}