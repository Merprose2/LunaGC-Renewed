package emu.grasscutter.server.packet.send;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.LunchBoxDataOuterClass.LunchBoxData;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collections;

public class PacketNreLunchBoxDataNotify extends BasePacket {

    public PacketNreLunchBoxDataNotify(Player player) {
        super(PacketOpcodes.AllWidgetDataNotify);

        var slots =
                player.getLunchBoxSlotMaterialMap() == null
                        ? Collections.<Integer, Integer>emptyMap()
                        : player.getLunchBoxSlotMaterialMap();

        var lunchBoxData =
                LunchBoxData.newBuilder()
                        .putAllSlotMaterialMap(slots)
                        .build();

        try {
            var buffer = new ByteArrayOutputStream();
            var output = CodedOutputStream.newInstance(buffer);

            // REL6.6 AllWidgetDataNotify.lunch_box_data = 12.
            output.writeByteArray(12, lunchBoxData.toByteArray());
            output.flush();

            setData(buffer.toByteArray());
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to encode NRE AllWidgetDataNotify",
                    exception);
        }
    }
}