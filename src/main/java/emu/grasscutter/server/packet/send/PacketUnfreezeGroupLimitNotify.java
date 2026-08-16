package emu.grasscutter.server.packet.send;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import java.io.ByteArrayOutputStream;
import java.io.IOException;

public class PacketUnfreezeGroupLimitNotify extends BasePacket {

    public PacketUnfreezeGroupLimitNotify(int pointId, int sceneId) {
        super(PacketOpcodes.UnfreezeGroupLimitNotify);
        this.setData(encodeCompatibilityPayload(pointId, sceneId));
    }

    private static byte[] encodeCompatibilityPayload(int pointId, int sceneId) {
        try (var output = new ByteArrayOutputStream()) {
            var coded = CodedOutputStream.newInstance(output);

            // Mapping used by the generated REL6.6 parser and writeTo implementation.
            coded.writeUInt32(14, sceneId);
            coded.writeUInt32(13, pointId);

            // Mapping declared by the embedded protobuf descriptor.
            coded.writeUInt32(1, sceneId);
            coded.writeUInt32(12, pointId);

            coded.flush();
            return output.toByteArray();
        } catch (IOException exception) {
            throw new IllegalStateException(
                    "Failed to encode UnfreezeGroupLimitNotify compatibility payload", exception);
        }
    }
}