package emu.grasscutter.game.managers.blossom;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.RewardPreviewData;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.entity.gadget.GadgetWorktop;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.world.*;
import emu.grasscutter.game.world.SpawnDataEntry.SpawnGroupEntry;
import emu.grasscutter.net.proto.*;
import emu.grasscutter.server.packet.send.PacketBlossomBriefInfoNotify;
import emu.grasscutter.utils.Utils;
import it.unimi.dsi.fastutil.ints.*;
import java.util.*;

public class BlossomManager {
    private final Scene scene;
    private final List<BlossomActivity> blossomActivities = new ArrayList<>();
    private final List<BlossomActivity> activeChests = new ArrayList<>();
    private final List<EntityGadget> createdEntity = new ArrayList<>();

    private final List<SpawnDataEntry> blossomConsumed = new ArrayList<>();

    private static final int[] SCOIN_REWARDS = {4101, 4103, 4104, 4105, 4106, 4107, 4108, 4109, 4110};
    private static final int[] EXP_REWARDS   = {4001, 4003, 4004, 4005, 4006, 4007, 4008, 4009, 4010};
    private static final int[] DRAGON_A     = {30311, 30312, 30313, 30314, 30315, 30316, 30317, 30318, 30319};
    private static final int[] DRAGON_B     = {30321, 30322, 30323, 30324, 30325, 30326, 30327, 30328, 30329};

    public BlossomManager(Scene scene) {
        this.scene = scene;
    }

    public void onTick() {
        synchronized (blossomActivities) {
            var it = blossomActivities.iterator();
            while (it.hasNext()) {
                var active = it.next();
                active.onTick();
                if (active.getPass()) {
                    EntityGadget chest = active.getChest();
                    scene.addEntity(chest);
                    scene.setChallenge(null);
                    activeChests.add(active);
                    it.remove();
                }
            }
        }
    }

    public void recycleGadgetEntity(List<GameEntity> entities) {
        for (var entity : entities) {
            if (entity instanceof EntityGadget gadget) {
                createdEntity.remove(gadget);
            }
        }
        notifyIcon();
    }

    public void initBlossom(EntityGadget gadget) {
        if (createdEntity.contains(gadget)) {
            return;
        }
        if (blossomConsumed.contains(gadget.getSpawnEntry())) {
            return;
        }
        var id = gadget.getGadgetId();
        if (BlossomType.valueOf(id) == null) {
            return;
        }
        gadget.buildContent();
        gadget.setState(204);
        int worldLevel = getWorldLevel();
        GadgetWorktop gadgetWorktop = ((GadgetWorktop) gadget.getContent());
        gadgetWorktop.addWorktopOptions(new int[] {187});
        gadgetWorktop.setOnSelectWorktopOptionEvent(
                (GadgetWorktop context, int option) -> {
                    BlossomActivity activity;
                    EntityGadget entityGadget = context.getGadget();
                    synchronized (blossomActivities) {
                        for (BlossomActivity i : this.blossomActivities) {
                            if (i.getGadget() == entityGadget) {
                                return false;
                            }
                        }

                        int volume = 0;
                        IntList monsters = new IntArrayList();
                        while (true) {
                            var remain = GameDepot.getBlossomConfig().getMonsterFightingVolume() - volume;
                            if (remain <= 0) {
                                break;
                            }
                            var rand = Utils.randomRange(1, 100);
                            if (rand > 85 && remain >= 50) { // 15% ,generate strong monster
                                monsters.addAll(getRandomMonstersID(2, 1));
                                volume += 50;
                            } else if (rand > 50 && remain >= 20) { // 35% ,generate normal monster
                                monsters.addAll(getRandomMonstersID(1, 1));
                                volume += 20;
                            } else { // 50% ,generate weak monster
                                monsters.addAll(getRandomMonstersID(0, 1));
                                volume += 10;
                            }
                        }

                        Grasscutter.getLogger().info("Blossom Monsters:" + monsters);

                        activity = new BlossomActivity(entityGadget, monsters, -1, worldLevel);
                        blossomActivities.add(activity);
                    }
                    entityGadget.updateState(201);
                    scene.setChallenge(activity.getChallenge());
                    scene.removeEntity(entityGadget, VisionTypeOuterClass.VisionType.VisionType_VISION_REMOVE);
                    activity.start();
                    return true;
                });
        createdEntity.add(gadget);
        notifyIcon();
    }

    public void notifyIcon() {
        final int wl = getWorldLevel();
        final int worldLevel = (wl < 0) ? 0 : ((wl > 9) ? 9 : wl);
        final var worldLevelData = GameData.getWorldLevelDataMap().get(worldLevel);
        final int monsterLevel = (worldLevelData != null) ? worldLevelData.getMonsterLevel() : 1;
        List<BlossomBriefInfoOuterClass.BlossomBriefInfo> blossoms = new ArrayList<>();
        GameDepot.getSpawnLists()
                .forEach(
                        (gridBlockId, spawnDataEntryList) -> {
                            int sceneId = gridBlockId.getSceneId();
                            spawnDataEntryList.stream()
                                    .map(SpawnDataEntry::getGroup)
                                    .map(SpawnGroupEntry::getSpawns)
                                    .flatMap(List::stream)
                                    .filter(spawn -> !blossomConsumed.contains(spawn))
                                    .filter(spawn -> BlossomType.valueOf(spawn.getGadgetId()) != null)
                                    .forEach(
                                            spawn -> {
                                                var type = BlossomType.valueOf(spawn.getGadgetId());
                                                Integer previewReward = getPreviewReward(type, worldLevel);

                                                if (previewReward == null) {
                                                    return;
                                                }

                                                blossoms.add(
                                                        BlossomBriefInfoOuterClass.BlossomBriefInfo.newBuilder()
                                                                .setSceneId(sceneId)
                                                                .setPos(spawn.getPos().toProto())
                                                                .setResin(20)
                                                                .setMonsterLevel(monsterLevel)
                                                                .setRewardId(previewReward)
                                                                .setCircleCampId(type.getCircleCampId())
                                                                .setRefreshId(type.getBlossomChestId())
                                                                .build());
                                            });
                        });
        scene.broadcastPacket(new PacketBlossomBriefInfoNotify(blossoms));
    }

