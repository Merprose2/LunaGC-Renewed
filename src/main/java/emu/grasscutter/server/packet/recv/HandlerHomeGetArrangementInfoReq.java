package emu.grasscutter.server.packet.recv;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.home.GameHome;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.HomeGetArrangementInfoReqOuterClass;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketHomeGetArrangementInfoRsp;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

@Opcodes(PacketOpcodes.HomeGetArrangementInfoReq)
public class HandlerHomeGetArrangementInfoReq extends PacketHandler {
    private static final int MAX_WIRE_DEPTH = 4;

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = HomeGetArrangementInfoReqOuterClass.HomeGetArrangementInfoReq.parseFrom(payload);
        var player = session.getPlayer();

        List<Integer> generatedSceneIds = req.getSceneIdListList();
        List<Integer> resolvedSceneIds =
                this.resolveRequestedSceneIds(player.getSceneId(), player.getCurrentRealmId(), generatedSceneIds, payload);

        Grasscutter.getLogger()
                .debug(
                        "[HomeArrangement] request uid={}, opcode={}, currentSceneId={}, "
                                + "currentRealmId={}, generatedSceneIds={}, resolvedSceneIds={}, "
                                + "payloadLength={}",
                        player.getUid(),
                        PacketOpcodes.HomeGetArrangementInfoReq,
                        player.getSceneId(),
                        player.getCurrentRealmId(),
                        generatedSceneIds,
                        resolvedSceneIds,
                        payload != null ? payload.length : 0);

        session.send(new PacketHomeGetArrangementInfoRsp(player, resolvedSceneIds));
    }

    private List<Integer> resolveRequestedSceneIds(
            int currentSceneId,
            int currentRealmId,
            List<Integer> generatedSceneIds,
            byte[] payload) {
        Set<Integer> resolved = new LinkedHashSet<>();

        if (generatedSceneIds != null) {
            generatedSceneIds.stream()
                    .filter(GameHome.HOME_SCENE_IDS::contains)
                    .forEach(resolved::add);
        }

        if (payload != null && payload.length > 0) {
            this.collectHomeSceneIds(payload, resolved, 0);
        }

        /*
         * REL6.6 compatibility fallback:
         * An empty decoded list would produce an empty arrangement response,
         * leaving the realm without its house, Tubby position, or furniture.
         */
        if (resolved.isEmpty()) {
            if (GameHome.HOME_SCENE_IDS.contains(currentSceneId)) {
                resolved.add(currentSceneId);
            }

            int outdoorSceneId = currentRealmId > 0 ? currentRealmId + 2000 : 0;
            if (GameHome.HOME_SCENE_IDS.contains(outdoorSceneId)) {
                resolved.add(outdoorSceneId);
            }
        }

        return new ArrayList<>(resolved);
    }

    private void collectHomeSceneIds(byte[] data, Set<Integer> output, int depth) {
        if (data == null || data.length == 0 || depth > MAX_WIRE_DEPTH) {
            return;
        }

        try {
            CodedInputStream input = CodedInputStream.newInstance(data);

            while (!input.isAtEnd()) {
                int tag = input.readTag();

                if (tag == 0) {
                    break;
                }

                int wireType = WireFormat.getTagWireType(tag);

                switch (wireType) {
                    case WireFormat.WIRETYPE_VARINT -> {
                        long value = input.readUInt64();
                        if (value <= Integer.MAX_VALUE
                                && GameHome.HOME_SCENE_IDS.contains((int) value)) {
                            output.add((int) value);
                        }
                    }

                    case WireFormat.WIRETYPE_LENGTH_DELIMITED -> {
                        byte[] nested = input.readByteArray();
                        this.collectPackedHomeSceneIds(nested, output);
                        this.collectHomeSceneIds(nested, output, depth + 1);
                    }

                    default -> input.skipField(tag);
                }
            }
        } catch (Exception ignored) {
            // Diagnostic compatibility parser: malformed candidates are ignored.
        }
    }

    private void collectPackedHomeSceneIds(byte[] data, Set<Integer> output) {
        try {
            CodedInputStream packed = CodedInputStream.newInstance(data);

            while (!packed.isAtEnd()) {
                int value = packed.readUInt32();
                if (GameHome.HOME_SCENE_IDS.contains(value)) {
                    output.add(value);
                }
            }
        } catch (Exception ignored) {
            // The length-delimited field may be a nested message instead.
        }
    }
}
