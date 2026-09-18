package emu.grasscutter.game.drop;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.ItemData;
import emu.grasscutter.game.inventory.ItemType;
import emu.grasscutter.game.inventory.MaterialType;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Approximate compatibility rules for monster drops.
 *
 * The original retail level-to-drop-table mapping is unavailable for
 * newer resource families, so these rules only enforce which qualities
 * are allowed to appear at a monster's level.
 *
 * Existing drop-table probabilities and quantities remain untouched.
 */
public final class MonsterDropLevelRules {
    private static final int SECOND_MATERIAL_TIER_LEVEL = 40;
    private static final int THIRD_MATERIAL_TIER_LEVEL = 60;

    private static final int THREE_STAR_ARTIFACT_LEVEL = 20;
    private static final int FOUR_STAR_ARTIFACT_LEVEL = 40;

    /*
     * Material family rank -> {
     *     minimum rankLevel,
     *     maximum rankLevel,
     *     number of distinct rankLevels
     * }
     */
    private static final Map<Integer, int[]> FAMILY_BOUNDS =
            new ConcurrentHashMap<>();

    private MonsterDropLevelRules() {
    }

    public static boolean isUnlocked(
            ItemData itemData,
            int monsterLevel) {

        if (itemData == null) {
            return true;
        }

        int level =
                Math.max(
                        1,
                        monsterLevel);

        /*
         * Ordinary elite-enemy artifact drops.
         *
         * Retail-like eligibility:
         *
         *   3-star -> Lv.20+
         *   4-star -> Lv.40+
         *
         * Leave other rarities alone rather than making assumptions about
         * custom/boss tables which may also pass through these classes.
         */
        if (itemData.getItemType()
                == ItemType.ITEM_RELIQUARY) {

            return switch (itemData.getRankLevel()) {
                case 3 ->
                        level >= THREE_STAR_ARTIFACT_LEVEL;

                case 4 ->
                        level >= FOUR_STAR_ARTIFACT_LEVEL;

                default ->
                        true;
            };
        }

        /*
         * Normal monster-material families.
         *
         * Boss gemstones and special materials should not be caught merely
         * because they are avatar materials; we additionally require the
         * family to have exactly three consecutive quality tiers.
         */
        if (itemData.getMaterialType()
                != MaterialType.MATERIAL_AVATAR_MATERIAL) {

            return true;
        }

        int familyId =
                itemData.getRank();

        if (familyId <= 0) {
            return true;
        }

        int[] bounds =
                FAMILY_BOUNDS.computeIfAbsent(
                        familyId,
                        MonsterDropLevelRules::findFamilyBounds);

        /*
         * Only recognize the normal three-tier enemy-material layout.
         *
         * Examples may be rankLevels:
         *
         *   1 / 2 / 3
         *
         * or:
         *
         *   2 / 3 / 4
         *
         * Four-tier gemstone families, single boss materials, etc. are
         * deliberately ignored.
         */
        if (bounds[2] != 3
                || bounds[1] - bounds[0] != 2) {

            return true;
        }

        int relativeTier =
                itemData.getRankLevel()
                        - bounds[0];

        return switch (relativeTier) {
            case 0 ->
                    true;

            case 1 ->
                    level >= SECOND_MATERIAL_TIER_LEVEL;

            case 2 ->
                    level >= THIRD_MATERIAL_TIER_LEVEL;

            default ->
                    true;
        };
    }

    private static int[] findFamilyBounds(
            int familyId) {

        int minRankLevel =
                Integer.MAX_VALUE;

        int maxRankLevel =
                Integer.MIN_VALUE;

        Set<Integer> distinctRankLevels =
                new HashSet<>();

        for (ItemData item :
                GameData.getItemDataMap().values()) {

            if (item == null
                    || item.getMaterialType()
                            != MaterialType.MATERIAL_AVATAR_MATERIAL
                    || item.getRank() != familyId) {

                continue;
            }

            int rankLevel =
                    item.getRankLevel();

            minRankLevel =
                    Math.min(
                            minRankLevel,
                            rankLevel);

            maxRankLevel =
                    Math.max(
                            maxRankLevel,
                            rankLevel);

            distinctRankLevels.add(
                    rankLevel);
        }

        if (distinctRankLevels.isEmpty()) {
            return new int[] {
                    Integer.MAX_VALUE,
                    Integer.MIN_VALUE,
                    0
            };
        }

        return new int[] {
                minRankLevel,
                maxRankLevel,
                distinctRankLevels.size()
        };
    }
}