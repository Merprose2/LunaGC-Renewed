package emu.grasscutter.game.managers.fishing;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.GameDepot;
import emu.grasscutter.data.ResourceLoader.AvatarConfig;
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

public class FishingManager {
    /** Delay (in scheduler ticks = real seconds) before a caught fish respawns in its pool. */
    private static final int FISH_RESTOCK_DELAY_TICKS = 120;

    private final Player player;
    /** Fishing ability names currently applied to the avatar (removed on exit). */
    private final List<String> activeFishingAbilities = new ArrayList<>();

    @Getter @Setter private int lastFishRodId = 200904; // Default rod
    @Getter private boolean inFishing = false;
    @Getter private int activeBaitId;
    @Getter private Position castPosition;
    @Getter private EntityMonster hookedFish;

    // Track fish pool catches: poolEntityId -> count
    private final Map<Integer, Integer> poolDailyCatchMap = new ConcurrentHashMap<>();

    public FishingManager(Player player) {
        this.player = player;
    }

    public void onEnterFishing(int poolEntityId) {
        this.inFishing = true;
        player.sendPacket(new PacketPlayerFishingDataNotify(this.lastFishRodId));

        // Apply the fishing ability group to the current avatar (official servers do this
        // before replying; the cast skill comes from these abilities).
        this.applyFishingAbilities(poolEntityId);
    }

    /** Adds the pool's fishing ability group (e.g. "Avatar_Fishing") to the current avatar. */
    private void applyFishingAbilities(int poolEntityId) {
        var avatarEntity = player.getTeamManager().getCurrentAvatarEntity();
        if (avatarEntity == null) return;
        var avatar = avatarEntity.getAvatar();

        // Resolve the pool's ability group, falling back to the standard fishing group.
        String groupName = "Avatar_Fishing";
        if (poolEntityId > 0
                && player.getScene().getEntityById(poolEntityId) instanceof EntityGadget poolGadget) {
            var poolData = poolGadget.resolveFishPoolData();
            if (poolData != null
                    && poolData.getAbilityGroup() != null
                    && !poolData.getAbilityGroup().isEmpty()) {
                groupName = poolData.getAbilityGroup();
            }
        }

        AvatarConfig config = GameDepot.getPlayerAbilities().get(groupName);
        if (config == null || config.abilities == null) {
            return;
        }

        for (var ability : config.abilities) {
            if (ability == null || ability.abilityName == null) continue;
            if (avatar.getExtraAbilityEmbryos().add(ability.abilityName)) {
                this.activeFishingAbilities.add(ability.abilityName);
            }
        }

        if (this.activeFishingAbilities.isEmpty()) return;

        // Official enter-fishing sequence: AvatarEquipChangeNotify -> SceneTeamUpdateNotify
        // -> AbilityChangeNotify (all before the EnterFishingRsp).
        if (avatar.getWeapon() != null) {
            player.sendPacket(new PacketAvatarEquipChangeNotify(avatar, avatar.getWeapon()));
        }
        player.sendPacket(new PacketSceneTeamUpdateNotify(player));
        player.sendPacket(new PacketAbilityChangeNotify(avatarEntity));
    }

    /** Removes the fishing abilities from the avatar again (official: on exit). */
    private void removeFishingAbilities() {
        if (this.activeFishingAbilities.isEmpty()) return;

        var avatarEntity = player.getTeamManager().getCurrentAvatarEntity();
        if (avatarEntity != null) {
            var avatar = avatarEntity.getAvatar();
            this.activeFishingAbilities.forEach(avatar.getExtraAbilityEmbryos()::remove);
            player.sendPacket(new PacketAbilityChangeNotify(avatarEntity));
        }
        this.activeFishingAbilities.clear();
    }

    public void onExitFishing() {
        this.clearFishingSession();
        this.inFishing = false;
        this.removeFishingAbilities();
    }

    public void clearFishingSession() {
        this.hookedFish = null;
        this.castPosition = null;
    }

