package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.BlossomBriefInfoOuterClass.BlossomBriefInfo;
import emu.grasscutter.net.proto.GetBlossomBriefInfoListRspOuterClass.GetBlossomBriefInfoListRsp;

/**
 * Answers {@code GetBlossomBriefInfoListReq} with the ley line outcrops of the requested cities.
 *
 * <p>The client asks for this list when it opens the map / the adventurer handbook, which is what
 * turns the outcrops of a nation into selectable entries. The message was missing from the generated
 * set; {@code GetBlossomBriefInfoListRspOuterClass} was added for it (see
 * resources/Tool/patch_blossom_protos.py).
 */
public class PacketGetBlossomBriefInfoListRsp extends BasePacket {

    public PacketGetBlossomBriefInfoListRsp(Iterable<BlossomBriefInfo> blossoms) {
        super(PacketOpcodes.GetBlossomBriefInfoListRsp);

        this.setData(
                GetBlossomBriefInfoListRsp.newBuilder()
                        .addAllBriefInfoList(blossoms)
                        .setRetcode(0)
                        .build());
    }
}