package emu.grasscutter.server.packet.send;

import com.google.protobuf.UnknownFieldSet;
import emu.grasscutter.net.proto.MapLayerInfoOuterClass;
import java.util.LinkedHashSet;
import java.util.Set;

public final class MapLayerInfoProto66 {
    private MapLayerInfoProto66() {}

    // REL6.6 _MapLayerInfo field mapping from uploaded deobfuscated proto.
    //
    // message _MapLayerInfo {
    //     repeated uint32 IMIKDGJJBHA = 4;
    //     repeated uint32 _unlocked_layer_group_list = 9;
    //     repeated uint32 ADALGDOJPBK = 13;
    // }
    //
    // First test:
    // field 4  = map layer ids
    // field 9  = map layer group ids
    // field 13 = map layer floor ids
    private static final int F_UNLOCKED_MAP_LAYER_ID_LIST = 4;
    private static final int F_UNLOCKED_MAP_LAYER_GROUP_ID_LIST = 9;
    private static final int F_UNLOCKED_MAP_LAYER_FLOOR_ID_LIST = 13;

    public static MapLayerInfoOuterClass.MapLayerInfo build(
            Iterable<Integer> mapLayerIds,
            Iterable<Integer> mapLayerFloorIds,
            Iterable<Integer> mapLayerGroupIds) {
        var unknowns = UnknownFieldSet.newBuilder();

        addRepeatedUInt32(unknowns, F_UNLOCKED_MAP_LAYER_ID_LIST, mapLayerIds);
        addRepeatedUInt32(unknowns, F_UNLOCKED_MAP_LAYER_GROUP_ID_LIST, mapLayerGroupIds);
        addRepeatedUInt32(unknowns, F_UNLOCKED_MAP_LAYER_FLOOR_ID_LIST, mapLayerFloorIds);

        return MapLayerInfoOuterClass.MapLayerInfo.newBuilder()
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
}