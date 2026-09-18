package emu.grasscutter.data.excels;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.*;
import java.util.List;
import lombok.Getter;

/**
 * The circle camp definitions of the ley line outcrops ("blossoms"), one entry per camp and city.
 *
 * <p>Every camp points at the scene group that contains its operator gadget through {@code
 * newGroupVec}, which is what lets the server tell a ley line camp group apart from an ordinary
 * overworld or quest group. Without it, outcrops could only be detected in the nations that happen
 * to have hand written spawn data (Mondstadt / Liyue / Inazuma / Fontaine).
 */
@ResourceType(name = "BlossomGroupsExcelConfigData.json")
@Getter
public class BlossomGroupsExcelConfigData extends GameResource {
    @Getter(onMethod_ = @Override)
    private int id;
    // Camp details
    private int cityId;
    private int sectionId;
    private int limitLevel;
    private int remindRadius;
    private int fightRadius;
    private int finishProgress;

    @SerializedName(value = "nextCampIdVec", alternate = {"next_camp_id_vec"})
    private List<Integer> nextCampIdVec;

    /** Scene groups (one per camp) that are refreshed for this camp. */
    @SerializedName(value = "newGroupVec", alternate = {"new_group_vec"})
    private List<Integer> newGroupVec;

    @SerializedName(value = "refreshTypeVec", alternate = {"refresh_type_vec"})
    private List<Integer> refreshTypeVec;
}