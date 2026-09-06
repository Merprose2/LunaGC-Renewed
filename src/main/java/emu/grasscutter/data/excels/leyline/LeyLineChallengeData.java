package emu.grasscutter.data.excels.leyline;

import emu.grasscutter.data.GameResource;
import emu.grasscutter.data.ResourceType;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import lombok.Getter;

/** Ley Line Challenge schedule entry (Stygian Onslaught). */
@ResourceType(name = "LeyLineChallengeExcelConfigData.json")
@Getter
public class LeyLineChallengeData extends GameResource {

    private static final DateTimeFormatter FORMAT =
            DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    @Getter(onMethod_ = @Override)
    private int id; // == scheduleId

    private int scheduleId;
    private String scheduleStartTime;
    private String scheduleEndTime;
    private List<Integer> levelIdList;
    private int rewardWatcherId;
    private List<Integer> NCIPGEBFJNP; // ley line dungeon group ids (1..3)
    private int PABCIOMCPMD;           // challenge limit (times?)
    private long PKIIMLOKLJK;
    private long challengeNameTextMapHash;

    /** Epoch seconds (UTC+8), resolved in onLoad. */
    private transient int startTimeEpoch;
    private transient int endTimeEpoch;

    @Override
    public void onLoad() {
        this.id = this.scheduleId;
        try {
            if (this.scheduleStartTime != null) {
                this.startTimeEpoch =
                        (int)
                                LocalDateTime.parse(this.scheduleStartTime, FORMAT)
                                        .toInstant(ZoneOffset.ofHours(8))
                                        .getEpochSecond();
            }
            if (this.scheduleEndTime != null) {
                this.endTimeEpoch =
                        (int)
                                LocalDateTime.parse(this.scheduleEndTime, FORMAT)
                                        .toInstant(ZoneOffset.ofHours(8))
                                        .getEpochSecond();
            }
        } catch (Exception ignored) {
        }
    }

    public int getScheduleId() {
        return this.scheduleId;
    }

    public int getStartTime() {
        return this.startTimeEpoch;
    }

    public int getEndTime() {
        return this.endTimeEpoch;
    }
}
