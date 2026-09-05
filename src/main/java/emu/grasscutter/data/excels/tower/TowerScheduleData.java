package emu.grasscutter.data.excels.tower;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.data.GameResource;
import emu.grasscutter.data.ResourceType;
import java.util.List;
import java.util.Objects;

@ResourceType(name = "TowerScheduleExcelConfigData.json")
public class TowerScheduleData extends GameResource {
    private int scheduleId;
    private List<Integer> entranceFloorId;

    @SerializedName(value = "schedules", alternate = {"LHOGNLPBILP"})
    private List<ScheduleDetail> schedules;

    private int monthlyLevelConfigId;

    @Override
    public int getId() {
        return scheduleId;
    }

    @Override
    public void onLoad() {
        super.onLoad();

        if (entranceFloorId == null) {
            entranceFloorId = List.of();
        }

        if (schedules == null) {
            schedules = List.of();
            return;
        }

        schedules = schedules.stream()
                .filter(Objects::nonNull)
                .filter(detail -> detail.getFloorList() != null)
                .filter(detail -> !detail.getFloorList().isEmpty())
                .toList();
    }

    public int getScheduleId() {
        return scheduleId;
    }

    public List<Integer> getEntranceFloorId() {
        return entranceFloorId;
    }

    public List<ScheduleDetail> getSchedules() {
        return schedules;
    }

    public int getMonthlyLevelConfigId() {
        return monthlyLevelConfigId;
    }

    public static class ScheduleDetail {
        private List<Integer> floorList;

        public List<Integer> getFloorList() {
            return floorList;
        }
    }
}