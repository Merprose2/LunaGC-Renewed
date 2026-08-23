package emu.grasscutter.game.investigation;

import dev.morphia.annotations.Entity;
import emu.grasscutter.net.proto.InvestigationTargetOuterClass.InvestigationTarget;
import lombok.*;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerInvestigationTarget {
    private int targetId;
    private int investigationId;
    private int questId;
    private int progress;
    private int totalProgress;
    private int state; // InvestigationTarget.State: 1 = IN_PROGRESS, 2 = COMPLETE, 3 = REWARD_TAKEN

    public InvestigationTarget toProto() {
        return InvestigationTarget.newBuilder()
                .setInvestigationId(this.investigationId)
                .setQuestId(this.targetId) // The proto field quest_id is the target config ID (e.g. 60001, 64001)
                .setProgress(this.progress)
                .setTotalProgress(this.totalProgress)
                .setStateValue(this.state)
                .build();
    }
}
