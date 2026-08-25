package emu.grasscutter.data.excels;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.*;
import emu.grasscutter.data.ResourceType.LoadPriority;
import emu.grasscutter.data.common.ItemParamData;
import java.util.List;
import lombok.Getter;

@ResourceType(
        name = {"CookRecipeExcelConfigData.json"},
        loadPriority = LoadPriority.LOW)
@Getter
public class CookRecipeData extends GameResource {
    @Getter(onMethod_ = @Override)
    private int id;

    private int rankLevel;
    private boolean isDefaultUnlocked;
    private int maxProficiency;

    // In the 7.0 resource dump these two JSON keys are swapped relative to their names/what
    // older (pre-7.0) Grasscutter code assumed: the array literally called "qualityOutputVec"
    // in the JSON is the raw-material cost (1-2 generic MATERIAL_EXCHANGE items), and the array
    // called "inputVec" is actually the cooked dish at its 3 quality tiers (Strange/Ordinary/
    // Delicious, all sharing the recipe's icon). Verified against every sampled recipe (1001,
    // 1003-1007, 1101, 1102, ...) - the pattern is 100% consistent, not a one-off.
    //
    // We keep the Java field/getter names matching what the data ACTUALLY represents, and map
    // them to the (mislabeled) JSON keys via @SerializedName so the rest of the code can use
    // semantically correct names instead of perpetuating the confusion.
    @SerializedName("qualityOutputVec")
    private List<ItemParamData> inputVec; // ingredient cost - despite the JSON key name

    @SerializedName("inputVec")
    private List<ItemParamData> qualityOutputVec; // 3 quality-tier dish results - despite the JSON key name
}