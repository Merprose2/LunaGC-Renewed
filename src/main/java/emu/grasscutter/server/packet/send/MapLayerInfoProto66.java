package emu.grasscutter.server.packet.send;

import com.google.protobuf.UnknownFieldSet;
import emu.grasscutter.net.proto.MapLayerInfoOuterClass;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/**
 * Builds the big world {@code _MapLayerInfo} of the scene data notifications.
 *
 * <p>The unlocked map layers and map layer groups are written through the named generated accessors.
 */
public final class MapLayerInfoProto66 {
    /**
     * Unlocked map layer floors: the third repeated uint32 of {@code _MapLayerInfo}. Its generated
     * accessor is obfuscated, and obfuscated names are reshuffled - together with their numbers - by
     * every client build, so it is written as an unknown field. The number has to be taken from the
     * proto of the client version in use (11 for the current one).
     */
    private static final int F_UNLOCKED_MAP_LAYER_FLOOR_LIST = 11;

    private MapLayerInfoProto66() {}

    public static MapLayerInfoOuterClass.MapLayerInfo build(
            Iterable<Integer> mapLayerIds,
            Iterable<Integer> mapLayerFloorIds,
            Iterable<Integer> mapLayerGroupIds) {
        var unknowns = UnknownFieldSet.newBuilder();
        addRepeatedUInt32(unknowns, F_UNLOCKED_MAP_LAYER_FLOOR_LIST, mapLayerFloorIds);

        return MapLayerInfoOuterClass.MapLayerInfo.newBuilder()
                .addAllUnlockMapLayerList(clean(mapLayerIds))
                .addAllUnlockMapLayerGroupList(clean(mapLayerGroupIds))
                .setUnknownFields(unknowns.build())
                .build();
    }

    private static void addRepeatedUInt32(
            UnknownFieldSet.Builder unknowns, int fieldNumber, Iterable<Integer> values) {
        if (values == null) {
            return;
        }

        Set<Integer> cleanValues = new LinkedHashSet<>();

        for (Integer value : values) {
            if (value != null && value > 0) {
                cleanValues.add(value);
            }
        }

        if (cleanValues.isEmpty()) {
            return;
        }

        var field = UnknownFieldSet.Field.newBuilder();

        for (Integer value : cleanValues) {
            field.addVarint(value);
        }

        unknowns.addField(fieldNumber, field.build());
    }

    private static List<Integer> clean(Iterable<Integer> values) {
        if (values == null) {
            return List.of();
        }

        Set<Integer> cleanValues = new LinkedHashSet<>();

        for (Integer value : values) {
            if (value != null && value > 0) {
                cleanValues.add(value);
            }
        }

        return List.copyOf(cleanValues);
    }
}