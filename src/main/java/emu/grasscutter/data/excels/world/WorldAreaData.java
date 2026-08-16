package emu.grasscutter.data.excels.world;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.*;
import emu.grasscutter.game.props.AreaType;
import emu.grasscutter.game.props.ElementType;
import lombok.Getter;

@ResourceType(name = "WorldAreaConfigData.json")
public class WorldAreaData extends GameResource {
    @SerializedName("id")
    private int ID;
    @Getter private ElementType elementType;

    @Getter
    @SerializedName("areaNameTextMapHash")
    private long textMapHash;

    @Getter
    @SerializedName("areaID1")
    private int parentArea;

    @Getter
    @SerializedName("areaID2")
    private int childArea;

    @Getter
    @SerializedName("sceneID")
    private int sceneId;

    @Override
    public int getId() {
        return (this.childArea << 16) + this.parentArea;
    }

    @Override
    public void onLoad() {
        if (this.getChildArea() == 0) {
            GameData.getWorldAreaParentDataMap().put(this.getParentArea(), this);
        } else {
            GameData.getWorldAreaChildDataMap().put(this.getChildArea(), this);
        }
    }

    /**
     * @param areaId   the raw area id from the request
     * @param areaType the area type from the request (LEVEL_1 or LEVEL_2)
     */
    public static WorldAreaData getByAreaId(int areaId, AreaType areaType) {
        return areaType == AreaType.LEVEL_2
                ? GameData.getWorldAreaChildDataMap().get(areaId)
                : GameData.getWorldAreaParentDataMap().get(areaId);
    }
}
