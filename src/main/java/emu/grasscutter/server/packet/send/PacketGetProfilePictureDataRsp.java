package emu.grasscutter.server.packet.send;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.utils.ProfilePictureUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class PacketGetProfilePictureDataRsp extends BasePacket {
    /*
     * REL6.6 candidate:
     *
     * GetProfilePictureDataRsp-like packet:
     * CmdID: 24009
     * repeated uint32 profile_frame_id_list = 3;
     * repeated uint32 profile_picture_id_list = 7;
     * int32 retcode = 5;
     */
    private static final int PROFILE_FRAME_ID_LIST_FIELD_NUMBER = 3;
    private static final int PROFILE_PICTURE_ID_LIST_FIELD_NUMBER = 7;
    private static final int RETCODE_FIELD_NUMBER = 5;

    public PacketGetProfilePictureDataRsp(Player player) {
        super(PacketOpcodes.GetProfilePictureDataRsp);

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            CodedOutputStream output = CodedOutputStream.newInstance(baos);

            // Default frame from ProfileFrameExcelConfigData.json.
            output.writeUInt32(PROFILE_FRAME_ID_LIST_FIELD_NUMBER, 100000);

            for (int profilePictureId : ProfilePictureUtils.getUnlockedProfilePictureIds(player)) {
                output.writeUInt32(PROFILE_PICTURE_ID_LIST_FIELD_NUMBER, profilePictureId);
            }

            output.writeInt32(RETCODE_FIELD_NUMBER, 0);
            output.flush();

            this.setData(baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode GetProfilePictureDataRsp for REL6.6", e);
        }
    }
}