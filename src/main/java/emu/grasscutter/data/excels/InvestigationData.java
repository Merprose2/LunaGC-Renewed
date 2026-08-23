package emu.grasscutter.data.excels;

import emu.grasscutter.data.*;
import java.util.List;
import lombok.*;
import lombok.experimental.FieldDefaults;

@ResourceType(
        name = "InvestigationConfigData.json",
        loadPriority = ResourceType.LoadPriority.LOW)
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InvestigationData extends GameResource {
    @Getter(onMethod_ = @Override)
    int id;

    int cityId;
    String investigationType;
    boolean isRewardEmpty;
    List<Integer> nextInvestigationIdList;
    int rewardId;
    long titleTextMapHash;
    int unlockLevel;
    String unlockOpenStateType;

    CityData cityData;

    @Override
    public void onLoad() {
        if (cityId > 0) {
            this.cityData = GameData.getCityDataMap().get(cityId);
        }
    }
}
