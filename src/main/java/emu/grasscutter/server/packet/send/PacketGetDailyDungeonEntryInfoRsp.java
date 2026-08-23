package emu.grasscutter.server.packet.send;

import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.dungeon.DungeonEntryData;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.*;
import java.util.Calendar;

public class PacketGetDailyDungeonEntryInfoRsp extends BasePacket {

    public PacketGetDailyDungeonEntryInfoRsp(Integer sceneID) {
        super(PacketOpcodes.GetDailyDungeonEntryInfoRsp);

        var resp = GetDailyDungeonEntryInfoRspOuterClass.GetDailyDungeonEntryInfoRsp.newBuilder();

        var stream = GameData.getDungeonEntryDataMap().values().stream();
        if (sceneID != null && sceneID > 0) {
            stream = stream.filter(d -> d.getSceneId() == sceneID || d.getSceneId() == 3);
        }

        for (var data : stream.toList()) {
            resp.addDailyDungeonInfoList(getDungeonEntryInfo(data));
        }

        this.setData(resp.build());
    }

    private DailyDungeonEntryInfoOuterClass.DailyDungeonEntryInfo getDungeonEntryInfo(
            DungeonEntryData data) {
        var dungeonEntryId = data.getDungeonEntryId();
        var id = data.getId();

        var builder = DailyDungeonEntryInfoOuterClass.DailyDungeonEntryInfo.newBuilder();
        builder.setDungeonEntryId(dungeonEntryId);
        builder.setDungeonEntryConfigId(id);
        builder.setIsQuickOpen(true);

        var dailyData = GameData.getDailyDungeonDataMap().get(id);
        if (dailyData == null) {
            dailyData = GameData.getDailyDungeonDataMap().get(dungeonEntryId);
        }

        int dayOfWeek = Calendar.getInstance().get(Calendar.DAY_OF_WEEK);
        int recommendDungeonId = 0;

        if (dailyData != null) {
            int[] dailyDungeons = dailyData.getDungeonsByDay(dayOfWeek);
            if (dailyDungeons != null && dailyDungeons.length > 0) {
                for (int dId : dailyDungeons) {
                    builder.addHAOIOGCMAIM(dId);
                }
                recommendDungeonId = dailyDungeons[dailyDungeons.length - 1];
            }
        }

        if (recommendDungeonId == 0) {
            recommendDungeonId = 130;
        }

        builder.setRecommendDungeonId(recommendDungeonId);
        builder.setRecommendDungeonEntryInfo(
                DungeonEntryInfoOuterClass.DungeonEntryInfo.newBuilder()
                        .setDungeonId(recommendDungeonId)
                        .setIsPassed(true)
                        .build());

        return builder.build();
    }
}
