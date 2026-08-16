package emu.grasscutter.utils;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.game.world.*;
import emu.grasscutter.utils.JsonAdapters.*;
import emu.grasscutter.utils.objects.JObject;
import it.unimi.dsi.fastutil.ints.IntList;
import java.io.*;
import java.lang.reflect.Type;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;

public final class JsonUtils {
	/*
	 * Resource classes use Morphia's @Transient annotation for fields that
	 * contain derived or runtime-only data.
	 *
	 * Gson does not recognize that annotation automatically, so the resource
	 * cache needs an explicit exclusion strategy.
	 */
	private static final ExclusionStrategy RESOURCE_CACHE_EXCLUSION_STRATEGY =
			new ExclusionStrategy() {
				@Override
				public boolean shouldSkipField(FieldAttributes field) {
					return field.getAnnotation(dev.morphia.annotations.Transient.class) != null;
				}

				@Override
				public boolean shouldSkipClass(Class<?> clazz) {
					return false;
				}
			};

	private static GsonBuilder createGsonBuilder() {
		return new GsonBuilder()
				.setPrettyPrinting()
				.registerTypeAdapter(DynamicFloat.class, new DynamicFloatAdapter().nullSafe())
				.registerTypeAdapter(IntList.class, new IntListAdapter().nullSafe())
				.registerTypeAdapter(Position.class, new PositionAdapter().nullSafe())
				.registerTypeAdapter(GridPosition.class, new GridPositionAdapter().nullSafe())
				.registerTypeAdapter(byte[].class, new ByteArrayAdapter().nullSafe())
				.registerTypeAdapter(JObject.class, new JObject.Adapter())
				.registerTypeAdapterFactory(new EnumTypeAdapterFactory())
				.disableHtmlEscaping();
	}

	/*
	 * Normal LunaGC JSON parser.
	 *
	 * Keep this separate so the cache implementation does not silently change
	 * the behavior of unrelated configuration, database or command JSON.
	 */
	static final Gson gson =
			createGsonBuilder().create();

	/*
	 * Cache-only parser.
	 *
	 * Morphia @Transient fields are omitted during both writing and reading.
	 */
	private static final Gson resourceCacheGson =
			createGsonBuilder().addSerializationExclusionStrategy(
							RESOURCE_CACHE_EXCLUSION_STRATEGY)
					.addDeserializationExclusionStrategy(
							RESOURCE_CACHE_EXCLUSION_STRATEGY)
					.create();

    /**
     * Converts the given object to a JsonElement.
     *
     * @param object The object to convert.
     * @return The JsonElement.
     */
    public static JsonElement toJson(Object object) {
        return gson.toJsonTree(object);
    }

    /*
     * Encode an object to a JSON string
     */
    public static String encode(Object object) {
        return gson.toJson(object);
    }
	
	/**
	 * Serializes resource-cache data while excluding Morphia @Transient
	 * runtime and derived fields.
	 */
	public static String encodeResourceCache(Object object) {
		return resourceCacheGson.toJson(object);
	}

	/**
	 * Deserializes one normalized cache section using the same exclusion
	 * rules that were applied by encodeResourceCache().
	 */
	public static <T> List<T> loadResourceCacheList(
			Reader reader,
			Class<T> classType)
			throws IOException {
		return resourceCacheGson.fromJson(reader, TypeToken.getParameterized(List.class, classType).getType());
	}

    public static <T> T decode(JsonElement jsonElement, Class<T> classType)
            throws JsonSyntaxException {
        return gson.fromJson(jsonElement, classType);
    }

    public static <T> T loadToClass(Reader fileReader, Class<T> classType) throws IOException {
        return gson.fromJson(fileReader, classType);
    }

    @Deprecated(forRemoval = true)
    public static <T> T loadToClass(String filename, Class<T> classType) throws IOException {
        try (InputStreamReader fileReader =
                new InputStreamReader(
                        new FileInputStream(Utils.toFilePath(filename)), StandardCharsets.UTF_8)) {
            return loadToClass(fileReader, classType);
        }
    }

    public static <T> T loadToClass(Path filename, Class<T> classType) throws IOException {
        try (var fileReader = Files.newBufferedReader(filename, StandardCharsets.UTF_8)) {
            return loadToClass(fileReader, classType);
        }
    }

    public static <T> List<T> loadToList(Reader fileReader, Class<T> classType) throws IOException {
        return gson.fromJson(fileReader, TypeToken.getParameterized(List.class, classType).getType());
    }

    @Deprecated(forRemoval = true)
    public static <T> List<T> loadToList(String filename, Class<T> classType) throws IOException {
        try (InputStreamReader fileReader =
                new InputStreamReader(
                        new FileInputStream(Utils.toFilePath(filename)), StandardCharsets.UTF_8)) {
            return loadToList(fileReader, classType);
        }
    }

    public static <T> List<T> loadToList(Path filename, Class<T> classType) throws IOException {
        try (var fileReader = Files.newBufferedReader(filename, StandardCharsets.UTF_8)) {
            return loadToList(fileReader, classType);
        }
    }

    public static <T1, T2> Map<T1, T2> loadToMap(
            Reader fileReader, Class<T1> keyType, Class<T2> valueType) throws IOException {
        return gson.fromJson(
                fileReader, TypeToken.getParameterized(Map.class, keyType, valueType).getType());
    }

    @Deprecated(forRemoval = true)
    public static <T1, T2> Map<T1, T2> loadToMap(
            String filename, Class<T1> keyType, Class<T2> valueType) throws IOException {
        try (InputStreamReader fileReader =
                new InputStreamReader(
                        new FileInputStream(Utils.toFilePath(filename)), StandardCharsets.UTF_8)) {
            return loadToMap(fileReader, keyType, valueType);
        }
    }

    public static <T1, T2> Map<T1, T2> loadToMap(
            Path filename, Class<T1> keyType, Class<T2> valueType) throws IOException {
        try (var fileReader = Files.newBufferedReader(filename, StandardCharsets.UTF_8)) {
            return loadToMap(fileReader, keyType, valueType);
        }
    }

    public static <T1, T2> Map<T1, T2> loadToMap(Path filename, Class<T1> keyType, Type valueType)
            throws IOException {
        try (var fileReader = Files.newBufferedReader(filename, StandardCharsets.UTF_8)) {
            return gson.fromJson(
                    fileReader, TypeToken.getParameterized(Map.class, keyType, valueType).getType());
        }
    }

    /**
     * Safely JSON decodes a given string.
     *
     * @param jsonData The JSON-encoded data.
     * @return JSON decoded data, or null if an exception occurred.
     */
    public static <T> T decode(String jsonData, Class<T> classType) {
        try {
            return gson.fromJson(jsonData, classType);
        } catch (Exception ignored) {
            return null;
        }
    }

    public static <T> T decode(String jsonData, Type type) {
        try {
            return gson.fromJson(jsonData, type);
        } catch (Exception ignored) {
            return null;
        }
    }
}
