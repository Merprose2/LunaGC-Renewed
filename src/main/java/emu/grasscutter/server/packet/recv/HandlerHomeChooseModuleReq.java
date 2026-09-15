package emu.grasscutter.server.packet.recv;

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

@Opcodes(PacketOpcodes.HomeChooseModuleReq)
public class HandlerHomeChooseModuleReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        // Read through the generated proto: module_id = 15. The REL6.6 number this handler used (3) is
        // a different field, so the realm picker only worked through its payload scan fallback.
        var req = HomeChooseModuleReqOuterClass.HomeChooseModuleReq.parseFrom(payload);
        int moduleId = req.getModuleId();

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
}
