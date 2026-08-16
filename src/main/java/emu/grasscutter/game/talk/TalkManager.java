package emu.grasscutter.game.talk;

import static emu.grasscutter.game.quest.enums.QuestCond.QUEST_COND_COMPLETE_TALK;
import static emu.grasscutter.game.quest.enums.QuestContent.*;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.binout.MainQuestData.TalkData;
import emu.grasscutter.data.excels.TalkConfigData.TalkExecParam;
import emu.grasscutter.game.entity.EntityGadget;
import emu.grasscutter.game.entity.GameEntity;
import emu.grasscutter.game.entity.EntityMonster;
import emu.grasscutter.game.props.FightProperty;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType;
import emu.grasscutter.game.player.*;
import emu.grasscutter.server.event.player.PlayerNpcTalkEvent;
import lombok.NonNull;
import java.util.Arrays;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public final class TalkManager extends BasePlayerManager {
    public TalkManager(@NonNull Player player) {
        super(player);
    }

    /**
     * Invoked when a talk is triggered.
     *
     * @param talkId The ID of the talk.
     * @param npcEntityId The entity ID of the NPC being talked to.
     */
    public void triggerTalkAction(int talkId, int npcEntityId) {
        var player = this.getPlayer();

		var talkData = GameData.getTalkConfigDataMap().get(talkId);

		Grasscutter.getLogger()
				.warn(
						"[TalkManager] triggerTalkAction: talkId={}, npcEntityId={}, talkDataFound={}",
						talkId,
						npcEntityId,
						talkData != null);

		// Invoke PlayerNpcTalkEvent.
		var event = new PlayerNpcTalkEvent(player, talkData, talkId, npcEntityId);
		if (!event.call()) return;

		if (talkData != null) {
			Grasscutter.getLogger()
					.warn(
							"[TalkManager] talkData: talkId={}, questId={}, npcIds={}, finishExecCount={}",
							talkData.getId(),
							talkData.getQuestId(),
							talkData.getNpcId(),
							talkData.getFinishExec() != null ? talkData.getFinishExec().size() : 0);

			// Check if the NPC id is valid.
			var entity = player.getScene().getEntityById(npcEntityId);

			Grasscutter.getLogger()
					.warn(
							"[TalkManager] npc entity lookup: npcEntityId={}, entityClass={}, configId={}, groupId={}, pos={}",
							npcEntityId,
							entity != null ? entity.getClass().getSimpleName() : "null",
							entity != null ? entity.getConfigId() : 0,
							entity != null ? entity.getGroupId() : 0,
							entity != null ? entity.getPosition() : null);

			if (entity != null) {
				var npcIds = talkData.getNpcId();

				// Some newer scripted interactions, such as Icewind Suite's Maillardet gadget, have an empty npcId list. 
				// Treat an empty list as "skip NPC config validation" instead of "reject every entity".
				if (npcIds != null && !npcIds.isEmpty() && !npcIds.contains(entity.getConfigId())) {
					Grasscutter.getLogger()
							.warn(
									"[TalkManager] NPC config mismatch: talkId={}, expectedNpcIds={}, actualConfigId={}, entityId={}",
									talkId,
									npcIds,
									entity.getConfigId(),
									npcEntityId);
					return;
				}

				if (npcIds == null || npcIds.isEmpty()) {
					Grasscutter.getLogger()
							.warn(
									"[TalkManager] NPC validation skipped because talkData.npcIds is empty: talkId={}, entityClass={}, configId={}, groupId={}, entityId={}",
									talkId,
									entity.getClass().getSimpleName(),
									entity.getConfigId(),
									entity.getGroupId(),
									npcEntityId);
				}
			}

			// Execute the talk action on associated handlers.
			talkData
					.getFinishExec()
					.forEach(
							e -> {
								Grasscutter.getLogger()
										.warn(
												"[TalkManager] finishExec: talkId={}, type={}({}), params={}",
												talkId,
												e.getType(),
												e.getType() != null ? e.getType().getValue() : -1,
												Arrays.toString(e.getParam()));

								if (this.handleInlineTalkExec(talkId, entity, e)) {
									return;
								}

								player.getServer().getTalkSystem().triggerExec(player, talkData, e);
							});

			// Save the talk value to the quest's data.
			this.saveTalkToQuest(talkId, talkData.getQuestId());
		}

		// Invoke the talking events for quests.
		var questManager = player.getQuestManager();
		questManager.queueEvent(QUEST_CONTENT_COMPLETE_ANY_TALK, talkId);
		questManager.queueEvent(QUEST_CONTENT_COMPLETE_TALK, talkId);
		questManager.queueEvent(QUEST_COND_COMPLETE_TALK, talkId);
	}
	
	private boolean handleInlineTalkExec(int talkId, GameEntity entity, TalkExecParam execParam) {
		if (execParam == null || execParam.getType() == null) {
			return true;
		}

		// TALK_EXEC_NONE is a real no-op placeholder. Do not send it to TalkSystem,
		// otherwise it only produces useless "missing handler" logs.
		if (execParam.getType() == TalkExec.TALK_EXEC_NONE) {
			return true;
		}

		if (execParam.getType() != TalkExec.TALK_EXEC_SET_GADGET_STATE) {
			return false;
		}

		if (!(entity instanceof EntityGadget gadget)) {
			Grasscutter.getLogger()
					.warn(
							"[TalkManager] TALK_EXEC_SET_GADGET_STATE failed: talkId={}, entityClass={}, params={}",
							talkId,
							entity != null ? entity.getClass().getSimpleName() : "null",
							Arrays.toString(execParam.getParam()));
			return true;
		}

		var params = execParam.getParam();

		if (params == null || params.length < 1 || params[0] == null || params[0].isBlank()) {
			Grasscutter.getLogger()
					.warn(
							"[TalkManager] TALK_EXEC_SET_GADGET_STATE missing state param: talkId={}, gadgetEntityId={}, configId={}, groupId={}",
							talkId,
							gadget.getId(),
							gadget.getConfigId(),
							gadget.getGroupId());
			return true;
		}

		int newState;

		try {
			newState = Integer.parseInt(params[0]);
		} catch (NumberFormatException e) {
			Grasscutter.getLogger()
					.warn(
							"[TalkManager] TALK_EXEC_SET_GADGET_STATE invalid state param: talkId={}, param={}, gadgetEntityId={}, configId={}, groupId={}",
							talkId,
							params[0],
							gadget.getId(),
							gadget.getConfigId(),
							gadget.getGroupId());
			return true;
		}

		int oldState = gadget.getState();
		gadget.updateState(newState);
		
		this.tryStartIcewindSuiteFallback(gadget, newState);

		Grasscutter.getLogger()
				.warn(
						"[TalkManager] Applied TALK_EXEC_SET_GADGET_STATE: talkId={}, gadgetEntityId={}, configId={}, groupId={}, oldState={}, newState={}",
						talkId,
						gadget.getId(),
						gadget.getConfigId(),
						gadget.getGroupId(),
						oldState,
						newState);

		return true;
	}

    public void saveTalkToQuest(int talkId, int mainQuestId) {
        // TODO, problem with this is that some talks for activity also have
        // quest id, which isn't present in QuestExcels
        var mainQuest = this.getPlayer().getQuestManager().getMainQuestById(mainQuestId);
        if (mainQuest == null) return;

        mainQuest.getTalks().put(talkId, new TalkData(talkId, ""));
    }
	
	private void tryStartIcewindSuiteFallback(EntityGadget gadget, int newState) {
		if (gadget == null) {
			return;
		}

		// Icewind Suite / Maillardet control gadget.
		if (gadget.getScene().getId() != 3
				|| gadget.getGroupId() != 133402002
				|| gadget.getConfigId() != 2004) {
			return;
		}

		int[] monsterIds;

		if (newState == 6801804) {
			// Dirge of Coppelia.
			monsterIds = new int[] {24070101};
		} else if (newState == 6801805) {
			// Nemesis of Coppelius.
			monsterIds = new int[] {24070201};
		} else {
			return;
		}

		var scene = gadget.getScene();

		// Remove any previous Icewind fallback boss entity so selecting the other option
		// does not stack multiple boss attempts.
		Set<Integer> icewindMonsterIds =
				Set.of(24070101, 24070102, 24070201, 24070202, 24070301);

		var existingIcewindMonsters =
				scene.getEntities().values().stream()
						.filter(e -> e instanceof EntityMonster)
						.map(e -> (EntityMonster) e)
						.filter(m -> icewindMonsterIds.contains(m.getMonsterData().getId()))
						.toList();

		if (!existingIcewindMonsters.isEmpty()) {
			scene.removeEntities(
					new ArrayList<>(existingIcewindMonsters), VisionType.VisionType_VISION_REMOVE);
		}

		// Stage position near the Icewind Suite arena gadget from group 133402002.
		Position basePos = new Position(3603.317f, 438.115f, 3814.537f);
		Position baseRot = new Position(0f, 221.7f, 0f);

		int level = scene.getLevelForMonster(0, 90);

		List<EntityMonster> toSpawn = new ArrayList<>();

		for (int i = 0; i < monsterIds.length; i++) {
			int monsterId = monsterIds[i];
			var monsterData = GameData.getMonsterDataMap().get(monsterId);

			if (monsterData == null) {
				Grasscutter.getLogger()
						.warn("[IcewindSuiteFallback] Missing monster data for monsterId={}", monsterId);
				continue;
			}

			Position pos = new Position(
					basePos.getX() + (i * 3f),
					basePos.getY(),
					basePos.getZ());

			EntityMonster monster = new EntityMonster(scene, monsterData, pos, baseRot, level, true);
			
			Grasscutter.getLogger()
				.warn(
						"[IcewindSuiteFallback] Spawn ability check: monsterId={}, abilities={}",
						monsterId,
						monster.getInstancedAbilities().stream()
								.map(a -> a.getData().abilityName)
								.filter(
										name ->
												name.contains("MachinaIustitia")
														|| name.contains("Nutcracker")
														|| name.contains("CombatType"))
								.toList());

			monster.setGroupId(133402002);
			monster.setBlockId(334);
			monster.setConfigId(newState == 6801804 ? 180400 + i : 180500 + i);
			monster.setPoseId(0);

			// Defensive: make sure it starts alive even if the monster data is odd.
			if (monster.getFightProperty(FightProperty.FIGHT_PROP_CUR_HP) <= 0f) {
				monster.setFightProperty(
						FightProperty.FIGHT_PROP_CUR_HP,
						monster.getFightProperty(FightProperty.FIGHT_PROP_MAX_HP));
			}

			toSpawn.add(monster);
		}

		if (!toSpawn.isEmpty()) {
			
			scene.queueIcewindSuiteArenaTeleport(this.getPlayer(), 2000);
			scene.activateIcewindSuiteFallbackWeather();
			
			scene.addEntities(toSpawn, VisionType.VisionType_VISION_BORN);
			toSpawn.forEach(scene::registerIcewindSuiteFallbackBoss);
			
			 // Hide the decorative Icewind Suite presence prop while the actual boss is active.
			scene.hideIcewindSuitePresenceProp();

			Grasscutter.getLogger()
					.warn(
							"[IcewindSuiteFallback] Started {}: state={}, spawnedMonsterIds={}, level={}, pos={}",
							newState == 6801804 ? "Dirge of Coppelia" : "Nemesis of Coppelius",
							newState,
							java.util.Arrays.toString(monsterIds),
							level,
							basePos);
		}
	}
}
