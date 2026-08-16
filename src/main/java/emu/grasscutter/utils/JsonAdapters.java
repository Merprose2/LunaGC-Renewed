package emu.grasscutter.utils;

import com.google.gson.*;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.*;
import emu.grasscutter.data.common.DynamicFloat;
import emu.grasscutter.game.world.*;
import it.unimi.dsi.fastutil.floats.FloatArrayList;
import it.unimi.dsi.fastutil.ints.*;
import java.io.IOException;
import java.lang.reflect.Field;
import java.util.*;
import lombok.val;

public interface JsonAdapters {
    class DynamicFloatAdapter extends TypeAdapter<DynamicFloat> {
        @Override
        public DynamicFloat read(JsonReader reader) throws IOException {
            switch (reader.peek()) {
                case STRING -> {
                    return new DynamicFloat(reader.nextString());
                }
                case NUMBER -> {
                    return new DynamicFloat((float) reader.nextDouble());
                }
                case BOOLEAN -> {
                    return new DynamicFloat(reader.nextBoolean());
                }
                case BEGIN_ARRAY -> {
                    reader.beginArray();
                    val opStack = new ArrayList<DynamicFloat.StackOp>();
                    while (reader.hasNext()) {
                        opStack.add(
                                switch (reader.peek()) {
                                    case STRING -> new DynamicFloat.StackOp(reader.nextString());
                                    case NUMBER -> new DynamicFloat.StackOp((float) reader.nextDouble());
                                    case BOOLEAN -> new DynamicFloat.StackOp(reader.nextBoolean());
                                    default -> throw new IOException(
                                            "Invalid DynamicFloat definition - " + reader.peek().name());
                                });
                    }
                    reader.endArray();
                    return new DynamicFloat(opStack);
                }
                case BEGIN_OBJECT -> {
                    reader.skipValue();
                    return DynamicFloat.ZERO;
                }
                default -> throw new IOException(
                        "Invalid DynamicFloat definition - " + reader.peek().name());
            }
        }

		@Override
		public void write(
				JsonWriter writer,
				DynamicFloat dynamicFloat)
				throws IOException {

			/*
			 * Constant DynamicFloat values are represented as an ordinary
			 * JSON number.
			 */
			if (!dynamicFloat.isDynamic()) {
				writer.value(dynamicFloat.getConstant());
				return;
			}

			var operations = dynamicFloat.getOps();

			if (operations == null || operations.isEmpty()) {
				/*
				 * A dynamic value without operations is malformed. Do not
				 * silently produce broken JSON.
				 */
				throw new IOException(
						"DynamicFloat is marked dynamic but contains no operations.");
			}

			/*
			 * Dynamic values are normalized into the array representation
			 * already accepted by DynamicFloatAdapter.read().
			 */
			writer.beginArray();

			for (var operation : operations) {
				if (operation == null || operation.op == null) {
					throw new IOException(
							"DynamicFloat contains an invalid null operation.");
				}

				String operationName =
						String.valueOf(operation.op);

				switch (operationName) {
					case "CONSTANT" ->
							writer.value(operation.fValue);

					case "KEY" -> {
						String key =
								operation.sValue != null
										? operation.sValue
										: "";

						writer.value(
								operation.negative
										? "-%" + key
										: "%" + key);
					}

					case "ADD", "SUB", "MUL", "DIV" ->
							writer.value(operationName);

					case "NEXBOOLEAN" ->
							writer.value(operation.bValue);

					default ->
							throw new IOException(
									"Unsupported DynamicFloat operation: "
											+ operationName);
				}
			}

			writer.endArray();
		}
    }

    class IntListAdapter extends TypeAdapter<IntList> {
        @Override
        public IntList read(JsonReader reader) throws IOException {
            if (Objects.requireNonNull(reader.peek()) == JsonToken.BEGIN_ARRAY) {
                reader.beginArray();
                val i = new IntArrayList();
                while (reader.hasNext()) i.add(reader.nextInt());
                reader.endArray();
                i.trim();

                return i;
            }
            throw new IOException("Invalid IntList definition - " + reader.peek().name());
        }

        @Override
        public void write(JsonWriter writer, IntList l) throws IOException {
            writer.beginArray();
            for (val i : l)
            writer.value(i);
            writer.endArray();
        }
    }

