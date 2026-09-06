package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.activity.ActivityManager;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.GetActivityInfoRspOuterClass.GetActivityInfoRsp;
import java.util.Objects;
import java.util.Set;

public class PacketGetActivityInfoRsp extends BasePacket {
    public PacketGetActivityInfoRsp(Set<Integer> activityIdList, ActivityManager activityManager) {
        super(PacketOpcodes.GetActivityInfoRsp);

        var proto = GetActivityInfoRsp.newBuilder();

        activityIdList.stream()
                .map(activityManager::getInfoProtoByActivityId)
                .filter(Objects::nonNull)
                .forEach(proto::addActivityInfoList);

        this.setData(proto);
    }

    /**
     * Login push: the official server delivers the full activity list (each entry with its detail
     * payload, e.g. ley_line_challenge_detail_info for Stygian Onslaught) right after
     * PlayerLoginRsp without waiting for a GetActivityInfoReq - the 7.0 client never requests it,
     * so without this push event pages such as Stygian Onslaught show the mode as closed.
     */
    public PacketGetActivityInfoRsp(ActivityManager activityManager) {
        this(activityManager.getAllActivityIds(), activityManager);
    }
}
