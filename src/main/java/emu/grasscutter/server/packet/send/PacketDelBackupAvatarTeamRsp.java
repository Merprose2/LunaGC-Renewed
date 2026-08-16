package emu.grasscutter.server.packet.send;

import com.google.protobuf.UnknownFieldSet;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.DelBackupAvatarTeamRspOuterClass.DelBackupAvatarTeamRsp;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;

public class PacketDelBackupAvatarTeamRsp extends BasePacket {

    public PacketDelBackupAvatarTeamRsp(Retcode retcode, int id) {
        super(PacketOpcodes.DelBackupAvatarTeamRsp);

        // Real REL6.6 proto:
        // DelBackupAvatarTeamRsp {
        //   uint32 backup_avatar_team_id = 8;
        //   int32 retcode = 15;
        // }
        //
        // The current generated Java class still has the wrong field numbers, so build the REL6.6-shaped packet through unknown fields.
        DelBackupAvatarTeamRsp proto =
                DelBackupAvatarTeamRsp.newBuilder()
                        .setUnknownFields(
                                UnknownFieldSet.newBuilder()
                                        .addField(8, varint(Math.max(0, id)))
                                        .addField(15, varint(retcode.getNumber()))
                                        .build())
                        .build();

        this.setData(proto);
    }

    public PacketDelBackupAvatarTeamRsp(int id) {
        this(Retcode.RET_SUCC, id);
    }

    private static UnknownFieldSet.Field varint(int value) {
        return UnknownFieldSet.Field.newBuilder().addVarint(value).build();
    }
}