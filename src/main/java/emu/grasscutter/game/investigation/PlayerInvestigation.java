package emu.grasscutter.game.investigation;

import dev.morphia.annotations.Entity;
import emu.grasscutter.net.proto.InvestigationOuterClass.Investigation;
import lombok.*;

@Entity
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PlayerInvestigation {
    private int id;
    private int progress;
    private int totalProgress;
    private int state; // Investigation.State: 1 = IN_PROGRESS, 2 = COMPLETE, 3 = REWARD_TAKEN

    public Investigation toProto() {
        return Investigation.newBuilder()
                .setId(this.id)
                .setProgress(this.progress)
                .setTotalProgress(this.totalProgress)
                .setStateValue(this.state)
                .build();
    }
}
