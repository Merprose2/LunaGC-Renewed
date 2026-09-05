package emu.grasscutter.game.battlepass;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.BattlePassMissionData;
import emu.grasscutter.game.player.Player;
import emu.grasscutter.game.props.*;
import emu.grasscutter.server.event.player.PlayerFinishBattlePassMission;
import emu.grasscutter.server.game.*;
import emu.grasscutter.server.packet.send.PacketBattlePassMissionUpdateNotify;
import java.util.*;

public class BattlePassSystem extends BaseGameSystem {
    private final Map<WatcherTriggerType, List<BattlePassMissionData>> cachedTriggers;

    public BattlePassSystem(GameServer server) {
        super(server);
        this.cachedTriggers = new HashMap<>();
        this.loadTriggers();
    }

    public synchronized void loadTriggers() {
        this.cachedTriggers.clear();
        if (GameData.getBattlePassMissionDataMap() == null || GameData.getBattlePassMissionDataMap().isEmpty()) {
            return;
        }

        for (BattlePassMissionData missionData : GameData.getBattlePassMissionDataMap().values()) {
            if (missionData.isValidRefreshType() && missionData.getTriggerType() != null) {
                List<BattlePassMissionData> triggerList =
                        this.cachedTriggers.computeIfAbsent(missionData.getTriggerType(), e -> new ArrayList<>());
                triggerList.add(missionData);
            }
        }
        Grasscutter.getLogger().info("BattlePassSystem loaded {} BP trigger types ({} total missions).",
                this.cachedTriggers.size(),
                this.cachedTriggers.values().stream().mapToInt(List::size).sum());
    }

    public GameServer getServer() {
        return server;
    }

    private Map<WatcherTriggerType, List<BattlePassMissionData>> getTriggers() {
        if (this.cachedTriggers.isEmpty()) {
            this.loadTriggers();
        }
        return this.cachedTriggers;
    }

    public void triggerMission(Player player, WatcherTriggerType triggerType) {
        triggerMission(player, triggerType, 0, 1);
    }

    public void triggerMission(
            Player player, WatcherTriggerType triggerType, int param, int progress) {
        List<BattlePassMissionData> triggerList = getTriggers().get(triggerType);

        if (triggerList == null || triggerList.isEmpty()) {
            Grasscutter.getLogger().debug("No BP triggers found for type {}", triggerType);
            return;
        }

        for (BattlePassMissionData data : triggerList) {
            // Only check parameter if the mission configuration actually requires specific IDs.
            // If mainParams is empty, it means the mission accepts any parameter (e.g. any gacha banner).
            if (param != 0 && data.getMainParams() != null && !data.getMainParams().isEmpty()) {
                if (!data.getMainParams().contains(param)) {
                    continue;
                }
            }

            BattlePassMission mission = player.getBattlePassManager().loadMissionById(data.getId());
            if (mission.isFinshed()) continue;

            // Add progress
            mission.addProgress(progress, data.getProgress());

            if (mission.getProgress() >= data.getProgress()) {
                mission.setStatus(BattlePassMissionStatus.MISSION_STATUS_FINISHED);
                new PlayerFinishBattlePassMission(player, mission).call();
            }

            player.getBattlePassManager().save();
            player.sendPacket(new PacketBattlePassMissionUpdateNotify(mission));
            Grasscutter.getLogger().info("BP mission {} ({}) updated progress: {}/{}",
                    data.getId(), triggerType, mission.getProgress(), data.getProgress());
        }
    }
}