    public int getWorldLevel() {
        return scene.getWorld().getWorldLevel();
    }

    private static Integer getFallbackPreviewReward(int blossomChestId, Object data, int worldLevel) {
        int wl = Math.max(0, Math.min(worldLevel, 8));
        if (blossomChestId == 1) return SCOIN_REWARDS[wl];
        if (blossomChestId == 2) return EXP_REWARDS[wl];
        if (blossomChestId == 3) return DRAGON_A[wl];
        if (blossomChestId == 4) return DRAGON_B[wl];

        if (data != null) {
            try {
                var method = data.getClass().getMethod("getRefreshType");
                Object typeObj = method.invoke(data);
                if (typeObj != null) {
                    String str = typeObj.toString();
                    if (str.contains("SENTRY_TOWER")) return 31701;
                    if (str.contains("BOMB")) return 31702;
                }
            } catch (Exception ignored) {}
        }

        return null;
    }

    private static Integer getPreviewReward(BlossomType type, int worldLevel) {
        // TODO: blossoms should be based on their city
        if (type == null) {
            Grasscutter.getLogger().error("Illegal blossom type {}", type);
            return null;
        }

        int blossomChestId = type.getBlossomChestId();
        var dataMap = GameData.getBlossomRefreshExcelConfigDataMap();

        if (dataMap == null || dataMap.isEmpty()) {
            Grasscutter.getLogger().debug("Blossom refresh config data is missing.");
            return null;
        }

        for (var data : dataMap.values()) {
            if (data == null) {
                continue;
            }

            if (blossomChestId == data.getBlossomChestId()) {
                var dropVecList = data.getDropVec();

                // Handles File 1 where "DropVec" was capitalized and GSON skipped deserialization
                if (dropVecList == null || dropVecList.length == 0) {
                    Integer fallback = getFallbackPreviewReward(blossomChestId, data, worldLevel);
                    if (fallback != null) {
                        return fallback;
                    }

                    Grasscutter.getLogger()
                            .debug(
                                    "Blossom refresh config has no drop vector: blossomChestId={}, type={}",
                                    blossomChestId,
                                    type);
                    return null;
                }

                if (worldLevel < 0 || worldLevel >= dropVecList.length) {
                    Grasscutter.getLogger()
                            .debug(
                                    "Illegal blossom world level: worldLevel={}, dropVecLength={}, blossomChestId={}, type={}",
                                    worldLevel,
                                    dropVecList.length,
                                    blossomChestId,
                                    type);
                    return null;
                }

                if (dropVecList[worldLevel] == null) {
                    Grasscutter.getLogger()
                            .debug(
                                    "Blossom drop vector entry is null: worldLevel={}, blossomChestId={}, type={}",
                                    worldLevel,
                                    blossomChestId,
                                    type);
                    return null;
                }

                return dropVecList[worldLevel].getPreviewReward();
            }
        }

        Grasscutter.getLogger().debug("Cannot find blossom type {}", type);
        return null;
    }

    private static RewardPreviewData getRewardList(BlossomType type, int worldLevel) {
        Integer previewReward = getPreviewReward(type, worldLevel);
        if (previewReward == null) return null;
        return GameData.getRewardPreviewDataMap().get((int) previewReward);
    }

    public List<GameItem> onReward(Player player, EntityGadget chest, boolean useCondensedResin) {
        var resinManager = player.getResinManager();
        synchronized (activeChests) {
            var it = activeChests.iterator();
            while (it.hasNext()) {
                var activeChest = it.next();
                if (activeChest.getChest() == chest) {
                    boolean pay =
                            useCondensedResin ? resinManager.useCondensedResin(1) : resinManager.useResin(20);
                    if (pay) {
                        int worldLevel = getWorldLevel();
                        List<GameItem> items = new ArrayList<>();
                        var gadget = activeChest.getGadget();
                        var type = BlossomType.valueOf(gadget.getGadgetId());
                        RewardPreviewData blossomRewards = getRewardList(type, worldLevel);
                        if (blossomRewards == null) {
                            Grasscutter.getLogger()
                                    .error("Blossom could not support world level : " + worldLevel);
                            return null;
                        }
                        var rewards = blossomRewards.getPreviewItems();
                        for (ItemParamData blossomReward : rewards) {
                            int rewardCount = blossomReward.getCount();
                            if (useCondensedResin) {
                                rewardCount += blossomReward.getCount(); // Double!
                            }
                            items.add(new GameItem(blossomReward.getItemId(), rewardCount));
                        }
                        it.remove();
                        recycleGadgetEntity(List.of(gadget));
                        blossomConsumed.add(gadget.getSpawnEntry());
                        return items;
                    }
                    return null;
                }
            }
        }
        return null;
    }

    public static IntList getRandomMonstersID(int difficulty, int count) {
        IntList result = new IntArrayList();
        List<Integer> monsters =
                GameDepot.getBlossomConfig().getMonsterIdsPerDifficulty().get(difficulty);
        for (int i = 0; i < count; i++) {
            result.add((int) monsters.get(Utils.randomRange(0, monsters.size() - 1)));
        }
        return result;
    }
}
