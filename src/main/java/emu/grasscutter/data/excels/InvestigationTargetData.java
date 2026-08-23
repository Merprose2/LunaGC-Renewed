package emu.grasscutter.data.excels;

import emu.grasscutter.data.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@ResourceType(
        name = "InvestigationTargetConfigData.json",
        loadPriority = ResourceType.LoadPriority.LOW)
@Getter
@FieldDefaults(level = AccessLevel.PRIVATE)
public class InvestigationTargetData extends GameResource {
    @Getter(onMethod_ = @Override)
    int id;

    int investigationId;
    int questId;
    int progress;
    int rewardId;
    String icon;
    String image;
    long infoDesTextMapHash;
    int sortOrder;
    boolean isDisuse;
}
