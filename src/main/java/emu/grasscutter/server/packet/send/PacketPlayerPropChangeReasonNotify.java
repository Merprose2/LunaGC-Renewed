package emu.grasscutter.server.packet.send;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.PlayerProperty;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass.PropChangeReason;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class PacketPlayerPropChangeReasonNotify extends BasePacket {

    public PacketPlayerPropChangeReasonNotify(
            Player player,
            PlayerProperty prop,
            int oldValue,
            int newValue,
            PropChangeReason changeReason) {
        super(PacketOpcodes.PlayerPropChangeReasonNotify);

        this.buildHeader(0);

        try {
            var stream = new ByteArrayOutputStream();
            var output = CodedOutputStream.newInstance(stream);

            output.writeFloat(6, newValue);
            output.writeEnum(12, changeReason.getNumber());
            output.writeUInt32(7, prop.getId());
            output.writeFloat(1, oldValue);

            output.flush();

            this.setData(stream.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException(
                    "Failed to encode PlayerPropChangeReasonNotify", e);
        }
    }
}
