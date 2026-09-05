package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.FishBattleEndRspOuterClass.FishBattleEndRsp;
import emu.grasscutter.net.proto.FishBattleResultOuterClass.FishBattleResult;
import emu.grasscutter.net.proto.ItemParamOuterClass.ItemParam;
import java.util.List;

public class PacketFishBattleEndRsp extends BasePacket {
    public PacketFishBattleEndRsp(int retcode, FishBattleResult result, boolean isGotReward, List<ItemParam> rewards) {
        super(PacketOpcodes.FishBattleEndRsp);

        var builder = FishBattleEndRsp.newBuilder()
                .setRetcode(retcode)
                .setBattleResult(result)
                .setIsGotReward(isGotReward);

        if (rewards != null) {
            builder.addAllRewardItemList(rewards);
        }

        this.setData(builder.build());
    }
}