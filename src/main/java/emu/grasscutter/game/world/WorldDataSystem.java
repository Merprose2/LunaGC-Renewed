package emu.grasscutter.game.world;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.excels.world.WorldLevelData;
import emu.grasscutter.game.entity.gadget.chest.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.InvestigationMonsterOuterClass;
import emu.grasscutter.net.proto.LockStateOuterClass;
import emu.grasscutter.scripts.data.*;
import emu.grasscutter.server.game.*;
import java.util.*;
import java.util.concurrent.ConcurrentHashMap;

public class WorldDataSystem extends BaseGameSystem {
    private final Map<String, ChestInteractHandler> chestInteractHandlerMap;
    private final Map<String, SceneGroup> sceneInvestigationGroupMap;
	
	private static final int BATHYSMAL_VISHAP_HERD_INVESTIGATION_ID = 37;
	private static final int BATHYSMAL_VISHAP_HERD_GROUP_ID = 155005095;

	// Verified fallback spawn locations for bosses whose original 5.5+ scene groups are not
	// available in the current resources. Keyed by InvestigationMonsterConfigData id.
	private static final Map<Integer, Position> BOSS_MARKER_POSITION_OVERRIDES =
			Map.ofEntries(
					Map.entry(79, new Position(-2251.9714f, 49.476456f, 9916.862f)),
					Map.entry(81, new Position(-3727.111f, 200.8754f, 11969.676f)),
					Map.entry(87, new Position(1471.1244f, 202.01233f, 10044.031f)),
					Map.entry(88, new Position(2504.9219f, 188.9483f, 9299.773f)),
					Map.entry(89, new Position(2327.8179f, 200.41061f, 10755.111f)),
					Map.entry(90, new Position(3423.3567f, 102.921936f, 9508.739f)),
					Map.entry(92, new Position(6406.6543f, 200.07468f, 10363.549f)),
					Map.entry(93, new Position(5792.5312f, 183.56174f, 9684.56f)),
					Map.entry(94, new Position(4200.399f, 91.27003f, -258.2993f)));

    public WorldDataSystem(GameServer server) {
        super(server);
        this.chestInteractHandlerMap = new HashMap<>();
        this.sceneInvestigationGroupMap = new ConcurrentHashMap<>();

        loadChestConfig();
    }

    public synchronized void loadChestConfig() {
        chestInteractHandlerMap.put("SceneObj_Chest_Flora", new BossChestInteractHandler());

        try {
            DataLoader.loadList("ChestReward.json", ChestReward.class)
                    .forEach(
                            reward ->
                                    reward
                                            .getObjNames()
                                            .forEach(
                                                    name ->
                                                            chestInteractHandlerMap.computeIfAbsent(
                                                                    name, x -> new NormalChestInteractHandler(reward))));
        } catch (Exception e) {
            Grasscutter.getLogger().error("Unable to load chest reward config.", e);
        }
    }

    public Map<String, ChestInteractHandler> getChestInteractHandlerMap() {
        return chestInteractHandlerMap;
    }

    public List<Integer> getInvestigationMonsterCityIds() {
        return GameData.getInvestigationMonsterDataMap().values().stream()
                .filter(Objects::nonNull)
                .map(InvestigationMonsterData::getCityId)
                .filter(cityId -> cityId > 0)
                .distinct()
                .sorted()
                .toList();
    }

    public RewardPreviewData getRewardByBossId(int monsterId) {
        var investigationMonsterData =
                GameData.getInvestigationMonsterDataMap().values().parallelStream()
                        .filter(imd -> imd.getMonsterIdList() != null && !imd.getMonsterIdList().isEmpty())
                        .filter(imd -> imd.getMonsterIdList().contains(monsterId))
                        .findFirst();

        return investigationMonsterData
                .map(monsterData -> GameData.getRewardPreviewDataMap().get(monsterData.getRewardPreviewId()))
                .orElse(null);
    }

    private SceneGroup getInvestigationGroup(int sceneId, int groupId) {
		var key = sceneId + "_" + groupId;

		if (!sceneInvestigationGroupMap.containsKey(key)) {
			try {
				var group = SceneGroup.of(groupId).load(sceneId);
				sceneInvestigationGroupMap.putIfAbsent(key, group);
				return group;
			} catch (Exception e) {
				Grasscutter.getLogger()
						.debug("Failed to load investigation group {} in scene {}", groupId, sceneId, e);
				return null;
			}
		}

		return sceneInvestigationGroupMap.get(key);
	}

