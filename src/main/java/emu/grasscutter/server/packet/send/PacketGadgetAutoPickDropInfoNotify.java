package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.net.proto.GadgetAutoPickDropInfoNotifyOuterClass.GadgetAutoPickDropInfoNotify;
import java.util.Collection;

public class PacketGadgetAutoPickDropInfoNotify extends BasePacket {

    public PacketGadgetAutoPickDropInfoNotify(Collection<GameItem> items) {
        super(PacketOpcodes.GadgetAutoPickDropInfoNotify);

        // Always encode through the generated proto: the field numbers of this message are not
        // stable between game versions. Hand writing the fields used to put the items on field 13,
        // which the client silently ignores, so opened chests showed an empty reward window even
        // though the items had been granted.
        var proto = GadgetAutoPickDropInfoNotify.newBuilder();
        for (GameItem item : items) {
            proto.addItemList(item.toProto());
        }

        this.setData(proto.build());
    }
}