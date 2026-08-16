package emu.grasscutter.game.drop;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.data.common.DropItemData;
import emu.grasscutter.data.excels.*;
import emu.grasscutter.data.server.DropTableExcelConfigData;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.inventory.*;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.scripts.data.SceneMonster;
import emu.grasscutter.server.game.*;
import emu.grasscutter.server.packet.send.*;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;
import java.util.*;

public final class DropSystem extends BaseGameSystem {
	private static final int SETEKH_WENUT_MONSTER_ID = 26130101;
    private static final Set<Integer> PRE_3_3_OVERWORLD_BOSS_IDS =
            Set.of(
                    20040101, // Electro Hypostasis
                    20040201, // Anemo Hypostasis
                    26020101, // Cryo Regisvine
                    29020101, // Andrius base form
                    29020102, // Andrius combat form
                    20040301, // Geo Hypostasis
                    20050101, // Oceanid
                    26020201, // Pyro Regisvine
                    26050101, // Primo Geovishap - Hydro
                    26050201, // Primo Geovishap - Pyro
                    26050301, // Primo Geovishap - Cryo
                    26050401, // Primo Geovishap - Electro
                    20040501, // Cryo Hypostasis
                    25090101, // Maguu Kenki
                    20040601, // Pyro Hypostasis
                    24021101, // Perpetual Mechanical Array
                    20040401, // Hydro Hypostasis
                    20070101, // Thunder Manifestation
                    22060101, // Golden Wolflord
                    26050701, // Bathysmal Vishap Herd - Bolteater
                    26050801, // Bathysmal Vishap Herd - Rimebiter
                    24010401, // Ruin Serpent
                    26020301, // Electro Regisvine
                    26110101, // Jadeplume Terrorshroom
                    24030301, // Aeonblight Drake
                    24050101, // Algorithm of Semi-Intransient Matrix
                    20040701, // Dendro Hypostasis
					SETEKH_WENUT_MONSTER_ID); // Setekh Wenut, Version 3.4
	
	/*
	 * Some environments display or decode the Chinese strings as question marks.
	 * Resolve the canonical ChestDrop.json key from the known monster ID instead.
	 */
	private static final Map<Integer, String> PRE_3_3_BOSS_DROP_TAGS =
			Map.ofEntries(
					Map.entry(20040101, "\u65E0\u76F8\u4E4B\u96F7"), // Electro Hypostasis
					Map.entry(20040201, "\u65E0\u76F8\u4E4B\u98CE"), // Anemo Hypostasis
					Map.entry(20040301, "\u65E0\u76F8\u4E4B\u5CA9"), // Geo Hypostasis
					Map.entry(20040401, "\u65E0\u76F8\u4E4B\u6C34"), // Hydro Hypostasis
					Map.entry(20040501, "\u65E0\u76F8\u4E4B\u51B0"), // Cryo Hypostasis
					Map.entry(20040601, "\u65E0\u76F8\u4E4B\u706B"), // Pyro Hypostasis
					Map.entry(20040701, "\u65E0\u76F8\u4E4B\u8349"), // Dendro Hypostasis
					Map.entry(20050101, "\u7EAF\u6C34\u7CBE\u7075"), // Oceanid
					Map.entry(20070101, "\u96F7\u97F3\u6743\u73B0"), // Thunder Manifestation
					Map.entry(22060101, "\u9EC4\u91D1\u738B\u517D"), // Golden Wolflord
					Map.entry(24010401, "\u9057\u8FF9\u5DE8\u86C7"), // Ruin Serpent
					Map.entry(24021101, "\u6052\u5E38\u673A\u5173\u9635\u5217"), // PMA
					Map.entry(24030301, "\u5146\u8F7D\u6C38\u52AB\u9F99\u517D"), // Aeonblight Drake
					Map.entry(24050101, "\u534A\u6C38\u6052\u7EDF\u8F96\u77E9\u9635"), // ASIMON
					Map.entry(25090101, "\u9B54\u5076\u5251\u9B3C"), // Maguu Kenki
					Map.entry(26020101, "\u6025\u51BB\u6811"), // Cryo Regisvine
					Map.entry(26020201, "\u7206\u708E\u6811"), // Pyro Regisvine
					Map.entry(26020301, "\u63A3\u7535\u6811"), // Electro Regisvine
					Map.entry(26050101, "\u53E4\u5CA9\u9F99\u8725\u6C34"), // Primo Geovishap Hydro
					Map.entry(26050201, "\u53E4\u5CA9\u9F99\u8725\u706B"), // Primo Geovishap Pyro
					Map.entry(26050301, "\u53E4\u5CA9\u9F99\u8725\u51B0"), // Primo Geovishap Cryo
					Map.entry(26050401, "\u53E4\u5CA9\u9F99\u8725\u7535"), // Primo Geovishap Electro
					Map.entry(26050701, "\u6DF1\u6D77\u9F99\u8725\u7EC4\u5408"), // Vishap Herd
					Map.entry(26050801, "\u6DF1\u6D77\u9F99\u8725\u7EC4\u5408"), // Vishap Herd
					Map.entry(26110101, "\u7FE0\u7FCE\u6050\u8548"), // Jadeplume
					Map.entry(29020101, "\u5317\u98CE\u72FC"), // Andrius
					Map.entry(29020102, "\u5317\u98CE\u72FC")); // Andrius combat form

