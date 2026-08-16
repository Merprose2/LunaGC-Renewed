package emu.grasscutter.game.dailytask;

import dev.morphia.annotations.Entity;
import emu.grasscutter.data.GameData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.proto.DailyTaskInfoOuterClass.DailyTaskInfo;
import javax.annotation.Nullable;
import lombok.Getter;
import java.util.HashSet;
import java.util.Set;

@Getter
@Entity
public class DailyTask {
    private int rewardId;
    private int taskId;
    private int finishProgress;
    private int progress;
    private boolean finished;

	/*
	 * Monsters already legitimately defeated for this specific daily task.
	 *
	 * We store a composite (groupId, configId) key rather than only configId,
	 * because config IDs are only guaranteed to be unique inside a group.
	 *
	 * This state is persisted with DailyTask, so leaving the area, teleporting,
	 * relogging or restarting the server cannot make already-defeated commission
	 * enemies valid targets again.
	 */
	private Set<Long> defeatedMonsterKeys = new HashSet<>();

    public DailyTask() {}

    private DailyTask(
            int rewardId,
            int taskId,
            int finishProgress,
            int progress,
            boolean finished) {
        this.rewardId = rewardId;
        this.taskId = taskId;
        this.finishProgress = finishProgress;
        this.progress = progress;
        this.finished = finished;
    }

    @Nullable
    public static DailyTask create(Player owner, int dailyTaskId) {
        var data = GameData.getDailyTaskDataMap().get(dailyTaskId);

        if (data == null || owner.getDailyTaskManager() == null) {
            return null;
        }

        int rewardId =
                owner.getDailyTaskManager()
                        .getRewardId(data.getTaskRewardId());

        return new DailyTask(
                rewardId,
                dailyTaskId,
                data.getFinishProgress(),
                0,
                false);
    }

    public boolean addProgress(int amount) {
        if (this.finished || amount <= 0) {
            return false;
        }

        int oldProgress = this.progress;

        this.progress =
                Math.min(
                        this.finishProgress,
                        this.progress + amount);

        if (this.progress >= this.finishProgress) {
            this.finished = true;
        }

        return this.progress != oldProgress;
    }

    public boolean finish() {
        if (this.finished) {
            return false;
        }

        this.progress = this.finishProgress;
        this.finished = true;
        return true;
    }

	private static long createMonsterKey(
			int groupId,
			int configId) {
		return ((long) groupId << 32)
				| (configId & 0xffffffffL);
	}

	private Set<Long> getOrCreateDefeatedMonsterKeys() {
		/*
		 * Old Mongo documents created before this field existed will deserialize
		 * with null here. Handle that transparently.
		 */
		if (this.defeatedMonsterKeys == null) {
			this.defeatedMonsterKeys =
					new HashSet<>();
		}

		return this.defeatedMonsterKeys;
	}

	public boolean markMonsterDefeated(
			int groupId,
			int configId) {
		if (groupId <= 0 || configId <= 0) {
			return false;
		}

		return this.getOrCreateDefeatedMonsterKeys()
				.add(
						createMonsterKey(
								groupId,
								configId));
	}

	public boolean isMonsterDefeated(
			int groupId,
			int configId) {
		if (groupId <= 0 || configId <= 0) {
			return false;
		}

		return this.getOrCreateDefeatedMonsterKeys()
				.contains(
						createMonsterKey(
								groupId,
								configId));
	}

    public DailyTaskInfo toProto() {
        return DailyTaskInfo.newBuilder()
                .setRewardId(this.rewardId)
                .setDailyTaskId(this.taskId)
                .setFinishProgress(this.finishProgress)
                .setProgress(this.progress)
                .setIsFinished(this.finished)
                .build();
    }
}