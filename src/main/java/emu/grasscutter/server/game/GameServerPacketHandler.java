package emu.grasscutter.server.game;

import static emu.grasscutter.config.Configuration.GAME_INFO;
import static emu.grasscutter.config.Configuration.SERVER;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.Grasscutter.ServerDebugMode;
import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import emu.grasscutter.game.home.GameHome;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.event.game.ReceivePacketEvent;
import emu.grasscutter.server.game.GameSession.SessionState;
import emu.grasscutter.utils.Utils;
import it.unimi.dsi.fastutil.ints.*;
import java.util.StringJoiner;
public final class GameServerPacketHandler {

    private final Int2ObjectMap<PacketHandler> handlers;

    public GameServerPacketHandler(Class<? extends PacketHandler> handlerClass) {
        this.handlers = new Int2ObjectOpenHashMap<>();

        this.registerHandlers(handlerClass);
    }

    public void registerPacketHandler(Class<? extends PacketHandler> handlerClass) {
        try {
            var opcode = handlerClass.getAnnotation(Opcodes.class);
            if (opcode == null || opcode.disabled() || opcode.value() <= 0) {
                return;
            }

            var packetHandler = handlerClass.getDeclaredConstructor().newInstance();
            this.handlers.put(opcode.value(), packetHandler);
        } catch (Exception e) {
            Grasscutter.getLogger()
                    .warn("Unable to register handler {}.", handlerClass.getSimpleName(), e);
        }
    }

    public void registerHandlers(Class<? extends PacketHandler> handlerClass) {
        var handlerClasses = Grasscutter.reflector.getSubTypesOf(handlerClass);
        for (var obj : handlerClasses) {
            this.registerPacketHandler(obj);
        }

        Grasscutter.getLogger()
                .debug("Registered " + this.handlers.size() + " " + handlerClass.getSimpleName() + "s");
    }

    public void handle(GameSession session, int opcode, byte[] header, byte[] payload) {
        PacketHandler handler = this.handlers.get(opcode);

        if (handler != null) {
            try {

                SessionState state = session.getState();

                if (opcode == PacketOpcodes.PingReq) {

                } else if (opcode == PacketOpcodes.GetPlayerTokenReq) {
                    if (state != SessionState.WAITING_FOR_TOKEN) {
                        return;
                    }
                } else if (state == SessionState.ACCOUNT_BANNED) {
                    session.close();
                    return;
                } else if (opcode == PacketOpcodes.PlayerLoginReq) {
                    if (state != SessionState.WAITING_FOR_LOGIN) {
                        return;
                    }
                } else if (opcode == PacketOpcodes.SetPlayerBornDataReq) {
                    if (state != SessionState.PICKING_CHARACTER) {
                        return;
                    }
                } else {
                    if (state != SessionState.ACTIVE) {
                        return;
                    }
                }

                ReceivePacketEvent event = new ReceivePacketEvent(session, opcode, payload);
                event.call();
                if (!event.isCanceled())
                handler.handle(session, header, event.getPacketData());
            } catch (Exception ex) {
                Grasscutter.getLogger()
                        .error(
                                "Error while handling packet opcode {} ({})",
                                opcode,
                                PacketOpcodesUtils.getOpcodeName(opcode),
                                ex);
            }
            return;
        }

        /*
         * Debug-only REL6.6 Home handshake probe.
         *
         * Several Home request opcodes are still placeholders. Some important
         * requests, especially scene-init acknowledgements, may have an empty
         * payload, so this intentionally accepts zero-length packets.
         */
        probeUnhandledHomePacket(session, opcode, header, payload);
    }