    private static final int[] FIVE_STAR_BERSERKER_ITEM_IDS = {
        55543, 55544, 55523, 55524, 55553, 55554, 55513, 55514, 55533, 55534
    };

    private static final int[] FIVE_STAR_INSTRUCTOR_ITEM_IDS = {
        57543, 57544, 57523, 57524, 57553, 57554, 57513, 57514, 57533, 57534
    };

    private static final int CHARACTER_EXP_ITEM_ID = 101;
	private static final int ADVENTURE_EXP_ITEM_ID = 102;
	private static final int COMPANIONSHIP_EXP_ITEM_ID = 105;
	private static final int MORA_ITEM_ID = 202;
    private static final int CUSTOM_ARTIFACT_ROLL_MIN = 9970;
    private static final int CUSTOM_ARTIFACT_ROLL_BOUND = 10000;

    private final Int2ObjectMap<DropTableData> dropTable;
	private final Int2ObjectMap<DropTableExcelConfigData> serverDropTable;
    private final Map<String, List<BaseDropData>> chestReward;
    private final Map<String, List<BaseDropData>> monsterDrop;
    private final Random rand;

    // TODO: don't know how to determine boss level.Have to hard-code the data from wiki.
    private final int[] bossLevel = {36, 37, 41, 50, 62, 72, 83, 91, 93, 103};

    public DropSystem(GameServer server) {
        super(server);

        this.rand = new Random();
        this.dropTable = GameData.getDropTableDataMap();
		this.serverDropTable = GameData.getDropTableExcelConfigDataMap();
        this.chestReward = new HashMap<>();
        this.monsterDrop = new HashMap<>();

        try {
            var dataList = DataLoader.loadList("ChestDrop.json", ChestDropData.class);
            for (var i : dataList) {
                if (!chestReward.containsKey(i.getIndex())) {
                    chestReward.put(i.getIndex(), new ArrayList<>());
                }
                chestReward.get(i.getIndex()).add(i);
            }
        } catch (Exception ignored) {
            Grasscutter.getLogger()
                    .error("Unable to load chest drop data. Please place ChestDrop.json in data folder.");
        }

        try {
            var dataList = DataLoader.loadList("MonsterDrop.json", BaseDropData.class);
            for (var i : dataList) {
                if (!monsterDrop.containsKey(i.getIndex())) {
                    monsterDrop.put(i.getIndex(), new ArrayList<>());
                }
                monsterDrop.get(i.getIndex()).add(i);
            }
        } catch (Exception ignored) {
            Grasscutter.getLogger()
                    .error("Unable to load monster drop data. Please place MonsterDrop.json in data folder.");
        }
    }

    private int queryDropData(String dropTag, int level, Map<String, List<BaseDropData>> rewards) {
        if (!rewards.containsKey(dropTag)) return 0;

        var rewardList = rewards.get(dropTag);
        BaseDropData dropData = null;
        int minLevel = 0;
        for (var i : rewardList) {
            if (level >= i.getMinLevel() && i.getMinLevel() > minLevel) {
                minLevel = i.getMinLevel();
                dropData = i;
            }
        }
        if (dropData == null) return 0;
        return dropData.getDropId();
    }

