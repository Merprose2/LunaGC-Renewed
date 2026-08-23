package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.IAAMFCHLCGLOuterClass.IAAMFCHLCGL;

public class PacketTakeInvestigationRewardRsp extends BasePacket {

    public PacketTakeInvestigationRewardRsp(int investigationId) {
        super(PacketOpcodes.TakeInvestigationRewardRsp);

        var proto = IAAMFCHLCGL.newBuilder()
                .setRetcode(0)
                .setInvestigationId(investigationId)
                .build();

        this.setData(proto);
    }
}
