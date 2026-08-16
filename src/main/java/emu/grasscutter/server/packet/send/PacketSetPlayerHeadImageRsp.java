package emu.grasscutter.server.packet.send;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.utils.ProfilePictureUtils;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class PacketSetPlayerHeadImageRsp extends BasePacket {
    /*
     * REL6.6 candidate:
     *
     * CmdID: 781
     * ProfilePicture profile_picture = 1;
     * int32 retcode = 5;
     */
    private static final int PROFILE_PICTURE_FIELD_NUMBER = 1;
    private static final int RETCODE_FIELD_NUMBER = 5;

    public PacketSetPlayerHeadImageRsp(Player player) {
        super(PacketOpcodes.SetPlayerHeadImageRsp);

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            CodedOutputStream output = CodedOutputStream.newInstance(baos);

            output.writeMessage(PROFILE_PICTURE_FIELD_NUMBER, ProfilePictureUtils.buildProfilePicture(player));
            output.writeInt32(RETCODE_FIELD_NUMBER, 0);

            output.flush();
            this.setData(baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode SetPlayerHeadImageRsp ", e);
        }
    }
}