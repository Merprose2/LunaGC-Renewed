package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.IAAMFCHLCGLOuterClass.IAAMFCHLCGL;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.TakeInvestigationRewardReq)
public class HandlerTakeInvestigationRewardReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        int investigationId = 0;
        try {
            var req = IAAMFCHLCGL.parseFrom(payload);
            investigationId = req.getInvestigationId();
        } catch (Exception e) {
            Grasscutter.getLogger().debug("Unable to parse TakeInvestigationRewardReq as IAAMFCHLCGL", e);
        }

        if (investigationId > 0) {
            session.getPlayer().getInvestigationManager().takeInvestigationReward(investigationId);
        }
    }
}
