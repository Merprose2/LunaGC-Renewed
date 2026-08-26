package emu.grasscutter.server.packet.send;

import emu.grasscutter.data.GameData;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import emu.grasscutter.net.proto._TakeDailyTaskScoreRewardRspOuterClass._TakeDailyTaskScoreRewardRsp;

public class PacketTakeDailyTaskScoreRewardRsp extends BasePacket {
    public PacketTakeDailyTaskScoreRewardRsp(boolean isClaimDailyAttendance, int rewardPreviewId, int retcode) {
        super(PacketOpcodes._TakeDailyTaskScoreRewardRsp);

        var rsp = _TakeDailyTaskScoreRewardRsp.newBuilder().setIsClaimDailyAttendance(isClaimDailyAttendance).setRetcode(retcode);

        if (retcode == 0 && rewardPreviewId > 0) {
            var reward = GameData.getRewardPreviewDataMap().get(rewardPreviewId);

            if (reward != null && reward.getPreviewItems() != null) {
                for (var item : reward.getPreviewItems()) {
                    rsp.addItemList(ItemParam.newBuilder().setItemId(item.getId()).setCount(item.getCount()));
                }
            }
        }

        this.setData(rsp.build());
    }
}