    public List<GameItem> handleDungeonRewardDrop(int dropId, boolean doubleReward) {
        if (!dropTable.containsKey(dropId)) return List.of();
        var dropData = dropTable.get(dropId);
        List<GameItem> items = new ArrayList<>();
        processDrop(dropData, doubleReward ? 2 : 1, items);
        return items;
    }

    public boolean handleMonsterDrop(EntityMonster monster) {
        int dropId;
		int level = monster.getLevel();
		SceneMonster sceneMonster = monster.getMetaMonster();

		if (sceneMonster != null) {
			if (sceneMonster.drop_tag != null) {
				dropId = queryDropData(sceneMonster.drop_tag, level, monsterDrop);
			} else {
				dropId = sceneMonster.drop_id;
			}
		} else {
			dropId = monster.getMonsterData().getKillDropId();
		}

		if (dropId <= 0) {
			return false;
		}

		List<GameItem> items = new ArrayList<>();
		boolean fallToGround;

		var dropData = dropTable.get(dropId);
		if (dropData != null) {
			processDrop(dropData, 1, items);
			fallToGround = dropData.isFallToGround();
		} else {
			var serverDropData = serverDropTable.get(dropId);
			if (serverDropData == null) {
				Grasscutter.getLogger()
						.debug(
								"No monster drop table found for drop_id = {}, monster_id = {}",
								dropId,
								monster.getMonsterData().getId());
				return false;
			}

			processDrop(serverDropData, 1, items);
			fallToGround = serverDropData.isFallToGround();
		}

		if (fallToGround) {
			dropItems(items, ActionReason.MonsterDie, monster, monster.getScene().getPlayers().get(0), true);
		} else {
			for (Player p : monster.getScene().getPlayers()) {
				p.getInventory().addItems(items, ActionReason.MonsterDie);
			}
		}
		return true;
	}

    public boolean handleChestDrop(int chestDropId, int dropCount, GameEntity bornFrom) {
        if (!dropTable.containsKey(chestDropId)) return false;
        var dropData = dropTable.get(chestDropId);
        List<GameItem> items = new ArrayList<>();
        processDrop(dropData, dropCount, items);
        if (dropData.isFallToGround()) {
            dropItems(items, ActionReason.OpenChest, bornFrom, bornFrom.getWorld().getHost(), false);
        } else {
            bornFrom.getWorld().getHost().getInventory().addItems(items, ActionReason.OpenChest);
        }
        return true;
    }

    public boolean handleChestDrop(String dropTag, int level, GameEntity bornFrom) {
        int dropId = queryDropData(dropTag, level, chestReward);
        if (dropId == 0) return false;
        return handleChestDrop(dropId, 1, bornFrom);
    }

    public boolean handleBossChestDrop(String dropTag, Player player) {
		int worldLevel =
				Math.max(0, Math.min(player.getWorldLevel(), bossLevel.length - 1));

		int dropId =
				queryDropData(dropTag, bossLevel[worldLevel], chestReward);

		if (dropId <= 0) {
			return false;
		}

		List<GameItem> items = new ArrayList<>();

		var localDropData = dropTable.get(dropId);
		if (localDropData != null) {
			processDrop(localDropData, 1, items);
		} else {
			var nativeDropData = serverDropTable.get(dropId);
			if (nativeDropData == null) {
				return false;
			}

			processDrop(nativeDropData, 1, items);
		}

		if (items.isEmpty()) {
			return false;
		}

		grantBossChestRewards(player, items);
		return true;
	}

	public boolean handleBossChestDrop(
			String dropTag, int monsterId, Player player) {
		if (!isPre33OverworldBoss(monsterId)) {
			return handleBossChestDrop(dropTag, player);
		}

		var items =
				generatePre33BossChestRewards(
						dropTag,
						monsterId,
						player.getWorldLevel());

		if (items.isEmpty()) {
			return false;
		}

		grantBossChestRewards(player, items);
		return true;
	}

