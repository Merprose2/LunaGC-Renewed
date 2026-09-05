package emu.grasscutter.data.excels.activity;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.GameResource;
import emu.grasscutter.data.ResourceType;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@ResourceType(
        name = "NewActivityExcelConfigData.json",
        loadPriority = ResourceType.LoadPriority.LOW)
public class ActivityData extends GameResource {
    private int activityId;
    private String activityType;
    private List<Integer> condGroupId;
    private List<Integer> watcherId;
    private List<ActivityWatcherData> watcherDataList;

    @Override
    public int getId() {
        return activityId;
    }

    @Override
    public void onLoad() {
        super.onLoad();

        condGroupId = cleanIds(condGroupId);
        watcherId = cleanIds(watcherId);

        watcherDataList = watcherId.stream()
                .map(id -> GameData.getActivityWatcherDataMap().get(id.intValue()))
                .filter(Objects::nonNull)
                .toList();
    }

    private static List<Integer> cleanIds(List<Integer> ids) {
        if (ids == null) {
            return new ArrayList<>();
        }

        return ids.stream()
                .filter(Objects::nonNull)
                .filter(id -> id > 0)
                .collect(Collectors.toList());
    }

    public int getActivityId() {
        return activityId;
    }

    public String getActivityType() {
        return activityType;
    }

    public List<Integer> getCondGroupId() {
        return condGroupId;
    }

    public List<Integer> getWatcherId() {
        return watcherId;
    }

    public List<ActivityWatcherData> getWatcherDataList() {
        return watcherDataList;
    }
}