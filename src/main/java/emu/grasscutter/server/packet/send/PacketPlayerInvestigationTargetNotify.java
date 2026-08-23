package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.InvestigationTargetOuterClass.InvestigationTarget;
import emu.grasscutter.net.proto.PlayerInvestigationTargetNotifyOuterClass.PlayerInvestigationTargetNotify;
import java.util.List;

public class PacketPlayerInvestigationTargetNotify extends BasePacket {

    public PacketPlayerInvestigationTargetNotify(List<InvestigationTarget> targets) {
        super(PacketOpcodes.PlayerInvestigationTargetNotify);

        var proto = PlayerInvestigationTargetNotify.newBuilder()
                .addAllInvestigationTargetList(targets)
                .build();

        this.setData(proto);
    }
}
