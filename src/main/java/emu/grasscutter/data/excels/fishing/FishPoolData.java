package emu.grasscutter.data.excels.fishing;

import emu.grasscutter.data.*;
import lombok.Getter;
import java.util.List;
import java.util.Map;

@ResourceType(name = "FishPoolExcelConfigData.json")
@Getter
public class FishPoolData extends GameResource {
    private int id;
    private int cityId;
    private int maxNum;
    private List<Integer> stockList;
    private Map<String, Integer> stockGuarantee;
    private List<StockLimit> stockLimitList;
    /** Ability group applied to the avatar while fishing at this pool (e.g. "Avatar_Fishing"). */
    private String abilityGroup;
    /** Ability group applied to the rest of the team while fishing. */
    private String teamAbilityGroup;

    @Override
    public int getId() {
        return this.id;
    }

    /** Per-stock constraints (fishWeight stock type + min/max fish count). */
    @Getter
    public static class StockLimit {
        private String stockType;
        private int maxNum;
        private int minNum;
    }
}