	public List<GameItem> generatePre33BossChestRewards(
			String luaDropTag, int monsterId, int worldLevel) {
		if (!isPre33OverworldBoss(monsterId)) {
			return List.of();
		}

		List<GameItem> items = new ArrayList<>();
		boolean alreadyContainsCustomArtifacts = false;

		if (monsterId == SETEKH_WENUT_MONSTER_ID) {
			/*
			 * ChestDrop.json contains no "沙虫" reward mapping.
			 *
			 * The supplied Drop.json already has a complete reward table for
			 * monster 26130101, which is the table for Setekh Wenut.
			 */
			items.addAll(rollConfiguredMonsterRewards(monsterId));
			alreadyContainsCustomArtifacts = !items.isEmpty();
		} else {
			String canonicalDropTag =
					PRE_3_3_BOSS_DROP_TAGS.get(monsterId);

			if (canonicalDropTag == null) {
				Grasscutter.getLogger()
						.warn(
								"No canonical boss blossom drop tag for monster_id = {}",
								monsterId);
				return List.of();
			}

			if (luaDropTag != null
					&& !luaDropTag.equals(canonicalDropTag)) {
				Grasscutter.getLogger()
						.debug(
								"Ignoring mismatched Lua boss drop tag '{}' and using '{}' "
										+ "for monster_id = {}",
								luaDropTag,
								canonicalDropTag,
								monsterId);
			}

			int clampedWorldLevel =
					Math.max(0, Math.min(worldLevel, bossLevel.length - 1));

			int dropId =
					queryDropData(
							canonicalDropTag,
							bossLevel[clampedWorldLevel],
							chestReward);

			if (dropId <= 0) {
				Grasscutter.getLogger()
						.warn(
								"No boss chest reward mapping for canonical drop_tag = {}, "
										+ "monster_id = {}, world_level = {}",
								canonicalDropTag,
								monsterId,
								worldLevel);
				return List.of();
			}

			var localDropData = dropTable.get(dropId);
			if (localDropData != null) {
				processDrop(localDropData, 1, items);
			} else {
				var nativeDropData = serverDropTable.get(dropId);

				if (nativeDropData == null) {
					Grasscutter.getLogger()
							.warn(
									"No boss chest drop table for drop_id = {}, "
											+ "canonical drop_tag = {}, monster_id = {}",
									dropId,
									canonicalDropTag,
									monsterId);
					return List.of();
				}

				processDrop(nativeDropData, 1, items);
			}
		}

		if (items.isEmpty()) {
			Grasscutter.getLogger()
					.warn(
							"Resolved an empty boss blossom reward for monster_id = {}",
							monsterId);
			return List.of();
		}

		enhancePre33BossChestRewards(
				items,
				!alreadyContainsCustomArtifacts);

		return items;
	}

    public boolean isPre33OverworldBoss(int monsterId) {
        return PRE_3_3_OVERWORLD_BOSS_IDS.contains(monsterId);
    }

		private void enhancePre33BossChestRewards(List<GameItem> items, boolean addCustomFiveStarArtifacts) {
		replaceOrAddRewardCount(
				items,
				CHARACTER_EXP_ITEM_ID,
				randomRange(2000, 5000));

		replaceOrAddRewardCount(
				items,
				ADVENTURE_EXP_ITEM_ID,
				200);

		replaceOrAddRewardCount(
				items,
				COMPANIONSHIP_EXP_ITEM_ID,
				45);

		replaceOrAddRewardCount(
				items,
				MORA_ITEM_ID,
				randomRange(10000, 30000));

		if (addCustomFiveStarArtifacts) {
			rollCustomFiveStarArtifacts(
					items,
					FIVE_STAR_BERSERKER_ITEM_IDS);

			rollCustomFiveStarArtifacts(
					items,
					FIVE_STAR_INSTRUCTOR_ITEM_IDS);
		}
	}

	private List<GameItem> rollConfiguredMonsterRewards(int monsterId) {
		var configuredDrops =
				server
						.getDropSystemLegacy()
						.getDropData()
						.get(monsterId);

		if (configuredDrops == null || configuredDrops.isEmpty()) {
			Grasscutter.getLogger()
					.warn(
							"No Drop.json reward entry found for monster_id = {}",
							monsterId);
			return List.of();
		}

		List<GameItem> items = new ArrayList<>();

		for (var configuredDrop : configuredDrops) {
			int roll = randomRange(1, 10000);

			if (roll < configuredDrop.getMinWeight()
					|| roll >= configuredDrop.getMaxWeight()) {
				continue;
			}

			var itemData =
					GameData.getItemDataMap()
							.get(configuredDrop.getItemId());

			if (itemData == null) {
				continue;
			}

			int amount =
					randomRange(
							configuredDrop.getMinCount(),
							configuredDrop.getMaxCount());

			if (amount <= 0) {
				continue;
			}

			if (itemData.isEquip()) {
				/*
				 * Equipment items should be created separately rather than
				 * represented as one stacked GameItem.
				 */
				for (int i = 0; i < amount; i++) {
					items.add(
							new GameItem(
									configuredDrop.getItemId(),
									1));
				}
			} else {
				addOrMergeReward(
						items,
						configuredDrop.getItemId(),
						amount);
			}
		}

		return items;
	}

