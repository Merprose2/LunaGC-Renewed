package emu.grasscutter.game.managers.cooking;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.game.player.*;
import emu.grasscutter.game.props.ActionReason;
import emu.grasscutter.net.proto.CookRecipeDataOuterClass;
import emu.grasscutter.net.proto.PlayerCookArgsReqOuterClass.PlayerCookArgsReq;
import emu.grasscutter.net.proto.PlayerCookReqOuterClass.PlayerCookReq;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.server.packet.send.*;
import io.netty.util.internal.ThreadLocalRandom;
import java.util.*;

public class CookingManager extends BasePlayerManager {
    private static final int MANUAL_PERFECT_COOK_QUALITY = 3;
    private static Set<Integer> defaultUnlockedRecipies;

    public CookingManager(Player player) {
        super(player);
    }

    public static void initialize() {
        // Initialize the set of recipies that are unlocked by default.
        defaultUnlockedRecipies = new HashSet<>();

        for (var recipe : GameData.getCookRecipeDataMap().values()) {
            if (recipe.isDefaultUnlocked()) {
                defaultUnlockedRecipies.add(recipe.getId());
            }
        }
    }

    /********************
     * Unlocking for recipies.
     ********************/
    public boolean unlockRecipe(int id) {
        if (this.player.getUnlockedRecipies().containsKey(id)) {
            return false; // Recipe already unlocked
        }
        // Tell the client that this blueprint is now unlocked and add the unlocked item to the player.
        this.player.getUnlockedRecipies().put(id, 0);
        this.player.sendPacket(new PacketCookRecipeDataNotify(id));

        return true;
    }

    /********************
     * Perform cooking.
     ********************/
    private double getSpecialtyChance(ItemData cookedItem) {
        // Chances taken from the Wiki.
        return switch (cookedItem.getRankLevel()) {
            case 1 -> 0.25;
            case 2 -> 0.2;
            case 3 -> 0.15;
            default -> 0;
        };
    }

    /**
     * qualityOutputVec is a fixed-size 5-slot array, but recipes don't all have 5 real quality
     * tiers - lower-rank recipes only fill the first 2-4 slots and pad the rest with {count: 0,
     * id: 0} placeholders. A fixed index (e.g. always assuming index 2 is "perfect") reads into
     * that padding for recipes with fewer tiers. When quality is 0 (auto-cook, no QTE result),
     * resolve to whichever tier is actually the highest one this recipe supports - i.e. the last
     * non-padding entry - rather than a hardcoded slot.
     */
    private static int resolveQualityIndex(List<ItemParamData> qualityOutputVec, int quality) {
        if (quality != 0) {
            return quality - 1;
        }
        for (int i = qualityOutputVec.size() - 1; i >= 0; i--) {
            if (qualityOutputVec.get(i).getItemId() != 0) {
                return i;
            }
        }
        return 0;
    }

