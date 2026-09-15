package emu.grasscutter.server.packet.recv;

import com.google.protobuf.CodedInputStream;
import com.google.protobuf.CodedOutputStream;
import emu.grasscutter.net.packet.BasePacket;
import emu.grasscutter.net.packet.Opcodes;
import emu.grasscutter.net.packet.PacketHandler;
import emu.grasscutter.net.packet.PacketOpcodes;
import emu.grasscutter.server.game.GameSession;
import java.io.ByteArrayOutputStream;

/**
 * Client -> server (CmdId 24312, official name obfuscated as "BEAPIGNCAOB"): the player enters
 * fishing mode and announces the avatar that will fish (officially the game switches to the
 * Traveler). Payload fields observed in the official capture:
 *
 * <ul>
 *   <li>6: uint32 state (= 1)
 *   <li>7: uint64 avatar_guid
 * </ul>
 *
 * The official server switches its active avatar accordingly and answers with CmdId 28194
 * ("FLPFLCPNIAG"): 3: uint64 avatar_guid, 7: uint32 state (, 9: uint32 entity_id - unused).
 *
 * <p>Both messages are obfuscated, so neither exists in the generated set and the fields have to be
 * read and written by hand. The numbers are tied to the client version.
 */
@Opcodes(PacketOpcodes.AvatarEnterFishingReq)
public class HandlerAvatarEnterFishingReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        long avatarGuid = 0;
        int state = 0;

        var input = CodedInputStream.newInstance(payload);
        int tag;
        while ((tag = input.readTag()) != 0) {
            if (tag == 56) { // field 7, varint -> avatar_guid
                avatarGuid = input.readInt64();
            } else if (tag == 48) { // field 6, varint -> state
                state = input.readUInt32();
            } else {
                input.skipField(tag);
            }
        }

        // Official behavior: entering fishing switches the active avatar (e.g. to the Traveler).
        session.getPlayer().getTeamManager().changeAvatar(avatarGuid, false);

        // Answer with the fishing state confirmation (CmdId 28194).
        var baos = new ByteArrayOutputStream();
        var body = CodedOutputStream.newInstance(baos);
        body.writeUInt64(3, avatarGuid);
        body.writeUInt32(7, state);
        body.flush();

        var rsp = new BasePacket(PacketOpcodes.AvatarEnterFishingRsp);
        rsp.setData(baos.toByteArray());
        session.send(rsp);
    }
}