	private void replaceOrAddRewardCount(List<GameItem> items, int itemId, int count) {
		for (var item : items) {
			if (item.getItemId() == itemId) {
				item.setCount(count);
				return;
			}
		}

		addOrMergeReward(items, itemId, count);
	}

	private void addOrMergeReward(List<GameItem> items, int itemId, int count) {
		if (count <= 0 || GameData.getItemDataMap().get(itemId) == null) {
			return;
		}

		for (var item : items) {
			if (item.getItemId() == itemId) {
				item.setCount(item.getCount() + count);
				return;
			}
		}

		items.add(new GameItem(itemId, count));
	}

	private void rollCustomFiveStarArtifacts(
			List<GameItem> items,
			int[] itemIds) {
		for (int itemId : itemIds) {
			if (rand.nextInt(CUSTOM_ARTIFACT_ROLL_BOUND) >= CUSTOM_ARTIFACT_ROLL_MIN) {
				items.add(new GameItem(itemId, 1));
			}
		}
	}

	private int randomRange(int min, int max) {
		return rand.nextInt(max - min + 1) + min;
	}

    private void grantBossChestRewards(Player player, List<GameItem> items) {
        player.getInventory().addItems(items, ActionReason.OpenWorldBossChest);
        player.sendPacket(new PacketGadgetAutoPickDropInfoNotify(items));
    }

    private void processDrop(DropTableData dropData, int count, List<GameItem> items) {
        // TODO:Not clear on the meaning of some fields,like "dropLevel".Will ignore them.
        // TODO:solve drop limits,like everydayLimit.
        if (count > 1) {
            for (int i = 0; i < count; i++) processDrop(dropData, 1, items);
            return;
        }
        if (dropData.getRandomType() == 0) {
            int weightSum = 0;
            for (var i : dropData.getDropVec()) {
                int id = i.getId();
                if (id == 0) continue;
                weightSum += i.getWeight();
            }
            if (weightSum == 0) return;
            int weight = rand.nextInt(weightSum);
            int sum = 0;
            for (var i : dropData.getDropVec()) {
                int id = i.getId();
                if (id == 0) continue;
                sum += i.getWeight();
                if (weight < sum) {
                    // win the item
                    int amount = calculateDropAmount(i) * count;
                    if (amount <= 0) break;
                    addResolvedDrop(id, amount, items);
                    break;
                }
            }
        } else if (dropData.getRandomType() == 1) {
            for (var i : dropData.getDropVec()) {
                int id = i.getId();
                if (id == 0) continue;
                if (rand.nextInt(10000) < i.getWeight()) {
                    int amount = calculateDropAmount(i) * count;
                    if (amount <= 0) continue;
                    addResolvedDrop(id, amount, items);
                }
            }
        }
    }
	
	private void processDrop(DropTableExcelConfigData dropData, int count, List<GameItem> items) {
		if (dropData == null || dropData.getDropVec() == null) {
			return;
		}

		if (count > 1) {
			for (int i = 0; i < count; i++) {
				processDrop(dropData, 1, items);
			}
			return;
		}

		if (dropData.getRandomType() == 0) {
			int weightSum = 0;
			for (var i : dropData.getDropVec()) {
				int id = i.getItemId();
				if (id == 0) continue;
				weightSum += i.getWeight();
			}

			if (weightSum == 0) return;

			int weight = rand.nextInt(weightSum);
			int sum = 0;

			for (var i : dropData.getDropVec()) {
				int id = i.getItemId();
				if (id == 0) continue;

				sum += i.getWeight();
				if (weight < sum) {
					int amount = calculateDropAmount(i.getCountRange()) * count;
					addResolvedDrop(id, amount, items);
					break;
				}
			}
		} else if (dropData.getRandomType() == 1) {
			for (var i : dropData.getDropVec()) {
				int id = i.getItemId();
				if (id == 0) continue;

				if (rand.nextInt(10000) < i.getWeight()) {
					int amount = calculateDropAmount(i.getCountRange()) * count;
					addResolvedDrop(id, amount, items);
				}
			}
		}
	}

