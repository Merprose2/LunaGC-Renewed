package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.AvatarGainTraceEffectNotifyOuterClass.AvatarGainTraceEffectNotify;

public class PacketAvatarGainTraceEffectNotify extends BasePacket {

    public PacketAvatarGainTraceEffectNotify(int traceEffect) {
        super(PacketOpcodes.AvatarGainTraceEffectNotify);

        AvatarGainTraceEffectNotify proto =
                AvatarGainTraceEffectNotify.newBuilder().setTraceEffectId(traceEffect).build();

        this.setData(proto);
    }
}