    public void handlePlayerCookReq(PlayerCookReq req) {
        // Get info from the request.
        int recipeId = req.getRecipeId();
        // quality: 0 = auto-cook (no QTE), 1-3 = manual QTE cook result tier (Strange/Ordinary/
        // Delicious). count: number of dishes cooked in this request.
        int quality = req.getQteQuality();
        int count = Math.max(1, req.getCookCount());
        int avatar = req.getAssistAvatar();

        // Get recipe data.
        var recipeData = GameData.getCookRecipeDataMap().get(recipeId);
        if (recipeData == null) {
            this.player.sendPacket(new PacketPlayerCookRsp(Retcode.RET_FAIL));
            return;
        }

        // Get proficiency for player.
        int proficiency = this.player.getUnlockedRecipies().getOrDefault(recipeId, 0);

        // Try consuming materials.
        boolean success =
                player.getInventory().payItems(recipeData.getInputVec(), count, ActionReason.Cook);
        if (!success) {
            Grasscutter.getLogger()
                    .warn(
                            "Player {} failed to pay ingredients for recipe {}, aborting cook.",
                            this.player.getUid(),
                            recipeId);
            this.player.sendPacket(new PacketPlayerCookRsp(Retcode.RET_FAIL));
            return;
        }

        // Get result item information.
        var qualityOutputVec = recipeData.getQualityOutputVec();
        int qualityIndex = resolveQualityIndex(qualityOutputVec, quality);

        ItemParamData resultParam = qualityOutputVec.get(qualityIndex);
        ItemData resultItemData = GameData.getItemDataMap().get(resultParam.getItemId());

        if (resultItemData == null) {
            // Safety net: covers the case where a recipe references an item id that's genuinely
            // missing/wrong in the data, as opposed to the padding-slot issue resolveQualityIndex
            // now avoids.
            Grasscutter.getLogger()
                    .warn(
                            "Recipe {} references unknown result item id {}, aborting cook.",
                            recipeId,
                            resultParam.getItemId());
            this.player.sendPacket(new PacketPlayerCookRsp(Retcode.RET_FAIL));
            return;
        }

        // Handle character's specialties.
        //
        // CookBonusExcelConfigData's paramVec[0] is a *substitute ingredient* id (matching the
        // recipe's own inputVec numbering - e.g. recipe 1004's inputVec ends at 108009 and its
        // bonus entry's paramVec[0] is 108010), not a reward/output item id. There's currently no
        // way to tell from the request whether the player actually swapped in that ingredient, so
        // treating paramVec[0] as a replacement output item (as before) just handed back a random
        // raw material instead of food. The specialty bonus is instead applied as extra copies of
        // the correctly resolved dish, matching how companionship cooking bonuses actually work.
        int bonusCount = 0;
        var bonusData = GameData.getCookBonusDataMap().get(avatar);
        if (bonusData != null && recipeId == bonusData.getRecipeId()) {
            double bonusChance = this.getSpecialtyChance(resultItemData);
            for (int i = 0; i < count; i++) {
                if (ThreadLocalRandom.current().nextDouble() <= bonusChance) {
                    bonusCount++;
                }
            }
        }

        // Obtain results.
        List<GameItem> cookResults = new ArrayList<>();

        int totalCount = count + bonusCount;
        GameItem cookResult = new GameItem(resultItemData, resultParam.getCount() * totalCount);
        cookResults.add(cookResult);
        this.player.getInventory().addItem(cookResult);

        // Increase player proficiency, if this was a manual perfect cook.
        if (quality == MANUAL_PERFECT_COOK_QUALITY) {
            proficiency = Math.min(proficiency + 1, recipeData.getMaxProficiency());
            this.player.getUnlockedRecipies().put(recipeId, proficiency);
        }

        // Send response.
        this.player.sendPacket(
                new PacketPlayerCookRsp(cookResults, quality, count, recipeId, proficiency));
    }

    /********************
     * Cooking arguments.
     ********************/
    public void handleCookArgsReq(PlayerCookArgsReq req) {
        this.player.sendPacket(new PacketPlayerCookArgsRsp());
    }

    /********************
     * Notify unlocked recipies.
     ********************/
    private void addDefaultUnlocked() {
        // Get recipies that are already unlocked.
        var unlockedRecipies = this.player.getUnlockedRecipies();

        // Get recipies that should be unlocked by default but aren't.
        var additionalRecipies = new HashSet<>(defaultUnlockedRecipies);
        additionalRecipies.removeAll(unlockedRecipies.keySet());

        // Add them to the player.
        for (int id : additionalRecipies) {
            unlockedRecipies.put(id, 0);
        }
    }

    public void sendCookDataNotify() {
        // Default unlocked recipes to player if they don't have them yet.
        this.addDefaultUnlocked();

        // Get unlocked recipes.
        var unlockedRecipes = this.player.getUnlockedRecipies();

        // Construct CookRecipeData protos.
        List<CookRecipeDataOuterClass.CookRecipeData> data = new ArrayList<>();
        unlockedRecipes.forEach(
                (recipeId, proficiency) ->
                        data.add(
                                CookRecipeDataOuterClass.CookRecipeData.newBuilder()
                                        .setRecipeId(recipeId)
                                        .setProficiency(proficiency)
                                        .build()));

        // Send packet.
        this.player.sendPacket(new PacketCookDataNotify(data));
    }
}