    public int getMonsterLevel(SceneMonster monster, World world) {
        int level = monster.level;
        WorldLevelData worldLevelData = GameData.getWorldLevelDataMap().get(world.getWorldLevel());

        if (worldLevelData != null) {
            level = Math.max(level, worldLevelData.getMonsterLevel());
        }

        return level;
    }

    private InvestigationMonsterOuterClass.InvestigationMonster getInvestigationMonster(
			Player player, InvestigationMonsterData imd) {
		if (imd.getGroupIdList() == null
				|| imd.getGroupIdList().isEmpty()
				|| imd.getMonsterIdList() == null
				|| imd.getMonsterIdList().isEmpty()) {
			return null;
		}

		int groupId = imd.getGroupIdList().get(0);
		int monsterId = imd.getMonsterIdList().get(0);
		int sceneId = getInvestigationMonsterSceneId(imd);

		var sceneMonster = findInvestigationMonsterInGroup(sceneId, groupId, imd.getMonsterIdList());

		Position pos = null;
		int level = getDefaultInvestigationMonsterLevel(player);

		if (sceneMonster != null) {
			pos = sceneMonster.pos;
			level = getMonsterLevel(sceneMonster, player.getWorld());
		}

		if (pos == null) {
			pos = getInvestigationMonsterMarkerPosition(imd, sceneId, groupId, monsterId);
		}

		if (pos == null) {
			pos = Position.ZERO;
		}

		int resin = 0;
		int maxBossChestNum = 0;

		if ("Boss".equals(imd.getMonsterCategory())) {
			resin = imd.getPODEFGMCJAD() > 0 ? imd.getPODEFGMCJAD() : 40;
			maxBossChestNum = 1;

			var group = getInvestigationGroup(sceneId, groupId);

			if (group != null && group.gadgets != null && !group.gadgets.isEmpty()) {
				try {
					var bossChest = group.searchBossChestInGroup();

					if (bossChest.isPresent()) {
						if (bossChest.get().resin > 0) {
							resin = bossChest.get().resin;
						}

						if (bossChest.get().take_num > 0) {
							maxBossChestNum = bossChest.get().take_num;
						}
					}
				} catch (Exception e) {
					Grasscutter.getLogger()
							.debug(
									"Failed to read boss chest data for investigation monster id={}, scene={}, group={}; using fallback resin data.",
									imd.getId(),
									sceneId,
									groupId,
									e);
				}
			}
		}

		return InvestigationMonsterProto66.build(
				imd.getId(),
				imd.getCityId(),
				sceneId,
				groupId,
				monsterId,
				pos,
				level,
				180,
				0,
				resin,
				0,
				maxBossChestNum,
				0,
				imd.getCGAJKDOHDKN(),
				true);
	}

    public List<InvestigationMonsterOuterClass.InvestigationMonster> getInvestigationMonstersByCityId(
            Player player, int cityId) {
        if (GameData.getCityDataMap().get(cityId) == null) {
            Grasscutter.getLogger().warn("City not found in CityConfigData: {}", cityId);
        }

        return GameData.getInvestigationMonsterDataMap().values().stream()
                .filter(imd -> imd.getCityId() == cityId)
                .map(imd -> this.getInvestigationMonster(player, imd))
                .filter(Objects::nonNull)
                .toList();
    }

    public List<InvestigationMonsterOuterClass.InvestigationMonster>
            getInvestigationMonsterMapMarkersByCityId(Player player, int cityId) {
        if (GameData.getCityDataMap().get(cityId) == null) {
            Grasscutter.getLogger().warn("City not found in CityConfigData: {}", cityId);
        }

        return GameData.getInvestigationMonsterDataMap().values().stream()
                .filter(imd -> imd.getCityId() == cityId)
                .filter(InvestigationMonsterData::isMapMarkable)
                .filter(imd -> "Boss".equals(imd.getMonsterCategory()))
                .map(imd -> this.getInvestigationMonsterMapMarker(player, imd))
                .filter(Objects::nonNull)
                .toList();
    }

