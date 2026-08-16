package emu.grasscutter.server.packet.send;

import emu.grasscutter.game.dungeons.challenge.WorldChallenge;
import emu.grasscutter.net.packet.*;
import emu.grasscutter.net.proto.ChallengeFinishTypeOuterClass.ChallengeFinishType;
import emu.grasscutter.net.proto.DungeonChallengeFinishNotifyOuterClass.DungeonChallengeFinishNotify;

public class PacketDungeonChallengeFinishNotify extends BasePacket {

    public PacketDungeonChallengeFinishNotify(WorldChallenge challenge) {
        super(PacketOpcodes.DungeonChallengeFinishNotify, true);

        DungeonChallengeFinishNotify proto =
                DungeonChallengeFinishNotify.newBuilder()
                        .setChallengeIndex(challenge.getChallengeIndex())
                        .setIsSuccess(challenge.isSuccess())
                        .setFinishType(
                                challenge.isSuccess()
                                        ? ChallengeFinishType.CHALLENGE_FINISH_TYPE_SUCC
                                        : ChallengeFinishType.CHALLENGE_FINISH_TYPE_FAIL)
                        .setChallengeRecordType(2)
                        .build();

        this.setData(proto);
    }
}
