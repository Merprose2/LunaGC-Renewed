package emu.grasscutter.data.binout;

import dev.morphia.annotations.Entity;
import emu.grasscutter.data.GameData;
import emu.grasscutter.data.excels.quest.QuestData;
import emu.grasscutter.game.quest.enums.QuestType;
import java.util.*;
import lombok.Data;

public class MainQuestData {
    private int id;
    private int ICLLDPJFIMA;
    private int series;
    private QuestType type;

    private long titleTextMapHash;
    private int[] suggestTrackMainQuestList;
    private int[] rewardIdList;

    private QuestData[] subQuests;
    private List<TalkData> talks;
    private String[] preloadLuaList;

    public String[] getPreloadLuaList() {
        return preloadLuaList;
    }

    public int getId() {
        return id;
    }

    public int getSeries() {
        return series;
    }

    public QuestType getType() {
        return type;
    }

    public long getTitleTextMapHash() {
        return titleTextMapHash;
    }

    public int[] getSuggestTrackMainQuestList() {
        return suggestTrackMainQuestList;
    }

    public int[] getRewardIdList() {
        return rewardIdList;
    }

    public QuestData[] getSubQuests() {
        return subQuests;
    }

    public List<TalkData> getTalks() {
        return talks;
    }

    public void onLoad() {
        if (this.talks == null) this.talks = new ArrayList<>();
        if (this.subQuests == null) this.subQuests = new QuestData[0];

        this.talks = this.talks.stream().filter(Objects::nonNull).toList();
        // Apply talk data to the quest talk map.
        this.talks.forEach(talkData -> GameData.getQuestTalkMap().put(talkData.getId(), this.getId()));

        // Apply and register subquests
        for (var quest : this.subQuests) {
            if (quest == null) continue;
            if (quest.getMainId() == 0) {
                quest.setMainId(this.getId());
            }
            var existing = GameData.getQuestDataMap().get(quest.getSubId());
            if (existing != null) {
                existing.mergeFrom(quest);
            } else {
                quest.onLoad();
                GameData.getQuestDataMap().put(quest.getSubId(), quest);
            }
        }
    }

    @Deprecated
    public static class SubQuestData extends QuestData {}

    @Data
    @Entity
    public static class TalkData {
        private int id;
        private String heroTalk;

        public TalkData() {}

        public TalkData(int id, String heroTalk) {
            this.id = id;
            this.heroTalk = heroTalk;
        }
    }
}
