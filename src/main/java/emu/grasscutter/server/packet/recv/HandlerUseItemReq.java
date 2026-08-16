package emu.grasscutter.server.packet.recv;

import emu.grasscutter.game.inventory.GameItem;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.UseItemReqOuterClass.UseItemReq;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketUseItemRsp;

@Opcodes(PacketOpcodes.UseItemReq)
public class HandlerUseItemReq extends PacketHandler {

    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        UseItemReq req = UseItemReq.parseFrom(payload);

        int count = req.getCount() > 0 ? req.getCount() : 1;

		// Fallback: older/normal option_idx field.
		int selectedOptionIdx = req.getOptionIdx();

		// Selectable boxes appear to send the real selected option here, using a 1-based flattened option index.
		if (req.getAPAANIAIJFICount() > 0) {
			selectedOptionIdx = req.getAPAANIAIJFI(0) - 1;
		}
		GameItem useItem =
				session
						.getServer()
						.getInventorySystem()
						.useItem(
								session.getPlayer(),
								req.getTargetGuid(),
								req.getGuid(),
								count,
								selectedOptionIdx,
								req.getIsEnterMpDungeonTeam());
		
		if (useItem != null) {
			session.send(new PacketUseItemRsp(req.getTargetGuid(), useItem));
		} else {
			session.send(new PacketUseItemRsp());
		}
	}
}
