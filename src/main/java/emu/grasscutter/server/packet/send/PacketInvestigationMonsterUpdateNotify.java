package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.InvestigationMonsterOuterClass.InvestigationMonster;
import emu.grasscutter.net.proto.InvestigationMonsterUpdateNotifyOuterClass.InvestigationMonsterUpdateNotify;

public class PacketInvestigationMonsterUpdateNotify extends BasePacket {

    public PacketInvestigationMonsterUpdateNotify(InvestigationMonster monster) {
        super(PacketOpcodes.InvestigationMonsterUpdateNotify);

        var proto = InvestigationMonsterUpdateNotify.newBuilder()
                .setInvestigationMonster(monster)
                .build();

        this.setData(proto);
    }
}
