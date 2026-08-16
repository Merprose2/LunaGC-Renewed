package emu.grasscutter.server.packet.recv;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.EntityVehicle;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.WidgetCreateLocationInfoOuterClass.WidgetCreateLocationInfo;
import emu.grasscutter.net.proto.WidgetDoBagReqOuterClass.WidgetDoBagReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketWidgetCoolDownNotify;
import emu.grasscutter.server.packet.send.PacketWidgetDoBagRsp;
import emu.grasscutter.server.packet.send.PacketWidgetGadgetDataNotify;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Opcodes(PacketOpcodes.WidgetDoBagReq)
public class HandlerWidgetDoBagReq extends PacketHandler {
    private static final int SERENITEA_POT_MATERIAL_ID = 220026;
    private static final int SERENITEA_POT_GADGET_ID = 70500025;

    private static final int FIREWORKS_LAUNCH_TUBE_MATERIAL_ID = 220047;
    private static final int FIREWORKS_LAUNCH_TUBE_GADGET_ID = 70800058;

    private static final int SERENITEA_POT_COOLDOWN_GROUP_ID = 15;
    private static final long SERENITEA_POT_COOLDOWN_MS = 5000L;

    private static final int MAX_WIRE_RECURSION_DEPTH = 4;
    private static final int MAX_NESTED_MESSAGE_LENGTH = 256;
    private static final double MAX_WIDGET_PLACEMENT_DISTANCE = 40.0;

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        WidgetDoBagReq req = WidgetDoBagReq.parseFrom(payload);
        var player = session.getPlayer();

        int materialId = this.resolveMaterialId(player.getWidgetId(), req, payload);
        Placement placement = this.resolvePlacement(player.getPosition(), player.getRotation(), req, payload);

        Grasscutter.getLogger()
                .debug(
                        "[HomeWidget] WidgetDoBagReq decoded: uid={}, generatedMaterialId={}, "
                                + "equippedMaterialId={}, resolvedMaterialId={}, opInfoCase={}, "
                                + "positionSource={}, pos={}, rot={}, payloadLength={}",
                        player.getUid(),
                        req.getMaterialId(),
                        player.getWidgetId(),
                        materialId,
                        req.getOpInfoCase(),
                        placement.source,
                        placement.position,
                        placement.rotation,
                        payload.length);

        switch (materialId) {
            case SERENITEA_POT_MATERIAL_ID -> {
                EntityGadget pot =
                        this.spawnGadget(
                                session,
                                SERENITEA_POT_GADGET_ID,
                                placement.position,
                                placement.rotation);

                if (pot != null) {
                    session.send(
                            new PacketWidgetCoolDownNotify(
                                    SERENITEA_POT_COOLDOWN_GROUP_ID,
                                    System.currentTimeMillis() + SERENITEA_POT_COOLDOWN_MS,
                                    true));
                }
            }
            case FIREWORKS_LAUNCH_TUBE_MATERIAL_ID ->
                    this.spawnVehicle(
                            session,
                            FIREWORKS_LAUNCH_TUBE_GADGET_ID,
                            placement.position,
                            placement.rotation);
            default ->
                    Grasscutter.getLogger()
                            .warn(
                                    "[HomeWidget] Unhandled WidgetDoBagReq material: "
                                            + "uid={}, resolvedMaterialId={}, generatedMaterialId={}, "
                                            + "equippedMaterialId={}, wire={}",
                                    player.getUid(),
                                    materialId,
                                    req.getMaterialId(),
                                    player.getWidgetId(),
                                    this.describeWire(payload));
        }

