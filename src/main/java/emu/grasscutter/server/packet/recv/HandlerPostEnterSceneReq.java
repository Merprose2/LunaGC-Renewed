package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.quest.enums.QuestContent;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.PostEnterSceneReqOuterClass.PostEnterSceneReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketGetWidgetSlotRsp;
import emu.grasscutter.server.packet.send.PacketPlayerWorldSceneInfoListNotify;
import emu.grasscutter.server.packet.send.PacketPostEnterSceneRsp;
import emu.grasscutter.server.packet.send.PacketWidgetSlotChangeNotify;
import emu.grasscutter.net.proto.WidgetSlotOpOuterClass.WidgetSlotOp;
import emu.grasscutter.server.packet.send.PacketAllWidgetDataNotify;

@Opcodes(PacketOpcodes.PostEnterSceneReq)
public class HandlerPostEnterSceneReq extends PacketHandler {

    @Override
    public void handle(
            GameSession session,
            byte[] header,
            byte[] payload)
            throws Exception {

        PostEnterSceneReq req =
                PostEnterSceneReq.parseFrom(payload);

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
                questManager.queueEvent(
                        QuestContent.QUEST_CONTENT_ENTER_MY_WORLD,
                        scene.getId());

                questManager.queueEvent(
                        QuestContent.QUEST_CONTENT_ENTER_MY_WORLD_SCENE,
                        scene.getId());
            }

            case SCENE_DUNGEON -> {
                var dungeonManager =
                        scene.getDungeonManager();

                if (dungeonManager != null) {
                    dungeonManager.startDungeon();
                }
            }
        }

        questManager.queueEvent(
                QuestContent.QUEST_CONTENT_LEAVE_SCENE,
                scene.getPrevScene());

        /*
         * Finish the client's scene-entry process first.
         */
        session.send(
                new PacketPostEnterSceneRsp(player));

        /*
         * Stygian Onslaught: the arena gallery packets are pushed once the
         * client finished loading (official sends them around PostEnterScene).
         */
        if (player.getStygianOnslaughtManager() != null) {
            player.getStygianOnslaughtManager().onPostEnterScene();
        }

        /*
         * Important for persisted scene-tag terrain/platform states.
         */
        if (player.getSceneTags() != null
                && !player.getSceneTags().isEmpty()) {

            session.send(
                    new PacketPlayerWorldSceneInfoListNotify(
                            player));
        }

        /*
         * REL7.0:
         *
         * The widget ID survives the database round trip correctly,
         * but the active HUD widget state sent during Player.onLogin()
         * is lost while the client finishes loading the scene.
         *
         * Re-apply the active quick-use slot only after PostEnterScene
         * has completed.
         */
        restoreActiveWidget(
                session,
                player);
    }

	private void restoreActiveWidget(
			GameSession session,
			Player player) {

		int materialId =
				player.getWidgetId();

		if (materialId <= 0) {
			return;
		}

		/*
		 * Replay the same client-visible quick-use transition used by
		 * the working inventory-equip and Quick Swap SET paths.
		 *
		 * Do not modify Player.widgetId or save the player here.
		 * Persistence is already correct.
		 */
		session.send(
				new PacketWidgetSlotChangeNotify(
						WidgetSlotOp.WidgetSlotOp_DETACH));

		session.send(
				new PacketWidgetSlotChangeNotify(
						materialId));

		/*
		 * Follow the transition with the same authoritative state
		 * snapshots used by the working handlers.
		 */
		session.send(
				new PacketGetWidgetSlotRsp(
						player));

		session.send(
				new PacketAllWidgetDataNotify(
						player));
	}
}