    private InvestigationMonsterOuterClass.InvestigationMonster getInvestigationMonsterMapMarker(
			Player player, InvestigationMonsterData imd) {
		if (imd.getGroupIdList() == null
				|| imd.getGroupIdList().isEmpty()
				|| imd.getMonsterIdList() == null
				|| imd.getMonsterIdList().isEmpty()) {
			return null;
		}

		int groupId = imd.getGroupIdList().get(0);
		int monsterId = imd.getMonsterIdList().get(0);
		int sceneId = getInvestigationMonsterSceneId(imd);

		Position markerPos;

		if (imd.getId() == BATHYSMAL_VISHAP_HERD_INVESTIGATION_ID
				&& groupId == BATHYSMAL_VISHAP_HERD_GROUP_ID
				&& imd.hasMapMarkerPosition()) {

			var markerData = imd.getDJLCKJCAKDA();

			markerPos =
					new Position(
							markerData.get(0),
							markerData.get(1),
							markerData.get(2));
		} else {
			markerPos = getInvestigationMonsterMarkerPosition(imd, sceneId, groupId, monsterId);
		}

		if (markerPos == null) {
			return null;
		}

		int resin = imd.getPODEFGMCJAD();
		if (resin <= 0) {
			resin = 40;
		}

		return InvestigationMonsterProto66.build(
				imd.getId(),
				imd.getCityId(),
				sceneId,
				groupId,
				monsterId,
				markerPos,
				getDefaultInvestigationMonsterLevel(player),
				180,
				0,
				resin,
				0,
				1,
				0,
				imd.getCGAJKDOHDKN(),
				true);
	}

    private SceneMonster findInvestigationMonsterInGroup(
            int sceneId, int groupId, List<Integer> monsterIdList) {
        var group = getInvestigationGroup(sceneId, groupId);

        if (group == null || group.monsters == null || group.monsters.isEmpty()) {
            return null;
        }

        return group.monsters.values().stream()
                .filter(monster -> monsterIdList.contains(monster.monster_id))
                .findFirst()
                .orElse(null);
    }

    private void applyBossChestData(
            InvestigationMonsterOuterClass.InvestigationMonster.Builder builder,
            InvestigationMonsterData imd,
            int sceneId,
            int groupId) {
        var group = getInvestigationGroup(sceneId, groupId);

        if (group != null) {
            var bossChest = group.searchBossChestInGroup();
            if (bossChest.isPresent()) {
                builder.setResin(bossChest.get().resin);
                builder.setMaxBossChestNum(bossChest.get().take_num);
                return;
            }
        }

        int resin = imd.getPODEFGMCJAD();
        if (resin <= 0) {
            resin = 40;
        }

        builder.setResin(resin);
        builder.setMaxBossChestNum(1);
    }

    private int getDefaultInvestigationMonsterLevel(Player player) {
        var worldLevelData = GameData.getWorldLevelDataMap().get(player.getWorld().getWorldLevel());
        if (worldLevelData != null) {
            return Math.max(1, worldLevelData.getMonsterLevel());
        }

        return 1;
    }

    private int getInvestigationMonsterSceneId(InvestigationMonsterData imd) {
        if (imd == null) {
            return 3;
        }

        if (imd.getCityData() != null && imd.getCityData().getSceneId() > 0) {
            return imd.getCityData().getSceneId();
        }

        if (imd.getGroupIdList() != null && !imd.getGroupIdList().isEmpty()) {
            int groupId = imd.getGroupIdList().get(0);

            int prefix = groupId / 10000000;
            int derivedSceneId = prefix - 10;

            if (derivedSceneId > 0 && derivedSceneId < 1000) {
                return derivedSceneId;
            }
        }

        return 3;
    }

    private Position getInvestigationMonsterMarkerPosition(
            InvestigationMonsterData imd, int sceneId, int groupId, int monsterId) {
        var override = BOSS_MARKER_POSITION_OVERRIDES.get(imd.getId());
        if (override != null) {
            return override.clone();
        }

        var group = getInvestigationGroup(sceneId, groupId);

        if (group != null && group.monsters != null) {
            var exactMonster =
                    group.monsters.values().stream()
                            .filter(monster -> monster.monster_id == monsterId)
                            .findFirst();

            if (exactMonster.isPresent()) {
                return exactMonster.get().pos;
            }

            if (imd.getMonsterIdList() != null && !imd.getMonsterIdList().isEmpty()) {
                var relatedMonster =
                        group.monsters.values().stream()
                                .filter(monster -> imd.getMonsterIdList().contains(monster.monster_id))
                                .findFirst();

                if (relatedMonster.isPresent()) {
                    return relatedMonster.get().pos;
                }
            }
        }

        if (imd.hasMapMarkerPosition()) {
            var posData = imd.getDJLCKJCAKDA();
            return new Position(posData.get(0), posData.get(1), posData.get(2));
        }

        return null;
    }
}