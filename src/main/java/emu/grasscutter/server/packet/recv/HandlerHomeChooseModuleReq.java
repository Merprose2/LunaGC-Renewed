package emu.grasscutter.server.packet.recv;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.WireFormat;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.HomeChooseModuleReqOuterClass;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketHomeBasicInfoNotify;
import emu.grasscutter.server.packet.send.PacketHomeChooseModuleRsp;
import emu.grasscutter.server.packet.send.PacketHomeComfortInfoNotify;
import emu.grasscutter.server.packet.send.PacketPlayerHomeCompInfoNotify;
import java.io.IOException;

@Opcodes(PacketOpcodes.HomeChooseModuleReq)
public class HandlerHomeChooseModuleReq extends PacketHandler {
    private static final int REL66_MODULE_ID_FIELD = 3;

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = HomeChooseModuleReqOuterClass.HomeChooseModuleReq.parseFrom(payload);
        int moduleId = resolveModuleId(req, payload);

        var player = session.getPlayer();
        var moduleData = GameData.getHomeWorldModuleDataMap().get(moduleId);

        if (moduleId <= 0 || moduleData == null) {
            Grasscutter.getLogger()
                    .warn(
                            "[HomeChooseModule] Rejected invalid initial realm selection: uid={}, moduleId={}, payloadLength={}",
                            player.getUid(),
                            moduleId,
                            payload != null ? payload.length : 0);
            return;
        }

        var realmList = player.getRealmList();
        boolean alreadyUnlocked = realmList != null && realmList.contains(moduleId);

        /*
         * HomeChooseModuleReq is the first-time realm picker. Only the three
         * modules marked free in HomeworldModuleExcelConfigData are valid here.
         * Already-unlocked modules remain accepted for harmless retries.
         */
        if (!moduleData.isFree() && !alreadyUnlocked) {
            Grasscutter.getLogger()
                    .warn(
                            "[HomeChooseModule] Rejected locked realm selection: uid={}, moduleId={}",
                            player.getUid(),
                            moduleId);
            return;
        }

        player.addRealmList(moduleId);
        player.setCurrentRealmId(moduleId);

        var homeWorld = session.getServer().getHomeWorldOrCreate(player);
        homeWorld.setHost(player);
        player.setCurHomeWorld(homeWorld);
        homeWorld.refreshModuleManager();

        /*
         * realmList and currentRealmId belong to Player, while the default
         * arrangement created by the refreshed module manager belongs to Home.
         */
        player.save();
        if (player.getHome() != null) {
            player.getHome().save();
        }

        session.send(new PacketHomeChooseModuleRsp(moduleId));
        session.send(new PacketHomeBasicInfoNotify(player, false));
        session.send(new PacketPlayerHomeCompInfoNotify(player));
        session.send(new PacketHomeComfortInfoNotify(player));

        Grasscutter.getLogger()
                .info(
                        "[HomeChooseModule] Initial realm selected: uid={}, moduleId={}, sceneId={}, realmList={}",
                        player.getUid(),
                        moduleId,
                        moduleData.getWorldSceneId(),
                        player.getRealmList());
    }

    private int resolveModuleId(
            HomeChooseModuleReqOuterClass.HomeChooseModuleReq req, byte[] payload) {
        if (req.getModuleId() > 0) {
            return req.getModuleId();
        }

        var unknownField = req.getUnknownFields().getField(REL66_MODULE_ID_FIELD);
        if (unknownField != null && !unknownField.getVarintList().isEmpty()) {
            long value = unknownField.getVarintList().get(0);
            if (value > 0 && value <= Integer.MAX_VALUE) {
                return (int) value;
            }
        }

        return decodeRel66ModuleId(payload);
    }

    private int decodeRel66ModuleId(byte[] payload) {
        if (payload == null || payload.length == 0) {
            return 0;
        }

        try {
            var input = CodedInputStream.newInstance(payload);

            while (!input.isAtEnd()) {
                int tag = input.readTag();
                if (tag == 0) {
                    break;
                }

                int fieldNumber = WireFormat.getTagFieldNumber(tag);
                int wireType = WireFormat.getTagWireType(tag);

                if (fieldNumber == REL66_MODULE_ID_FIELD
                        && wireType == WireFormat.WIRETYPE_VARINT) {
                    return input.readUInt32();
                }

                if (!input.skipField(tag)) {
                    break;
                }
            }
        } catch (IOException exception) {
            Grasscutter.getLogger()
                    .warn("[HomeChooseModule] Failed to decode REL6.6 module field", exception);
        }

        return 0;
    }
}
