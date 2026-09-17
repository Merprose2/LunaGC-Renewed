package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.TakeBattlePassRewardReqOuterClass.TakeBattlePassRewardReq;
import emu.grasscutter.server.game.GameSession;

@Opcodes(PacketOpcodes.TakeBattlePassRewardReq)
public class HandlerTakeBattlePassRewardReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        var req = TakeBattlePassRewardReq.parseFrom(payload);
        var takeOptions = req.getTakeOptionListList();

        // The client packs the options into field 10 of this message. An empty list here
        // therefore always means the proto field number drifted, which used to fail silently.
        if (takeOptions.isEmpty()) {
            Grasscutter.getLogger()
                    .warn(
                            "TakeBattlePassRewardReq had no take_option_list (proto field 10) - nothing to grant. "
                                    + "Check TakeBattlePassRewardReqOuterClass for a field number mismatch.");
        }

        session.getPlayer().getBattlePassManager().takeReward(takeOptions);
    }
}
