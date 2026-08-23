package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.InvestigationOuterClass.Investigation;
import emu.grasscutter.net.proto.PlayerInvestigationAllInfoNotifyOuterClass.PlayerInvestigationAllInfoNotify;
import emu.grasscutter.net.proto.PlayerInvestigationTargetNotifyOuterClass.PlayerInvestigationTargetNotify;
import java.util.List;

public class PacketPlayerInvestigationAllInfoNotify extends BasePacket {

    public PacketPlayerInvestigationAllInfoNotify(
            List<Investigation> investigations,
            PlayerInvestigationTargetNotify targetNotify) {
        super(PacketOpcodes.PlayerInvestigationAllInfoNotify);

        var proto = PlayerInvestigationAllInfoNotify.newBuilder()
                .addAllInvestigationList(investigations);

        if (targetNotify != null) {
            proto.setInvestigationTargetList(targetNotify.toByteString());
        }

        this.setData(proto.build());
    }
}
