package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.entity.EntityVehicle;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.VehiclePhlogistonPointsNotifyOuterClass.VehiclePhlogistonPointsNotify;

public class PacketVehiclePhlogistonPointsNotify extends BasePacket {

    public PacketVehiclePhlogistonPointsNotify(EntityVehicle vehicle) {
        super(PacketOpcodes.VehiclePhlogistonPointsNotify);

        VehiclePhlogistonPointsNotify notify = VehiclePhlogistonPointsNotify.newBuilder()
                .setCurPhlogiston(vehicle.getCurPhlogiston())
                .setEntityId(vehicle.getId())
                .build();


        this.setData(notify);
    }
}
