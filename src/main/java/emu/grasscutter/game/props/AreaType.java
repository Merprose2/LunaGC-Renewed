package emu.grasscutter.game.props;

import it.unimi.dsi.fastutil.ints.*;
import java.util.*;
import java.util.stream.Stream;
import lombok.Getter;

public enum AreaType {
    NONE(0),
    LEVEL_1(1),
    LEVEL_2(2);

    private static final Int2ObjectMap<AreaType> map = new Int2ObjectOpenHashMap<>();

    static {
        Stream.of(values()).forEach(e -> map.put(e.getValue(), e));
    }

    @Getter private final int value;

    AreaType(int value) {
        this.value = value;
    }

    public static AreaType getTypeByValue(int value) {
        return map.getOrDefault(value, NONE);
    }
}