        session.send(new PacketWidgetDoBagRsp(materialId));
    }

    private int resolveMaterialId(int equippedMaterialId, WidgetDoBagReq req, byte[] payload) {
        if (req.getMaterialId() != 0) {
            return req.getMaterialId();
        }

        int wireMaterialId = this.findKnownMaterialId(payload, 0);
        if (wireMaterialId != 0) {
            return wireMaterialId;
        }

        /*
         * REL6.6 sends material_id under a different field number than the generated
         * WidgetDoBagReq class expects. The equipped quick-use slot is therefore the
         * authoritative fallback for this request.
         */
        return equippedMaterialId;
    }

    private Placement resolvePlacement(
            Position playerPosition,
            Position playerRotation,
            WidgetDoBagReq req,
            byte[] payload) {
        WidgetCreateLocationInfo generatedLocation = this.getGeneratedLocationInfo(req);

        if (generatedLocation != null && generatedLocation.hasPos()) {
            Position pos = new Position(generatedLocation.getPos());
            Position rot =
                    generatedLocation.hasRot()
                            ? new Position(generatedLocation.getRot())
                            : playerRotation.clone();
            return new Placement(pos, rot, "generated-proto");
        }

        List<VectorCandidate> candidates = new ArrayList<>();
        this.collectVectorCandidates(payload, "root", 0, candidates);

        VectorCandidate positionCandidate =
                candidates.stream()
                        .filter(VectorCandidate::hasCompletePosition)
                        .filter(candidate -> candidate.position.computeDistance(playerPosition) <= MAX_WIDGET_PLACEMENT_DISTANCE)
                        .min(Comparator.comparingDouble(candidate -> candidate.position.computeDistance(playerPosition)))
                        .orElse(null);

        if (positionCandidate != null) {
            VectorCandidate rotationCandidate =
                    candidates.stream()
                            .filter(candidate -> candidate != positionCandidate)
                            .filter(VectorCandidate::looksLikeRotation)
                            .max(Comparator.comparingInt(VectorCandidate::componentCount))
                            .orElse(null);

            Position rot =
                    rotationCandidate != null
                            ? rotationCandidate.position
                            : playerRotation.clone();

            return new Placement(
                    positionCandidate.position,
                    rot,
                    "wire:" + positionCandidate.path);
        }

        /*
         * Last-resort fallback: place the gadget two units from the player.
         * This keeps the Pot testable even before every obfuscated request field is named.
         */
        Position fallbackPos = playerPosition.clone();
        double yaw = Math.toRadians(playerRotation.getY());
        fallbackPos.addX((float) (Math.sin(yaw) * 2.0));
        fallbackPos.addZ((float) (Math.cos(yaw) * 2.0));

        Grasscutter.getLogger()
                .warn(
                        "[HomeWidget] Could not decode REL6.6 placement vectors; "
                                + "using player-relative fallback. wire={}",
                        this.describeWire(payload));

        return new Placement(fallbackPos, playerRotation.clone(), "player-relative-fallback");
    }

    private WidgetCreateLocationInfo getGeneratedLocationInfo(WidgetDoBagReq req) {
        return switch (req.getOpInfoCase()) {
            case LOCATION_INFO -> req.getLocationInfo();
            case WIDGET_CREATOR_INFO -> req.getWidgetCreatorInfo().getLocationInfo();
            case OPINFO_NOT_SET -> null;
        };
    }

    private int findKnownMaterialId(byte[] data, int depth) {
        if (data == null || data.length == 0 || depth > MAX_WIRE_RECURSION_DEPTH) {
            return 0;
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
                        if (value == SERENITEA_POT_MATERIAL_ID
                                || value == FIREWORKS_LAUNCH_TUBE_MATERIAL_ID) {
                            return (int) value;
                        }
                    }
                    case WireFormat.WIRETYPE_FIXED64 -> input.readFixed64();
                    case WireFormat.WIRETYPE_LENGTH_DELIMITED -> {
                        byte[] nested = input.readByteArray();
                        int nestedResult = this.findKnownMaterialId(nested, depth + 1);
                        if (nestedResult != 0) {
                            return nestedResult;
                        }
                    }
                    case WireFormat.WIRETYPE_FIXED32 -> input.readFixed32();
                    default -> {
                        if (!input.skipField(tag)) {
                            return 0;
                        }
                    }
                }
            }
        } catch (IOException ignored) {
            return 0;
        }

        return 0;
    }

    private void collectVectorCandidates(
            byte[] data, String path, int depth, List<VectorCandidate> output) {
        if (data == null
                || data.length == 0
                || data.length > MAX_NESTED_MESSAGE_LENGTH
                || depth > MAX_WIRE_RECURSION_DEPTH) {
            return;
        }

        Map<Integer, Integer> fixed32Fields = new LinkedHashMap<>();

        try {
            CodedInputStream input = CodedInputStream.newInstance(data);

            while (!input.isAtEnd()) {
                int tag = input.readTag();
                if (tag == 0) {
                    break;
                }

                int fieldNumber = WireFormat.getTagFieldNumber(tag);
                int wireType = WireFormat.getTagWireType(tag);

                switch (wireType) {
                    case WireFormat.WIRETYPE_VARINT -> input.readUInt64();
                    case WireFormat.WIRETYPE_FIXED64 -> input.readFixed64();
                    case WireFormat.WIRETYPE_LENGTH_DELIMITED -> {
                        byte[] nested = input.readByteArray();
                        this.collectVectorCandidates(
                                nested,
                                path + ".f" + fieldNumber,
                                depth + 1,
                                output);
                    }
                    case WireFormat.WIRETYPE_FIXED32 ->
                            fixed32Fields.put(fieldNumber, input.readFixed32());
                    default -> {
                        if (!input.skipField(tag)) {
                            return;
                        }
                    }
                }
            }
        } catch (IOException ignored) {
            return;
        }

        boolean hasX = fixed32Fields.containsKey(1);
        boolean hasY = fixed32Fields.containsKey(2);
        boolean hasZ = fixed32Fields.containsKey(3);

        if (!hasX && !hasY && !hasZ) {
            return;
        }

        float x = hasX ? Float.intBitsToFloat(fixed32Fields.get(1)) : 0.0f;
        float y = hasY ? Float.intBitsToFloat(fixed32Fields.get(2)) : 0.0f;
        float z = hasZ ? Float.intBitsToFloat(fixed32Fields.get(3)) : 0.0f;

        if (!Float.isFinite(x)
                || !Float.isFinite(y)
                || !Float.isFinite(z)
                || Math.abs(x) > 100000.0f
                || Math.abs(y) > 100000.0f
                || Math.abs(z) > 100000.0f) {
            return;
        }

        output.add(new VectorCandidate(path, new Position(x, y, z), hasX, hasY, hasZ));
    }

    private String describeWire(byte[] data) {
        List<String> descriptions = new ArrayList<>();
        this.describeWire(data, "root", 0, descriptions);
        return descriptions.toString();
    }

    private void describeWire(
            byte[] data, String path, int depth, List<String> descriptions) {
        if (data == null
                || data.length == 0
                || data.length > MAX_NESTED_MESSAGE_LENGTH
                || depth > MAX_WIRE_RECURSION_DEPTH) {
            return;
        }

        try {
            CodedInputStream input = CodedInputStream.newInstance(data);

            while (!input.isAtEnd()) {
                int tag = input.readTag();
                if (tag == 0) {
                    break;
                }

                int fieldNumber = WireFormat.getTagFieldNumber(tag);
                int wireType = WireFormat.getTagWireType(tag);
                String fieldPath = path + ".f" + fieldNumber;

                switch (wireType) {
                    case WireFormat.WIRETYPE_VARINT ->
                            descriptions.add(fieldPath + "=varint:" + input.readUInt64());
                    case WireFormat.WIRETYPE_FIXED64 ->
                            descriptions.add(fieldPath + "=fixed64:" + input.readFixed64());
                    case WireFormat.WIRETYPE_LENGTH_DELIMITED -> {
                        byte[] nested = input.readByteArray();
                        descriptions.add(fieldPath + "=bytes[" + nested.length + "]");
                        this.describeWire(nested, fieldPath, depth + 1, descriptions);
                    }
                    case WireFormat.WIRETYPE_FIXED32 -> {
                        int bits = input.readFixed32();
                        descriptions.add(
                                fieldPath
                                        + "=fixed32:"
                                        + bits
                                        + "/float:"
                                        + Float.intBitsToFloat(bits));
                    }
                    default -> {
                        descriptions.add(fieldPath + "=wireType:" + wireType);
                        if (!input.skipField(tag)) {
                            return;
                        }
                    }
                }
            }
        } catch (IOException exception) {
            descriptions.add(path + "=<decode-error:" + exception.getMessage() + ">");
        }
    }

    private EntityGadget spawnGadget(
            GameSession session, int gadgetId, Position pos, Position rot) throws Exception {
        var player = session.getPlayer();
        var scene = player.getScene();

        if (scene == null) {
            Grasscutter.getLogger()
                    .warn(
                            "[HomeWidget] Cannot spawn widget gadget because the player has no scene: "
                                    + "uid={}, gadgetId={}",
                            player.getUid(),
                            gadgetId);
            return null;
        }

        var entity = new EntityGadget(scene, gadgetId, pos, rot);
        var owner = player.getTeamManager().getCurrentAvatarEntity();
        if (owner != null) {
            entity.setOwner(owner);
        }
        entity.buildContent();

        scene.addEntity(entity);
        player.getTeamManager().getGadgets().add(entity);
        session.send(new PacketWidgetGadgetDataNotify(gadgetId, entity.getId()));

        Grasscutter.getLogger()
                .debug(
                        "[HomeWidget] Spawned Serenitea Pot: uid={}, sceneId={}, "
                                + "entityId={}, gadgetId={}, pos={}, rot={}",
                        player.getUid(),
                        scene.getId(),
                        entity.getId(),
                        gadgetId,
                        pos,
                        rot);

        return entity;
    }

    private void spawnVehicle(GameSession session, int gadgetId, Position pos, Position rot)
            throws Exception {
        var player = session.getPlayer();
        var scene = player.getScene();

        if (scene == null) {
            return;
        }

        var entity = new EntityVehicle(scene, player, gadgetId, 0, pos, rot);
        scene.addEntity(entity);
        session.send(new PacketWidgetGadgetDataNotify(gadgetId, entity.getId()));
    }


    private static final class Placement {
        private final Position position;
        private final Position rotation;
        private final String source;

        private Placement(Position position, Position rotation, String source) {
            this.position = position;
            this.rotation = rotation;
            this.source = source;
        }
    }

    private static final class VectorCandidate {
        private final String path;
        private final Position position;
        private final boolean hasX;
        private final boolean hasY;
        private final boolean hasZ;

        private VectorCandidate(
                String path, Position position, boolean hasX, boolean hasY, boolean hasZ) {
            this.path = path;
            this.position = position;
            this.hasX = hasX;
            this.hasY = hasY;
            this.hasZ = hasZ;
        }

        private int componentCount() {
            return (this.hasX ? 1 : 0) + (this.hasY ? 1 : 0) + (this.hasZ ? 1 : 0);
        }

        private boolean hasCompletePosition() {
            return this.hasX && this.hasY && this.hasZ;
        }

        private boolean looksLikeRotation() {
            return this.componentCount() > 0
                    && Math.abs(this.position.getX()) <= 720.0f
                    && Math.abs(this.position.getY()) <= 720.0f
                    && Math.abs(this.position.getZ()) <= 720.0f;
        }
    }
}
