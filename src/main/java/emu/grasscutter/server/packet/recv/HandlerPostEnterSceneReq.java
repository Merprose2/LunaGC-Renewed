package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PostEnterSceneReqOuterClass.PostEnterSceneReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketPlayerWorldSceneInfoListNotify;
import emu.grasscutter.server.packet.send.PacketPostEnterSceneRsp;

@Opcodes(PacketOpcodes.PostEnterSceneReq)
public class HandlerPostEnterSceneReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        PostEnterSceneReq req = PostEnterSceneReq.parseFrom(payload);

        var player = session.getPlayer();
        var scene = player.getScene();
        var questManager = player.getQuestManager();

        switch (scene.getSceneType()) {
            case SCENE_ROOM ->
                    questManager.queueEvent(
                            QuestContent.QUEST_CONTENT_ENTER_ROOM,
                            scene.getId(),
                            0);

            case SCENE_WORLD -> {
                questManager.queueEvent(QuestContent.QUEST_CONTENT_ENTER_MY_WORLD, scene.getId());
                questManager.queueEvent(QuestContent.QUEST_CONTENT_ENTER_MY_WORLD_SCENE, scene.getId());
            }

            case SCENE_DUNGEON -> {
                var dungeonManager = scene.getDungeonManager();
                if (dungeonManager != null) {
                    dungeonManager.startDungeon();
                }
            }
        }

        questManager.queueEvent(QuestContent.QUEST_CONTENT_LEAVE_SCENE, scene.getPrevScene());

        session.send(new PacketPostEnterSceneRsp(player));

        // Important for scene-tag terrain/platform states.
        // /tag add works live because it sends this notify after the scene is already loaded.
        // Re-send it here so saved scene tags are re-applied after relog / scene enter too.
        if (player.getSceneTags() != null && !player.getSceneTags().isEmpty()) {
            session.send(new PacketPlayerWorldSceneInfoListNotify(player));
        }
    }
}