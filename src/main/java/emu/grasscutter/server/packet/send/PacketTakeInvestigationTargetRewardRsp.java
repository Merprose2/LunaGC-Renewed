package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.IAAMFCHLCGLOuterClass.IAAMFCHLCGL;

public class PacketTakeInvestigationTargetRewardRsp extends BasePacket {

    public PacketTakeInvestigationTargetRewardRsp(int targetId) {
        super(PacketOpcodes.TakeInvestigationTargetRewardRsp);

        var proto = IAAMFCHLCGL.newBuilder()
                .setRetcode(0)
                .setInvestigationId(targetId)
                .build();

        this.setData(proto);
    }
}
