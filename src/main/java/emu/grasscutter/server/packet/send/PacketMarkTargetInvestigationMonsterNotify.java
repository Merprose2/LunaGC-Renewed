package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto._InvestigationMonsterConfigOuterClass;
import emu.grasscutter.net.proto.MarkTargetInvestigationMonsterNotifyOuterClass.MarkTargetInvestigationMonsterNotify;

public class PacketMarkTargetInvestigationMonsterNotify extends BasePacket {

    public PacketMarkTargetInvestigationMonsterNotify(int monsterId, int sceneId, int groupId) {
        super(PacketOpcodes.MarkTargetInvestigationMonsterNotify);

        var config = _InvestigationMonsterConfigOuterClass._InvestigationMonsterConfig.newBuilder()
                .setMonsterId(monsterId)
                .setSceneId(sceneId)
                .setGroupId(groupId)
                .build();

        var proto = MarkTargetInvestigationMonsterNotify.newBuilder()
                .setInvestigationMonsterId(monsterId)
                .setGHPPPJJLDJN(config)
                .build();

        this.setData(proto);
    }
}
