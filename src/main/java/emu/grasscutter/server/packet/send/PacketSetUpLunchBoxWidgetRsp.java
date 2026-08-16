package emu.grasscutter.server.packet.send;

import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.LunchBoxDataOuterClass.LunchBoxData;
import emu.grasscutter.net.proto.SetUpLunchBoxWidgetRspOuterClass.SetUpLunchBoxWidgetRsp;

public class PacketSetUpLunchBoxWidgetRsp extends BasePacket {

    public PacketSetUpLunchBoxWidgetRsp(
            LunchBoxData lunchBoxData) {
        super(PacketOpcodes.SetUpLunchBoxWidgetRsp);

        setData(
                SetUpLunchBoxWidgetRsp.newBuilder()
                        .setRetcode(0)
                        .setLunchBoxData(lunchBoxData)
                        .build());
    }
}