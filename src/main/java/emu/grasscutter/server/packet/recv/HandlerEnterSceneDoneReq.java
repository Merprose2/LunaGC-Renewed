package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.player.Player.SceneLoadState;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ChangeHpDebtsReasonOuterClass;
import emu.grasscutter.net.proto.EnterSceneDoneReqOuterClass.EnterSceneDoneReq;
import emu.grasscutter.net.proto.PropChangeReasonOuterClass;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.*;

@Opcodes(PacketOpcodes.EnterSceneDoneReq)
public class HandlerEnterSceneDoneReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        EnterSceneDoneReq req = EnterSceneDoneReq.parseFrom(payload);

        var player = session.getPlayer();

        // Finished loading
        player.setSceneLoadState(SceneLoadState.LOADED);

        // Done
        session.send(new PacketPlayerTimeNotify(player)); // Probably not the right place

        // Spawn player in world
        player.getScene().spawnPlayer(player);

        // Spawn other entities already in world
        player.getScene().showOtherEntities(player);

        // Spawn Stygian Leyline Gadget if entering Scene 3 (Overworld)
        var scene = player.getScene();
        if (scene != null && scene.getId() == 3) {
            boolean alreadySpawned = scene.getEntities().values().stream()
                    .filter(e -> e instanceof EntityGadget)
                    .map(e -> (EntityGadget) e)
                    .anyMatch(g -> g.getGadgetId() == 73051004);

            if (!alreadySpawned) {
                var pos = new Position(980.627f, 260.567f, -194.518f);
                var rot = new Position(0f, 200.0f, 0f);

                var entity = new EntityGadget(scene, 73051004, pos, rot);
                entity.setGroupId(133105039);
                entity.setConfigId(39001);
                entity.setState(201); // 201 = Active Leyline Challenge State

                scene.addEntity(entity);
            }
        }

        // Locations
        session.send(new PacketWorldPlayerLocationNotify(player.getWorld()));
        session.send(new PacketScenePlayerLocationNotify(player.getScene()));
        session.send(new PacketWorldPlayerRTTNotify(player.getWorld()));

        var avatarEntity = player.getTeamManager().getCurrentAvatarEntity();
        float currentHpDebts = avatarEntity.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP_DEBTS);
        if (currentHpDebts > 0.0f) {
            avatarEntity.getWorld().broadcastPacket(new PacketEntityFightPropChangeReasonNotify(
                    avatarEntity,
                    FightProperty.FIGHT_PROP_CUR_HP_DEBTS,
                    currentHpDebts,
                    PropChangeReasonOuterClass.PropChangeReason.PropChangeReason_PROP_CHANGE_NONE,
                    ChangeHpDebtsReasonOuterClass.ChangeHpDebtsReason.CHANGE_HP_DEBTS_REASON_CHANGE_HP_DEBTS_NONE));
        }

        // spawn NPC
        player.getScene().loadNpcForPlayerEnter(player);

        // notify client to load the npc for quest
        var questGroupSuites = player.getQuestManager().getSceneGroupSuite(player.getSceneId());
        player.getScene().loadGroupForQuest(questGroupSuites);

        Grasscutter.getLogger().trace(
                "Loaded Scene {} Quest(s) Groupsuite(s): {}",
                player.getSceneId(),
                questGroupSuites);

        session.send(new PacketGroupSuiteNotify(questGroupSuites));

        /*
         * Daily commissions use dynamic Lua groups.
         *
         * Unlike normal overworld groups, these are intentionally excluded from
         * Scene.checkGroups() and therefore have to be explicitly activated while
         * their corresponding daily tasks are active.
         *
         * Use the world owner's commissions so multiplayer visitors see the host's
         * active daily encounters as well.
         */
        var worldOwner = player.getWorld().getHost();
        if (worldOwner != null && worldOwner.getDailyTaskManager() != null) {
            worldOwner.getDailyTaskManager().updateActiveGroups(player.getScene());
        }

        // Reset timer for sending player locations
        player.resetSendPlayerLocTime();

        // Rsp
        session.send(new PacketEnterSceneDoneRsp(player));
    }
}