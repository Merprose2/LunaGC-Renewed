package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.GetProfilePictureDataRspOuterClass.GetProfilePictureDataRsp;
import emu.grasscutter.utils.ProfilePictureUtils;

public class PacketGetProfilePictureDataRsp extends BasePacket {

    public PacketGetProfilePictureDataRsp(Player player) {
        super(PacketOpcodes.GetProfilePictureDataRsp);

        // Generated proto: special_profile_picture_list = 4 (repeated uint32) and retcode = 11.
        // The profile pictures the player owns are what this response carries; the two lists the
        // class used to hand write on fields 3 and 7 do not exist in this version of the message.
        this.setData(
                GetProfilePictureDataRsp.newBuilder()
                        .addAllSpecialProfilePictureList(
                                ProfilePictureUtils.getUnlockedProfilePictureIds(player))
                        .setRetcode(0)
                        .build());
    }
}