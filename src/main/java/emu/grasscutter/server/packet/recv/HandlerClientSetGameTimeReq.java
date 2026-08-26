package emu.grasscutter.server.packet.recv;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ClientSetGameTimeReqOuterClass.ClientSetGameTimeReq;
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
        var req = ClientSetGameTimeReq.parseFrom(payload);

        int targetGameTime = req.getGameTime();
        int clientGameTime = req.getClientGameTime();
        boolean isForceSet = req.getIsForceSet();

        var player = session.getPlayer();
        var world = player.getWorld();

        int targetDayMinutes = targetGameTime % 1440;
        int currentDayMinutes = world.getGameTime();
        
        int extraDays = 0;
        if (targetGameTime >= 1440) {
            extraDays = targetGameTime / 1440;
        } else if (targetDayMinutes < currentDayMinutes) {
            extraDays = 1;
        }

        Grasscutter.getLogger()
                .info(
                        "[ClientSetGameTimeReq] uid={}, clientSeq={}, clientGameTime={}, targetGameTime={}, targetDayMinutes={}, extraDays={}, isForceSet={}",
                        player.getUid(),
                        clientSequence,
                        clientGameTime,
                        targetGameTime,
                        targetDayMinutes,
                        extraDays,
                        isForceSet);

        if (world.isTimeLocked()) {
            world.lockTime(false);
        }

        world.changeTime(targetDayMinutes, extraDays);

        player.sendPacket(new PacketPlayerGameTimeNotify(player));

        if (player.getScene() != null) {
            player.sendPacket(new PacketSceneTimeNotify(player.getScene()));
        }

        player.sendPacket(new PacketClientSetGameTimeRsp(clientSequence, clientGameTime, world.getGameTime()));

        Grasscutter.getLogger()
                .info(
                        "[ClientSetGameTimeReq] completed uid={}, clientSeq={}, serverGameTime={}",
                        player.getUid(),
                        clientSequence,
                        world.getGameTime());
    }
}