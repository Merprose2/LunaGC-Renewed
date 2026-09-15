package emu.grasscutter.data.excels;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.*;
import java.util.List;
import lombok.Getter;

@ResourceType(name = "BlossomRefreshExcelConfigData.json")
@Getter
public class BlossomRefreshExcelConfigData extends GameResource {
    @Getter(onMethod_ = @Override)
    private int id;
    // Map details
    private long nameTextMapHash;
    private long descTextMapHash;
    private String icon;
    private String clientShowType; // BLOSSOM_SHOWTYPE_CHALLENGE, BLOSSOM_SHOWTYPE_NPCTALK

    // Refresh details
    private String refreshType; // Leyline blossoms, magical ore outcrops
    private int refreshCount; // Number of entries to spawn at refresh (1 for each leyline type for each city,
    // 4 for magical ore for each city)
    private String refreshTime; // Server time-of-day to refresh at
    private RefreshCond[] refreshCondVec; // AR requirements etc.

    private int cityId;

    /**
     * The resource dumps name this key either {@code blossomChestId} or {@code blossom_chest_id} (1 for mora, 2 for
     * exp), so both spellings have to be accepted.
     */
    @SerializedName(value = "blossomChestId", alternate = {"blossom_chest_id"})
    private int blossomChestId;

    /**
     * The resource dumps contain two spellings of this table: {@code DropVec} (with camelCase entries) and {@code
     * dropVec} (with snake_case entries). Both hold the same reward table, and both spellings are accepted so that no
     * entry is silently dropped when Gson can not map it to this field.
     */
    @SerializedName(value = "dropVec", alternate = {"DropVec"})
    private Drop[] dropVec;

    // Unknown details
    // @Getter private int reviseLevel;
    // @Getter private int campUpdateNeedCount;  // Always 1 if specified

    @Getter
    public static class Drop {
        @SerializedName(value = "dropId", alternate = {"drop_id"})
        int dropId;

        @SerializedName(value = "previewReward", alternate = {"preview_reward"})
        int previewReward;
    }

    @Getter
    public static class RefreshCond {
        String type;
        List<Integer> param;
    }
}
