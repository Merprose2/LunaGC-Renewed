package emu.grasscutter.server.packet.recv;

import com.google.protobuf.UnknownFieldSet;
import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.PacketHeadOuterClass.PacketHead;
import emu.grasscutter.server.game.GameSession;
import emu.grasscutter.server.packet.send.PacketClientSetGameTimeRsp;
import emu.grasscutter.server.packet.send.PacketPlayerGameTimeNotify;
import emu.grasscutter.server.packet.send.PacketSceneTimeNotify;

@Opcodes(PacketOpcodes.ClientSetGameTimeReq)
public class HandlerClientSetGameTimeReq extends PacketHandler {
    @Override
    public void handle(GameSession session, byte[] header, byte[] payload) throws Exception {
        PacketHead head = header != null && header.length > 0
                ? PacketHead.parseFrom(header)
                : PacketHead.getDefaultInstance();

        int clientSequence = head.getClientSequenceId();

        int clientGameTime = decodeVarintField(payload, 2, -1);
        int targetTotalGameTime = decodeVarintField(payload, 12, 0);
        boolean isForceSet = decodeBoolField(payload, 13, false);

        var player = session.getPlayer();
        var world = player.getWorld();

        int targetDayMinutes = Math.floorMod(targetTotalGameTime, 1440);
        int extraDays = 0;

        if (clientGameTime >= 0) {
            int clientDay = Math.floorDiv(clientGameTime, 1440);
            int targetDay = Math.floorDiv(targetTotalGameTime, 1440);
            extraDays = Math.max(0, targetDay - clientDay);
        }

        Grasscutter.getLogger()
                .info(
                        "[ClientSetGameTimeReq] uid={}, clientSeq={}, clientGameTime={}, targetTotalGameTime={}, targetDayMinutes={}, extraDays={}, isForceSet={}, currentServerTime={}, timeLocked={}",
                        player.getUid(),
                        clientSequence,
                        clientGameTime,
                        targetTotalGameTime,
                        targetDayMinutes,
                        extraDays,
                        isForceSet,
                        world.getGameTime(),
                        world.isTimeLocked());

        if (world.isTimeLocked()) {
            world.lockTime(false);
        }

        world.changeTime(targetDayMinutes, extraDays);

        int serverTotalGameTime = (int) world.getTotalGameTimeMinutes();

		/*
		 * REL6.6 clock UI appears to receive ClientSetGameTimeRsp but still hangs.
		 * Try syncing player/scene time first, then send the correlated response.
		 */
		player.sendPacket(new PacketPlayerGameTimeNotify(player));

		if (player.getScene() != null) {
			player.sendPacket(new PacketSceneTimeNotify(player.getScene()));
		}

		player.sendPacket(new PacketClientSetGameTimeRsp(clientSequence, clientGameTime, serverTotalGameTime));

		Grasscutter.getLogger()
				.info(
						"[ClientSetGameTimeReq] completed uid={}, clientSeq={}, serverTotalGameTime={}, dayTime={}",
						player.getUid(),
						clientSequence,
						serverTotalGameTime,
						world.getGameTime());
    }

    private int decodeVarintField(byte[] payload, int wantedField, int fallback) {
        try {
            var unknowns = UnknownFieldSet.parseFrom(payload);
            var field = unknowns.asMap().get(wantedField);
            if (field != null && !field.getVarintList().isEmpty()) {
                return field.getVarintList().get(0).intValue();
            }
        } catch (Exception ignored) {
        }

        return fallback;
    }

    private boolean decodeBoolField(byte[] payload, int wantedField, boolean fallback) {
        return decodeVarintField(payload, wantedField, fallback ? 1 : 0) != 0;
    }
}