    public class ByteArrayAdapter extends TypeAdapter<byte[]> {
        @Override
        public void write(JsonWriter out, byte[] value) throws IOException {
            out.value(Utils.base64Encode(value));
        }

        @Override
        public byte[] read(JsonReader in) throws IOException {
            return Utils.base64Decode(in.nextString());
        }
    }

    class GridPositionAdapter extends TypeAdapter<GridPosition> {
        @Override
        public void write(JsonWriter out, GridPosition value) throws IOException {
            out.value("(" + value.getX() + ", " + value.getZ() + ", " + value.getWidth() + ")");
        }

        @Override
        public GridPosition read(JsonReader in) throws IOException {
            if (in.peek() != JsonToken.STRING)
                throw new IOException("Invalid GridPosition definition - " + in.peek().name());

            var str = in.nextString().replace("(", "").replace(")", "").replace(" ", "");
            var split = str.split(",");

            if (split.length != 3)
                throw new IOException("Invalid GridPosition definition - " + in.peek().name());

            return new GridPosition(
                    Integer.parseInt(split[0]), Integer.parseInt(split[1]), Integer.parseInt(split[2]));
        }
    }

    class PositionAdapter extends TypeAdapter<Position> {
        @Override
        public Position read(JsonReader reader) throws IOException {
            switch (reader.peek()) {
                case BEGIN_ARRAY -> {
                    reader.beginArray();
                    val array = new FloatArrayList(3);
                    while (reader.hasNext()) array.add(reader.nextInt());
                    reader.endArray();
                    return new Position(array);
                }
                case BEGIN_OBJECT -> {
                    float x = 0f;
                    float y = 0f;
                    float z = 0f;
                    reader.beginObject();
                    for (var next = reader.peek(); next != JsonToken.END_OBJECT; next = reader.peek()) {
                        val name = reader.nextName();
                        switch (name) {
                            case "x", "X", "_x" -> x = (float) reader.nextDouble();
                            case "y", "Y", "_y" -> y = (float) reader.nextDouble();
                            case "z", "Z", "_z" -> z = (float) reader.nextDouble();
                            default -> reader.skipValue();
                        }
                    }
                    reader.endObject();
                    return new Position(x, y, z);
                }
                default -> throw new IOException("Invalid Position definition - " + reader.peek().name());
            }
        }

        @Override
        public void write(JsonWriter writer, Position i) throws IOException {
            writer.beginArray();
            writer.value(i.getX());
            writer.value(i.getY());
            writer.value(i.getZ());
            writer.endArray();
        }
    }

    class EnumTypeAdapterFactory implements TypeAdapterFactory {
        @SuppressWarnings("unchecked")
        public <T> TypeAdapter<T> create(Gson gson, TypeToken<T> type) {
            Class<T> enumClass = (Class<T>) type.getRawType();
            if (!enumClass.isEnum()) return null;

            val map = new HashMap<String, T>();
            val enumConstants = enumClass.getEnumConstants();
            for (val constant : enumConstants) map.put(constant.toString(), constant);

            for (Field f : enumClass.getDeclaredFields()) {
                if (switch (f.getName()) {
                    case "value", "id" -> true;
                    default -> false;
                }) {

                    try {
                        for (var constant : enumConstants) {
                            var accessible = f.canAccess(constant);
                            f.setAccessible(true);
                            map.put(String.valueOf(f.getInt(constant)), constant);
                            f.setAccessible(accessible);
                        }
                    } catch (IllegalAccessException e) {

                    }
                    break;
                }
            }

			return new TypeAdapter<T>() {
				@Override
				public T read(JsonReader reader) throws IOException {
					return switch (reader.peek()) {
						case STRING -> {
							String value = reader.nextString();
							T match = map.get(value);
							if (match == null && value.startsWith("__exp_")) {
								match = map.get(value.substring("__exp_".length()));
							}
							yield match;
						}

						case NUMBER -> map.get(String.valueOf(reader.nextInt()));

						default -> throw new IOException("Invalid Enum definition - " + reader.peek().name());
					};
				}
				@Override
				public void write(JsonWriter writer, T value) throws IOException {
					writer.value(value.toString());
				}
			}.nullSafe();
        }
    }
}
