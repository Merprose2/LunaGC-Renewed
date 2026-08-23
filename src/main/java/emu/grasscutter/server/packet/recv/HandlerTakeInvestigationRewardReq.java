package emu.grasscutter.server.packet.recv;

import com.google.protobuf.CodedInputStream;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.IAAMFCHLCGLOuterClass.IAAMFCHLCGL;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketTakeInvestigationRewardRsp;

@Opcodes(PacketOpcodes.TakeInvestigationRewardReq)
public class HandlerTakeInvestigationRewardReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        int investigationId = 0;
        try {
            var req = IAAMFCHLCGL.parseFrom(payload);
            investigationId = req.getInvestigationId();
            if (investigationId == 0) {
                investigationId = req.getStageId();
            }
            if (investigationId == 0) {
                investigationId = req.getILCINFEDJMH();
            }
        } catch (Exception ignored) {}

        if (investigationId == 0 && payload != null && payload.length > 0) {
            try {
                CodedInputStream input = CodedInputStream.newInstance(payload);
                while (!input.isAtEnd()) {
                    int tag = input.readTag();
                    if (tag == 0) break;
                    int wireType = tag & 7;
                    if (wireType == 0) {
                        int val = input.readInt32();
                        if (val > 0) {
                            investigationId = val;
                            break;
                        }
                    } else {
                        input.skipField(tag);
                    }
                }
            } catch (Exception e) {
                Grasscutter.getLogger().debug("Unable to parse TakeInvestigationRewardReq varints", e);
            }
        }

        Grasscutter.getLogger().info("TakeInvestigationRewardReq received for investigationId: {}", investigationId);

        if (investigationId > 0) {
            session.getPlayer().getInvestigationManager().takeInvestigationReward(investigationId);
            session.send(new PacketTakeInvestigationRewardRsp(investigationId));
        }
    }
}
