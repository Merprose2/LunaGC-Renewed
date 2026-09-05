package emu.grasscutter.data.excels.fishing;

import emu.grasscutter.data.*;
import java.util.List;
import lombok.Getter;

@ResourceType(name = "FishBaitExcelConfigData.json")
@Getter
public class FishBaitData extends GameResource {
    private int id;
    /** Feature tags of this bait; the weighted entry is the fish species it attracts. */
    private List<BaitFeature> featureList;
    private int sort;

    @Override
    public int getId() {
        return this.id;
    }

    @Getter
    public static class BaitFeature {
        private int weight;
        private int bonusRange;
        private int featureTag;
    }
}