    public void onCastRod(int baitId, int rodId, Position pos) {
        this.lastFishRodId = rodId;
        this.activeBaitId = baitId;
        this.castPosition = pos;

        // NOTE: officially the bait is NOT consumed at cast time - it is consumed when the fish
        // bites (FishBiteReq), together with FishBaitGoneNotify.

        // Official cast semantics (decoded from the capture):
        // - FishChosenNotify  = the bait-matching fish that will BITE (bait tag matches)
        // - FishAttractNotify = other nearby fish (decoys): they approach, then leave by design
        EntityMonster chosenFish = null;
        float chosenDist = Float.MAX_VALUE;
        List<Integer> decoyIds = new ArrayList<>();

        var scene = player.getScene();
        for (GameEntity entity : scene.getEntities().values()) {
            if (!(entity instanceof EntityMonster monster)) continue;
            if (monster.getFishId() <= 0) continue;

            FishData fData = GameData.getFishDataMap().get(monster.getFishId());
            if (fData == null) continue;

            float dist = (float) monster.getPosition().computeDistance(pos);
            if (dist > Math.max(fData.getAttractRange(), 6.0f)) continue;

            if (isFishAttractedByBait(fData, baitId)) {
                // Nearest bait-matching fish becomes the biter; other matching ones become decoys.
                if (chosenFish == null || dist < chosenDist) {
                    if (chosenFish != null) {
                        decoyIds.add(chosenFish.getId());
                    }
                    chosenDist = dist;
                    chosenFish = monster;
                } else {
                    decoyIds.add(monster.getId());
                }
            } else {
                decoyIds.add(monster.getId());
            }
        }

        if (chosenFish != null) {
            this.hookedFish = chosenFish;
        }

        // Official order of responses to FishCastRodReq:
        // FishChosenNotify -> FishAttractNotify -> FishCastRodRsp (rsp last, always sent)
        if (chosenFish != null) {
            player.sendPacket(new PacketFishChosenNotify(chosenFish.getId()));
        }
        player.sendPacket(new PacketFishAttractNotify(player.getUid(), pos, decoyIds));
        player.sendPacket(new PacketFishCastRodRsp(0));
    }

    /**
     * Official bait <-> fish matching: a fish only approaches a bait whose weighted featureTag is
     * present in the fish monster's feature tag group (e.g. Fruit Paste Bait 111023 has tag 9201,
     * which the Medaka's tag group contains).
     */
    private boolean isFishAttractedByBait(FishData fishData, int baitId) {
        var bait = GameData.getFishBaitDataMap().get(baitId);
        if (bait == null || bait.getFeatureList() == null) {
            return true; // Unknown bait: don't block attraction.
        }

        var monster = GameData.getMonsterDataMap().get(fishData.getMonsterId());
        if (monster == null) return false;

        var tagGroup = GameData.getFeatureTagGroupDataMap().get(monster.getFeatureTagGroupID());
        if (tagGroup == null || tagGroup.getTagIDs() == null) return false;

        for (var feature : bait.getFeatureList()) {
            if (feature == null || feature.getWeight() <= 0) continue; // weighted tag = species specific
            if (tagGroup.getTagIDs().contains(feature.getFeatureTag())) return true;
        }
        return false;
    }

    public void onFishBite() {
        // Official flow at bite: consume the bait (StoreItemChangeNotify is sent by the
        // inventory), tell the client the bait is gone, then reply with FishBiteRsp.
        if (this.activeBaitId > 0) {
            player.getInventory().removeItem(this.activeBaitId, 1);
        }
        player.sendPacket(new PacketFishBaitGoneNotify(player.getUid()));
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
                rewards.add(ItemParam.newBuilder()
                        .setItemId(fData.getItemId())
                        .setCount(1)
                        .build());
            }

            // Official order on catch: FishPoolDataNotify -> fish item add -> fish disappears
            // (VISION_FISH_QTE_SUCC) -> FishBattleEndRsp (last).
            player.sendPacket(new PacketFishPoolDataNotify(poolEntityId, caughtCount));

            if (fData != null && fData.getItemId() > 0) {
                player.getInventory().addItem(new GameItem(fData.getItemId(), 1), ActionReason.SubfieldDrop);
            }

            EntityMonster caughtFish = this.hookedFish;
            player.getScene()
                    .removeEntity(caughtFish, VisionType.VisionType_VISION_FISH_QTE_SUCC);

            player.sendPacket(new PacketFishBattleEndRsp(0, result, true, rewards));

            // Schedule a restock of the pool
            if (poolEntityId > 0) {
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
