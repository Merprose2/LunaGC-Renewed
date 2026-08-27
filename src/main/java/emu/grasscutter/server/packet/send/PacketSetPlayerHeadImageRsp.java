package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.SetPlayerHeadImageRspOuterClass.SetPlayerHeadImageRsp;
import emu.grasscutter.utils.ProfilePictureUtils;

public class PacketSetPlayerHeadImageRsp extends BasePacket {

    public PacketSetPlayerHeadImageRsp(Player player) {
        super(PacketOpcodes.SetPlayerHeadImageRsp);

        SetPlayerHeadImageRsp proto = SetPlayerHeadImageRsp.newBuilder().setProfilePicture(ProfilePictureUtils.buildProfilePicture(player)).setRetcode(0).build();

        this.setData(proto);
    }
}