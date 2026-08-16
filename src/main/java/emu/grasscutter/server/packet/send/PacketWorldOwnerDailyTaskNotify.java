package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.dailytask.DailyTask;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.WorldOwnerDailyTaskNotifyOuterClass.WorldOwnerDailyTaskNotify;
import java.util.List;

public class PacketWorldOwnerDailyTaskNotify extends BasePacket {
    public PacketWorldOwnerDailyTaskNotify(Player player) {
        this(
                player,
                player.getDailyTaskManager()
                        .getDailyTasks(),
                player.getDailyTaskManager()
                        .getCityId());
    }

    /*
     * Development/testing constructor.
     *
     * Allows /dt preview to present a temporary task list to one client
     * without modifying or persisting the player's real daily quota.
     */
    public PacketWorldOwnerDailyTaskNotify(
            Player player,
            List<DailyTask> tasks,
            int filterCityId) {
        super(PacketOpcodes.WorldOwnerDailyTaskNotify);

        int finishedCount =
                (int)
                        tasks.stream()
                                .filter(DailyTask::isFinished)
                                .count();

        var notify =
                WorldOwnerDailyTaskNotify.newBuilder()
                        .setFilterCityId(filterCityId)
                        .setFinishedDailyTaskNum(finishedCount);

        tasks.forEach(
                task ->
                        notify.addTaskList(
                                task.toProto()));

        this.setData(
                notify.build());
    }
}