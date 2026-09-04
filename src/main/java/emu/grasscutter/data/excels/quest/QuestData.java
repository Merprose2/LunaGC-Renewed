package emu.grasscutter.data.excels.quest;

import com.google.gson.annotations.SerializedName;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.data.*;
import emu.grasscutter.data.binout.MainQuestData;
import emu.grasscutter.data.common.ItemParamData;
import emu.grasscutter.game.quest.enums.*;
import java.util.*;
import javax.annotation.*;
import lombok.*;
import lombok.experimental.FieldDefaults;

@ResourceType(name = "QuestExcelConfigData.json")
@Getter
@ToString
public class QuestData extends GameResource {
    @Getter @Setter private int subId;
    @Getter @Setter private int mainId;
    @Getter @Setter private int order;
    @Getter @Setter private int subIdSet;
    @Getter @Setter private long descTextMapHash;
    @Getter @Setter private long stepDescTextMapHash;
    @Getter @Setter private long guideTipsTextMapHash;
    @Getter @Setter private String showType;

    @Getter @Setter private boolean finishParent;
    @Getter @Setter private boolean isRewind;
    @Getter @Setter private boolean isMpBlock;

    @Getter @Setter private LogicType acceptCondComb;
    @Getter @Setter private LogicType finishCondComb;
    @Getter @Setter private LogicType failCondComb;

    @Getter @Setter private List<QuestAcceptCondition> acceptCond;
    @Getter @Setter private List<QuestContentCondition> finishCond;
    @Getter @Setter private List<QuestContentCondition> failCond;
    @Getter @Setter private List<QuestExecParam> beginExec;
    @Getter @Setter private List<QuestExecParam> finishExec;
    @Getter @Setter private List<QuestExecParam> failExec;
    @Getter @Setter private Guide guide;

    @Getter @Setter private List<Integer> trialAvatarList;
    @Getter @Setter private List<ItemParamData> gainItems;
    @Getter @Setter private List<Integer> exclusivePlaceList;

    public static String questConditionKey(
            @Nonnull Enum<?> type, int firstParam, @Nullable String paramsStr) {
        return type.name() + firstParam + (paramsStr != null ? paramsStr : "");
    }

    // ResourceLoader not happy if you remove getId() ~~
    public int getId() {
        return subId;
    }

    public void onLoad() {
        if (this.acceptCond == null) this.acceptCond = Collections.emptyList();
        if (this.finishCond == null) this.finishCond = Collections.emptyList();
        if (this.failCond == null) this.failCond = Collections.emptyList();
        if (this.beginExec == null) this.beginExec = Collections.emptyList();
        if (this.finishExec == null) this.finishExec = Collections.emptyList();
        if (this.failExec == null) this.failExec = Collections.emptyList();

        this.acceptCond = acceptCond.stream().filter(p -> p != null && p.getType() != null).toList();
        this.finishCond = finishCond.stream().filter(p -> p != null && p.getType() != null).toList();
        this.failCond = failCond.stream().filter(p -> p != null && p.getType() != null).toList();

        this.beginExec = beginExec.stream().filter(p -> p != null && p.type != null).toList();
        this.finishExec = finishExec.stream().filter(p -> p != null && p.type != null).toList();
        this.failExec = failExec.stream().filter(p -> p != null && p.type != null).toList();

        if (this.acceptCondComb == null) this.acceptCondComb = LogicType.LOGIC_NONE;

        if (this.finishCondComb == null) this.finishCondComb = LogicType.LOGIC_NONE;

        if (this.failCondComb == null) this.failCondComb = LogicType.LOGIC_NONE;

        if (this.gainItems == null) this.gainItems = Collections.emptyList();

        this.addToCache();
    }

    public void applyFrom(MainQuestData.SubQuestData additionalData) {
        this.isRewind = additionalData.isRewind();
        this.finishParent = additionalData.isFinishParent();
    }

