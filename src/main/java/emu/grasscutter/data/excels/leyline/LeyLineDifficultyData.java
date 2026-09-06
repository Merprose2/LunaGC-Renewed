package emu.grasscutter.data.excels.leyline;

import emu.grasscutter.data.GameResource;
import emu.grasscutter.data.ResourceType;
import lombok.Getter;

/** Stygian Onslaught difficulty tier (I - VI). */
@ResourceType(name = "LeyLineDifficultyExcelConfigData.json")
@Getter
public class LeyLineDifficultyData extends GameResource {

    @Getter(onMethod_ = @Override)
    private int id;

    /** Dungeon used by this difficulty (may be absent for tiers IV-VI in the excel). */
    private int leyLineDungeonId;
    private int LLHHEJEKAML;
    private int requiredDifficultyCompletionToUnlock;
    private long nameTextMapHash;
}