    private static void probeUnhandledHomePacket(
            GameSession session, int opcode, byte[] header, byte[] payload) {
        if (session == null
                || session.getState() != SessionState.ACTIVE
                || session.getPlayer() == null
                || !Grasscutter.getLogger().isDebugEnabled()) {
            return;
        }

        var player = session.getPlayer();
        int currentSceneId = player.getSceneId();
        int previousSceneId = player.getPrevScene();

        boolean currentIsHome = GameHome.HOME_SCENE_IDS.contains(currentSceneId);
        boolean previousWasHome = GameHome.HOME_SCENE_IDS.contains(previousSceneId);

        if (!currentIsHome && !previousWasHome) {
            return;
        }

        int payloadLength = payload != null ? payload.length : 0;

        /*
         * Home transition/control packets are normally tiny. Avoid logging
         * unrelated large traffic while still allowing empty init packets.
         */
        if (payloadLength > 128) {
            return;
        }

        Grasscutter.getLogger()
                .debug(
                        "[HomeHandshakeProbe] unhandled packet: uid={}, opcode={}, opcodeName={}, "
                                + "currentSceneId={}, previousSceneId={}, worldClass={}, "
                                + "currentRealmId={}, realmList={}, headerLength={}, payloadLength={}, "
                                + "wireFields={}, payloadHex={}",
                        player.getUid(),
                        opcode & 0xFFFF,
                        PacketOpcodesUtils.getOpcodeName(opcode),
                        currentSceneId,
                        previousSceneId,
                        player.getWorld() != null
                                ? player.getWorld().getClass().getSimpleName()
                                : "null",
                        player.getCurrentRealmId(),
                        player.getRealmList(),
                        header != null ? header.length : 0,
                        payloadLength,
                        describeWireFields(payload),
                        payload != null ? Utils.bytesToHex(payload) : "");
    }

    private static String describeWireFields(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return "[]";
        }

        StringJoiner fields = new StringJoiner(", ", "[", "]");

        try {
            CodedInputStream input = CodedInputStream.newInstance(payload);

            while (!input.isAtEnd()) {
                int tag = input.readTag();

                if (tag == 0) {
                    break;
                }

                int fieldNumber = WireFormat.getTagFieldNumber(tag);
                int wireType = WireFormat.getTagWireType(tag);

                switch (wireType) {
                    case WireFormat.WIRETYPE_VARINT ->
                            fields.add(
                                    "f"
                                            + fieldNumber
                                            + "=varint:"
                                            + Long.toUnsignedString(input.readUInt64()));

                    case WireFormat.WIRETYPE_FIXED64 ->
                            fields.add(
                                    "f"
                                            + fieldNumber
                                            + "=fixed64:"
                                            + Long.toUnsignedString(input.readFixed64()));

                    case WireFormat.WIRETYPE_LENGTH_DELIMITED -> {
                        byte[] value = input.readByteArray();
                        fields.add(
                                "f"
                                        + fieldNumber
                                        + "=bytes("
                                        + value.length
                                        + "):"
                                        + Utils.bytesToHex(value));
                    }

                    case WireFormat.WIRETYPE_FIXED32 ->
                            fields.add(
                                    "f"
                                            + fieldNumber
                                            + "=fixed32:"
                                            + Integer.toUnsignedString(input.readFixed32()));

                    default -> {
                        fields.add("f" + fieldNumber + "=wireType:" + wireType);
                        input.skipField(tag);
                    }
                }
            }
        } catch (Exception exception) {
            fields.add("decodeError:" + exception.getClass().getSimpleName());
        }

        return fields.toString();
    }

    private static boolean shouldDump(GameSession session, int opcode) {
        if (PacketOpcodes.BANNED_PACKETS.contains(opcode)) return false;
        return switch (GAME_INFO.logPackets) {
            case ALL -> !PacketOpcodesUtils.LOOP_PACKETS.contains(opcode) || GAME_INFO.isShowLoopPackets;
            case WHITELIST -> SERVER.debugWhitelist.contains(opcode);
            case BLACKLIST -> !SERVER.debugBlacklist.contains(opcode);
            default -> false;
        };
    }

}