    public void mergeFrom(QuestData other) {
        if (other == null) return;
        this.isRewind = other.isRewind;
        this.finishParent = other.finishParent;
        this.isMpBlock = other.isMpBlock;

        if (other.order != 0) this.order = other.order;
        if (other.mainId != 0) this.mainId = other.mainId;
        if (other.descTextMapHash != 0) this.descTextMapHash = other.descTextMapHash;
        if (other.stepDescTextMapHash != 0) this.stepDescTextMapHash = other.stepDescTextMapHash;
        if (other.guideTipsTextMapHash != 0) this.guideTipsTextMapHash = other.guideTipsTextMapHash;
        if (other.showType != null && !other.showType.isEmpty()) this.showType = other.showType;
        if (other.subIdSet != 0) this.subIdSet = other.subIdSet;
        if (other.exclusivePlaceList != null && !other.exclusivePlaceList.isEmpty()) this.exclusivePlaceList = other.exclusivePlaceList;

        if (other.finishCond != null && !other.finishCond.isEmpty()) {
            this.finishCond = other.finishCond.stream().filter(p -> p != null && p.getType() != null).toList();
        }
        if (other.finishExec != null && !other.finishExec.isEmpty()) {
            this.finishExec = other.finishExec.stream().filter(p -> p != null && p.type != null).toList();
        }
        if (other.failCond != null && !other.failCond.isEmpty()) {
            this.failCond = other.failCond.stream().filter(p -> p != null && p.getType() != null).toList();
        }
        if (other.failExec != null && !other.failExec.isEmpty()) {
            this.failExec = other.failExec.stream().filter(p -> p != null && p.type != null).toList();
        }
        if (other.beginExec != null && !other.beginExec.isEmpty()) {
            this.beginExec = other.beginExec.stream().filter(p -> p != null && p.type != null).toList();
        }
        if (other.guide != null) {
            this.guide = other.guide;
        }
        if (other.finishCondComb != null && other.finishCondComb != LogicType.LOGIC_NONE) {
            this.finishCondComb = other.finishCondComb;
        }
        if (other.failCondComb != null && other.failCondComb != LogicType.LOGIC_NONE) {
            this.failCondComb = other.failCondComb;
        }
        if (other.acceptCond != null && !other.acceptCond.isEmpty()) {
            this.acceptCond = other.acceptCond.stream().filter(p -> p != null && p.getType() != null).toList();
            if (other.acceptCondComb != null && other.acceptCondComb != LogicType.LOGIC_NONE) {
                this.acceptCondComb = other.acceptCondComb;
            }
            this.addToCache();
        }
        if (other.gainItems != null && !other.gainItems.isEmpty()) {
            this.gainItems = other.gainItems;
        }
        if (other.trialAvatarList != null && !other.trialAvatarList.isEmpty()) {
            this.trialAvatarList = other.trialAvatarList;
        }
    }

    private void addToCache() {
        if (this.acceptCond == null) {
            Grasscutter.getLogger().warn("missing AcceptConditions for quest {}", getSubId());
            return;
        }

        var cacheMap = GameData.getBeginCondQuestMap();
        if (getAcceptCond().isEmpty()) {
            var list =
                    cacheMap.computeIfAbsent(
                            QuestData.questConditionKey(QuestCond.QUEST_COND_NONE, 0, null),
                            e -> new ArrayList<>());
            list.add(this);
        } else {
            this.getAcceptCond()
                    .forEach(
                            questCondition -> {
                                if (questCondition.getType() == null) {
                                    Grasscutter.getLogger().warn("null accept type for quest {}", getSubId());
                                    return;
                                }

                                var key = questCondition.asKey();
                                var list = cacheMap.computeIfAbsent(key, e -> new ArrayList<>());
                                list.add(this);
                            });
        }
    }

    @Data
    @FieldDefaults(level = AccessLevel.PRIVATE)
    public static class QuestExecParam {
        @SerializedName(
                value = "_type",
                alternate = {"type"})
        QuestExec type;

        @SerializedName(
                value = "_param",
                alternate = {"param"})
        String[] param;

        @SerializedName(
                value = "_count",
                alternate = {"count"})
        String count;
    }

    public static class QuestAcceptCondition extends QuestCondition<QuestCond> {}

    public static class QuestContentCondition extends QuestCondition<QuestContent> {}

    @Data
    public static class QuestCondition<TYPE extends Enum<?> & QuestTrigger> {
        @SerializedName(
                value = "_type",
                alternate = {"type"})
        private TYPE type;

        @SerializedName(
                value = "_param",
                alternate = {"param"})
        private int[] param;

        @SerializedName(
                value = "_param_str",
                alternate = {"param_str"})
        private String paramStr = "";

        @SerializedName(
                value = "_count",
                alternate = {"count"})
        private int count;

        public String asKey() {
            return questConditionKey(getType(), getParam()[0], getParamStr());
        }
    }

    @Data
    public static class Guide {
        private String type;
        private List<String> param;
        private int guideScene;
        private String guideStyle;
        private String guideLayer;
    }
}
