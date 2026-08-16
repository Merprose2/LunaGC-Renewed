package emu.grasscutter.server.packet.send;

import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.PacketOpcodes;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Collection;

public class PacketGadgetAutoPickDropInfoNotify extends BasePacket {
    /*
     * REL6.6 proto archaeology:
     *
     * GadgetAutoPickDropInfoNotify
     * CmdID: 338
     * repeated Item item_list = 13;
     *
     */
    private static final int ITEM_LIST_FIELD_NUMBER_6_6 = 13;

    public PacketGadgetAutoPickDropInfoNotify(Collection<GameItem> items) {
        super(PacketOpcodes.GadgetAutoPickDropInfoNotify);

        try {
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            CodedOutputStream output = CodedOutputStream.newInstance(baos);

            for (GameItem item : items) {
                output.writeMessage(ITEM_LIST_FIELD_NUMBER_6_6, item.toProto());
            }

            output.flush();
            this.setData(baos.toByteArray());
        } catch (IOException e) {
            throw new RuntimeException("Failed to encode GadgetAutoPickDropInfoNotify for REL6.6", e);
        }
    }
}