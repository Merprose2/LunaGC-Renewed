package emu.grasscutter.server.packet.recv;

import com.google.protobuf.CodedInputStream;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketTakeInvestigationTargetRewardRsp;

@Opcodes(PacketOpcodes.TakeInvestigationTargetRewardReq)
public class HandlerTakeInvestigationTargetRewardReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        int targetId = 0;
        try {
            CodedInputStream input = CodedInputStream.newInstance(payload);
            while (!input.isAtEnd()) {
                int tag = input.readTag();
                if (tag == 0) break;
                int wireType = tag & 7;
                if (wireType == 0) { // Varint
                    int val = input.readInt32();
                    if (val > 0) {
                        targetId = val;
                        break;
                    }
                } else {
                    input.skipField(tag);
                }
            }
        } catch (Exception e) {
            Grasscutter.getLogger().debug("Unable to parse TakeInvestigationTargetRewardReq", e);
        }

        Grasscutter.getLogger().info("TakeInvestigationTargetRewardReq received for targetId: {}", targetId);

        if (targetId > 0) {
            session.getPlayer().getInvestigationManager().takeInvestigationTargetReward(targetId);
            session.send(new PacketTakeInvestigationTargetRewardRsp(targetId));
        }
    }
}
