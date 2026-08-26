package emu.grasscutter.server.packet.recv;

import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.RetcodeOuterClass.Retcode;
import emu.grasscutter.net.proto._TakeDailyTaskScoreRewardReqOuterClass._TakeDailyTaskScoreRewardReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketTakeDailyTaskScoreRewardRsp;

@Opcodes(PacketOpcodes._TakeDailyTaskScoreRewardReq)
public class HandlerTakeDailyTaskScoreRewardReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
		
        _TakeDailyTaskScoreRewardReq req = _TakeDailyTaskScoreRewardReq.parseFrom(payload);

        var player = session.getPlayer();

        player.loadDailyTaskManager();

        var manager = player.getDailyTaskManager();

        int rewardId = manager.getScoreRewardId();

        int retcode;

        if (manager.isScoreRewardTaken()) {
            retcode = Retcode.RET_DAILY_TAKS_HAS_TAKEN_VALUE;
			
        } else if (manager.getFinishedCount() < 4) {
            retcode = Retcode.RET_DAILY_TASK_NOT_FINISH_VALUE;
			
        } else if (!manager.claimScoreReward()) {
            retcode = Retcode.RET_FAIL_VALUE;
			
        } else {
            retcode = Retcode.RET_SUCC_VALUE;
        }
        session.send(new PacketTakeDailyTaskScoreRewardRsp(req.getIsClaimDailyAttendance(), rewardId, retcode));
    }
}