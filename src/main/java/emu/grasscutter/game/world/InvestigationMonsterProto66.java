package emu.grasscutter.game.world;

import emu.grasscutter.net.proto.InvestigationMonsterOuterClass.InvestigationMonster;
import emu.grasscutter.net.proto._InvestigationMonsterConfigOuterClass._InvestigationMonsterConfig;
import emu.grasscutter.net.proto._InvestigationMonsterDetailOuterClass._InvestigationMonsterDetail;

/**
 * Builds an {@code InvestigationMonster} for the investigation (monster tracking) system.
 *
 * <p>The message and its nested {@code _InvestigationMonsterConfig} / {@code _InvestigationMonsterDetail}
 * are assembled through the generated proto accessors: their field numbers change between game
 * versions, so hand written numbers silently produce monsters the client can not read.
 */
public final class InvestigationMonsterProto66 {
    private InvestigationMonsterProto66() {}

    public static InvestigationMonster build(
            int investigationId,
            int cityId,
            int sceneId,
            int groupId,
            int monsterId,
            Position pos,
            int level,
            int refreshInterval,
            int nextRefreshTime,
            int resin,
            int bossChestNum,
            int maxBossChestNum,
            int nextBossChestRefreshTime,
            int mapLayerId,
            boolean isAlive) {
        if (pos == null) {
            pos = Position.ZERO;
        }

        var monsterConfig =
                _InvestigationMonsterConfig.newBuilder()
                        .setSceneId(sceneId)
                        .setGroupId(groupId)
                        .setMonsterId(monsterId)
                        .build();

        var detail =
                _InvestigationMonsterDetail.newBuilder()
                        .setPos(pos.toProto())
                        .setLevel(Math.max(1, level))
                        .setRefreshInterval(Math.max(0, refreshInterval))
                        .setNextRefreshTime(Math.max(0, nextRefreshTime))
                        .setResin(Math.max(0, resin))
                        .setMonsterConfig(monsterConfig)
                        .setIsAlive(isAlive);

        if (mapLayerId > 0) {
            detail.setMapLayerId(mapLayerId);
        }

        if (bossChestNum > 0) {
            detail.setBossChestNum(bossChestNum);
        }

        if (maxBossChestNum > 0) {
            detail.setMaxBossChestNum(maxBossChestNum);
        }

        if (nextBossChestRefreshTime > 0) {
            detail.setNextBossChestRefreshTime(nextBossChestRefreshTime);
        }

        // lock_state is LOCK_NONE (the proto3 default), so it is not set.
        return InvestigationMonster.newBuilder()
                .setId(investigationId)
                .setCityId(cityId)
                .addInvestigationMonsterDetailList(detail)
                .build();
    }
}