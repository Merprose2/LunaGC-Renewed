package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.expedition.ExpeditionInfo;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarExpeditionStartRspOuterClass.AvatarExpeditionStartRsp;
import emu.grasscutter.net.proto._AvatarExpeditionBasicInfoOuterClass._AvatarExpeditionBasicInfo;
import java.util.List;
import java.util.Map;

public class PacketAvatarExpeditionStartRsp extends BasePacket {
    public PacketAvatarExpeditionStartRsp(Map<Long, ExpeditionInfo> expeditionInfo) {
        this(expeditionInfo, List.of());
    }

    public PacketAvatarExpeditionStartRsp(
            Map<Long, ExpeditionInfo> expeditionInfo, List<_AvatarExpeditionBasicInfo> startedInfoList) {
        super(PacketOpcodes.AvatarExpeditionStartRsp);

        AvatarExpeditionStartRsp.Builder proto = AvatarExpeditionStartRsp.newBuilder();
        expeditionInfo.forEach((key, e) -> proto.putExpeditionInfoMap(key, e.toProto()));
        // The client uses this echoed list to update the expedition slots immediately,
        // without it the UI only reflects the change after re-opening the menu.
        proto.addAllExpeditionBasicInfoList(startedInfoList);

        this.setData(proto.build());
    }
}