    private int calculateDropAmount(DropItemData i) {
        int amount;
        if (i.getCountRange().contains(";")) {
            String[] ranges = i.getCountRange().split(";");
            amount = rand.nextInt(Integer.parseInt(ranges[0]), Integer.parseInt(ranges[1]) + 1);
        } else if (i.getCountRange().contains(".")) {
            double expectAmount = Double.parseDouble(i.getCountRange());
            amount = (int) expectAmount;
            if (rand.nextDouble() < expectAmount - amount) amount++;
        } else {
            amount = Integer.parseInt(i.getCountRange());
        }
        return amount;
    }
	
	private void addResolvedDrop(int id, int amount, List<GameItem> items) {
		if (amount <= 0) {
			return;
		}

		if (dropTable.containsKey(id)) {
			processDrop(dropTable.get(id), amount, items);
			return;
		}

		if (serverDropTable.containsKey(id)) {
			processDrop(serverDropTable.get(id), amount, items);
			return;
		}

		if (GameData.getItemDataMap().get(id) == null) {
			Grasscutter.getLogger().debug("Skipping invalid drop item/subdrop id = {}", id);
			return;
		}

		for (var item : items) {
			if (item.getItemId() == id) {
				item.setCount(item.getCount() + amount);
				return;
			}
		}

		items.add(new GameItem(id, amount));
	}

	private int calculateDropAmount(String countRange) {
		int amount;

		if (countRange.contains(";")) {
			String[] ranges = countRange.split(";");
			amount = rand.nextInt(Integer.parseInt(ranges[0]), Integer.parseInt(ranges[1]) + 1);
		} else if (countRange.contains(".")) {
			double expectAmount = Double.parseDouble(countRange);
			amount = (int) expectAmount;
			if (rand.nextDouble() < expectAmount - amount) amount++;
		} else {
			amount = Integer.parseInt(countRange);
		}

		return amount;
	}

    /**
     * @param share Whether other players in the scene could see the drop items.
     */
    private void dropItem(
            GameItem item, ActionReason reason, Player player, GameEntity bornFrom, boolean share) {
        DropMaterialData drop = GameData.getDropMaterialDataMap().get(item.getItemId());
        if ((drop != null && drop.isAutoPick())
                || (item.getItemData().getItemType() == ItemType.ITEM_VIRTUAL
                        && item.getItemData().getGadgetId() == 0)) {
            giveItem(item, reason, player, share);
        } else {
            // TODO:solve share problem
            player.getScene().addDropEntity(item, bornFrom, player, share);
        }
    }

    private void dropItems(
            List<GameItem> items,
            ActionReason reason,
            GameEntity bornFrom,
            Player player,
            boolean share) {
        for (var i : items) {
            dropItem(i, reason, player, bornFrom, share);
        }
    }

    private void giveItem(GameItem item, ActionReason reason, Player player, boolean share) {
        if (share) {
            for (var p : player.getScene().getPlayers()) {
                p.getInventory().addItem(item, reason);
                p.sendPacket(new PacketDropHintNotify(item.getItemId(), player.getPosition().toProto()));
            }
        } else {
            player.getInventory().addItem(item, reason);
            player.sendPacket(new PacketDropHintNotify(item.getItemId(), player.getPosition().toProto()));
        }
    }

    private void giveItems(List<GameItem> items, ActionReason reason, Player player, boolean share) {
        // don't know whether we need PacketDropHintNotify.
        if (share) {
            for (var p : player.getScene().getPlayers()) {
                p.getInventory().addItems(items, reason);
                p.sendPacket(new PacketDropHintNotify(items, player.getPosition().toProto()));
            }
        } else {
            player.getInventory().addItems(items, reason);
            player.sendPacket(new PacketDropHintNotify(items, player.getPosition().toProto()));
        }
    }
}
