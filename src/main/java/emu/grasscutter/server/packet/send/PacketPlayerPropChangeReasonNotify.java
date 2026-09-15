package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.PlayerPropChangeReasonNotifyOuterClass.PlayerPropChangeReasonNotify;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass.PropChangeReason;

public class PacketPlayerPropChangeReasonNotify extends BasePacket {

    public PacketPlayerPropChangeReasonNotify(
            Player player,
            PlayerProperty prop,
            int oldValue,
            int newValue,
            PropChangeReason changeReason) {
        super(PacketOpcodes.PlayerPropChangeReasonNotify);

        this.buildHeader(0);

        // Generated proto: prop_type = 3, reason = 8, cur_value = 12 and old_value = 15. The numbers
        // this class used to write (1, 6, 7 and 12) do not line up with that at all.
        this.setData(
                PlayerPropChangeReasonNotify.newBuilder()
                        .setPropType(prop.getId())
                        .setReason(changeReason)
                        .setCurValue(newValue)
                        .setOldValue(oldValue)
                        .build());
    }
}
