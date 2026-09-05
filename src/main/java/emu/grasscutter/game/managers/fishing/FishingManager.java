package emu.grasscutter.game.managers.fishing;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.fishing.*;
import emu.grasscutter.game.entity.*;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.game.world.Position;
import emu.grasscutter.net.proto.FishBattleResultOuterClass.FishBattleResult;
import emu.grasscutter.net.proto.FishEscapeReasonOuterClass.FishEscapeReason;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import emu.grasscutter.net.proto.VisionTypeOuterClass.VisionType;
import emu.grasscutter.server.packet.send.*;
import lombok.Getter;
import lombok.Setter;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Future;

public class FishingManager {
    /** Delay (in scheduler ticks = real seconds) before a caught fish respawns in its pool. */
    private static final int FISH_RESTOCK_DELAY_TICKS = 120;

    private final Player player;

    @Getter @Setter private int lastFishRodId = 200904; // Default rod
    @Getter private boolean inFishing = false;
    @Getter private int activeBaitId;
    @Getter private Position castPosition;
    @Getter private EntityMonster hookedFish;
    private int biteTaskId = 0;

    // Track fish pool catches: poolEntityId -> count
    private final Map<Integer, Integer> poolDailyCatchMap = new ConcurrentHashMap<>();

    public FishingManager(Player player) {
        this.player = player;
    }

    public void onEnterFishing() {
        this.inFishing = true;
        player.sendPacket(new PacketPlayerFishingDataNotify(this.lastFishRodId));
    }

    public void onExitFishing() {
        this.clearFishingSession();
        this.inFishing = false;
    }

    public void clearFishingSession() {
        if (this.biteTaskId > 0) {
            if (player.getServer() != null && player.getServer().getScheduler() != null) {
                player.getServer().getScheduler().cancelTask(this.biteTaskId);
            }
            this.biteTaskId = 0;
        }
        this.hookedFish = null;
        this.castPosition = null;
    }

    public void onCastRod(int baitId, int rodId, Position pos) {
        this.lastFishRodId = rodId;
        this.activeBaitId = baitId;
        this.castPosition = pos;

        // Deduct 1 bait item
        player.getInventory().removeItem(baitId, 1);
        player.sendPacket(new PacketFishCastRodRsp(0));

        // Find closest fish in scene (officially the fish nearest to the cork gets attracted,
        // even if the cork is a few meters beyond the fish's own attract range)
        EntityMonster targetFish = null;
        FishData targetFishData = null;
        float minDistance = Float.MAX_VALUE;

        var scene = player.getScene();
        for (GameEntity entity : scene.getEntities().values()) {
            if (!(entity instanceof EntityMonster monster)) continue;
            if (monster.getFishId() <= 0) continue;

            FishData fData = GameData.getFishDataMap().get(monster.getFishId());
            if (fData == null) continue;

            float dist = (float) monster.getPosition().computeDistance(pos);
            float range = Math.max(fData.getAttractRange(), 6.0f);
            if (dist <= range && dist < minDistance) {
                minDistance = dist;
                targetFish = monster;
                targetFishData = fData;
            }
        }

        if (targetFish != null) {
            this.hookedFish = targetFish;

            // Official servers send FishAttractNotify and FishChosenNotify together
            player.sendPacket(new PacketFishAttractNotify(player.getUid(), pos, List.of(targetFish.getId())));
            player.sendPacket(new PacketFishChosenNotify(targetFish.getId()));
        }
    }

    public void onFishBite() {
        player.sendPacket(new PacketFishBiteRsp(0));
    }

    public void onFishBattleBegin() {
        player.sendPacket(new PacketFishBattleBeginRsp(0));
    }

    public void onFishBattleEnd(FishBattleResult result) {
        if (result == FishBattleResult.FishBattleResult_SUCC && this.hookedFish != null) {
            int fishConfigId = this.hookedFish.getFishId();
            FishData fData = GameData.getFishDataMap().get(fishConfigId);

            int poolEntityId = this.hookedFish.getFishPoolEntityId();
            int caughtCount = poolDailyCatchMap.getOrDefault(poolEntityId, 0) + 1;
            poolDailyCatchMap.put(poolEntityId, caughtCount);

            List<ItemParam> rewards = new ArrayList<>();
            if (fData != null && fData.getItemId() > 0) {
                player.getInventory().addItem(new GameItem(fData.getItemId(), 1), ActionReason.SubfieldDrop);
                rewards.add(ItemParam.newBuilder()
                        .setItemId(fData.getItemId())
                        .setCount(1)
                        .build());
            }

            player.sendPacket(new PacketFishBattleEndRsp(0, result, true, rewards));

            // Officially the caught fish disappears with VISION_FISH_QTE_SUCC
            EntityMonster caughtFish = this.hookedFish;
            player.getScene()
                    .removeEntity(caughtFish, VisionType.VisionType_VISION_FISH_QTE_SUCC);

            // Update pool quota and schedule a restock of the pool
            if (poolEntityId > 0) {
                player.sendPacket(new PacketFishPoolDataNotify(poolEntityId, caughtCount));

                if (player.getScene().getEntityById(poolEntityId) instanceof EntityGadget poolGadget) {
                    poolGadget.getChildren().remove(caughtFish);
                    player.getServer()
                            .getScheduler()
                            .scheduleDelayedTask(
                                    () -> {
                                        if (player.getScene().getEntityById(poolEntityId) == poolGadget) {
                                            poolGadget.restockFishPool();
                                        }
                                    },
                                    FISH_RESTOCK_DELAY_TICKS);
                }
            }
        } else if (result == FishBattleResult.FishBattleResult_FAIL || result == FishBattleResult.FishBattleResult_TIMEOUT) {
            if (this.hookedFish != null) {
                player.sendPacket(new PacketFishEscapeNotify(
                        player.getUid(),
                        FishEscapeReason.FishEscapeReason_FISH_ESCAPE_UNHOOK,
                        this.hookedFish.getPosition(),
                        List.of(this.hookedFish.getId())));
            }
            player.sendPacket(new PacketFishBattleEndRsp(0, result, false, Collections.emptyList()));
        } else {
            player.sendPacket(new PacketFishBattleEndRsp(0, result, false, Collections.emptyList()));
        }

        this.clearFishingSession();
    }
}
