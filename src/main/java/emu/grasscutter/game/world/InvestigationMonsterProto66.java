package emu.grasscutter.game.world;

import com.google.protobuf.ByteString;
import com.google.protobuf.UnknownFieldSet;
import emu.grasscutter.net.proto.InvestigationMonsterOuterClass;

public final class InvestigationMonsterProto66 {
    private InvestigationMonsterProto66() {}

    public static InvestigationMonsterOuterClass.InvestigationMonster build(
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

        var detail =
                buildDetail(
                        sceneId,
                        groupId,
                        monsterId,
                        pos,
                        level,
                        refreshInterval,
                        nextRefreshTime,
                        resin,
                        bossChestNum,
                        maxBossChestNum,
                        nextBossChestRefreshTime,
                        mapLayerId,
                        isAlive);

        var monsterUnknowns =
                UnknownFieldSet.newBuilder()
                        // 6.6 InvestigationMonster.id = 15
                        .addField(15, varint(investigationId))
                        // 6.6 InvestigationMonster.city_id = 14
                        .addField(14, varint(cityId))
                        // 6.6 InvestigationMonster.lock_state = 10; LOCK_NONE = 0
                        .addField(10, varint(0))
                        // 6.6 repeated _InvestigationMonsterDetail = 1149
                        .addField(1149, message(detail))
                        .build();

        return InvestigationMonsterOuterClass.InvestigationMonster.newBuilder()
                .setUnknownFields(monsterUnknowns)
                .build();
    }

    private static ByteString buildDetail(
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
        var detail =
                UnknownFieldSet.newBuilder()
                        // _InvestigationMonsterDetail._monster_config = 1
                        .addField(1, message(buildMonsterConfig(sceneId, groupId, monsterId)))
                        // _InvestigationMonsterDetail._map_layer_id = 2
                        // Added below only when > 0.
                        // _InvestigationMonsterDetail._is_respawning = 3
                        .addField(3, bool(false))
                        // _InvestigationMonsterDetail.pos = 4
                        .addField(4, message(pos.toProto().toByteString()))
                        // _InvestigationMonsterDetail.level = 5
                        .addField(5, varint(Math.max(1, level)))
                        // _InvestigationMonsterDetail.boss_chest_num = 6
                        // Added below only when > 0.
                        // _InvestigationMonsterDetail.max_boss_chest_num = 8
                        // Added below only when > 0.
                        // _InvestigationMonsterDetail.refresh_interval = 9
                        .addField(9, varint(Math.max(0, refreshInterval)))
                        // _InvestigationMonsterDetail.is_alive = 10
                        .addField(10, bool(isAlive))
                        // _InvestigationMonsterDetail.resin = 11
                        .addField(11, varint(Math.max(0, resin)))
                        // _InvestigationMonsterDetail.is_area_locked = 13
                        .addField(13, bool(false))
                        // _InvestigationMonsterDetail.next_boss_chest_refresh_time = 14
                        // Added below only when > 0.
                        // _InvestigationMonsterDetail.next_refresh_time = 15
                        .addField(15, varint(Math.max(0, nextRefreshTime)));

        if (mapLayerId > 0) {
            detail.addField(2, varint(mapLayerId));
        }

        if (bossChestNum > 0) {
            detail.addField(6, varint(bossChestNum));
        }

        if (maxBossChestNum > 0) {
            detail.addField(8, varint(maxBossChestNum));
        }

        if (nextBossChestRefreshTime > 0) {
            detail.addField(14, varint(nextBossChestRefreshTime));
        }

        return detail.build().toByteString();
    }

    private static ByteString buildMonsterConfig(int sceneId, int groupId, int monsterId) {
        return UnknownFieldSet.newBuilder()
                // _InvestigationMonsterConfig.scene_id = 1
                .addField(1, varint(sceneId))
                // _InvestigationMonsterConfig.group_id = 14
                .addField(14, varint(groupId))
                // _InvestigationMonsterConfig.monster_id = 10
                .addField(10, varint(monsterId))
                .build()
                .toByteString();
    }

    private static UnknownFieldSet.Field varint(long value) {
        return UnknownFieldSet.Field.newBuilder().addVarint(value).build();
    }

    private static UnknownFieldSet.Field bool(boolean value) {
        return varint(value ? 1 : 0);
    }

    private static UnknownFieldSet.Field message(ByteString value) {
        return UnknownFieldSet.Field.newBuilder().addLengthDelimited(value).build();
    }
}