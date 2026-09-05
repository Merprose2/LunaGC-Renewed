package emu.grasscutter.data.excels.monster;

import emu.grasscutter.data.*;
import java.util.List;
import lombok.Getter;

/** Maps a monster's featureTagGroupID to its feature tags (used for fish <-> bait matching). */
@ResourceType(name = "FeatureTagGroupExcelConfigData.json")
@Getter
public class FeatureTagGroupData extends GameResource {
    private int groupID;
    private List<Integer> tagIDs;

    @Override
    public int getId() {
        return this.groupID;
    }
}
