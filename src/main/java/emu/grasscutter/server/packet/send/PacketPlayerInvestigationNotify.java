package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.InvestigationOuterClass.Investigation;
import emu.grasscutter.net.proto.PlayerInvestigationNotifyOuterClass.PlayerInvestigationNotify;
import java.util.List;

public class PacketPlayerInvestigationNotify extends BasePacket {

    public PacketPlayerInvestigationNotify(List<Investigation> investigations) {
        super(PacketOpcodes.PlayerInvestigationNotify);

        var proto = PlayerInvestigationNotify.newBuilder()
                .addAllInvestigationList(investigations)
                .build();

        this.setData(proto);
    }
}
