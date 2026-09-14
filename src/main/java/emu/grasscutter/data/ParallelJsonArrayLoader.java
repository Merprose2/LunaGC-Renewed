package emu.grasscutter.data;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.utils.JsonUtils;
import java.io.*;
import java.nio.channels.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.stream.*;
import lombok.val;

/**
 * Loads large Hoyo style JSON array files (e.g. {@code TalkExcelConfigData.json}) by splitting the
 * top level array at element boundaries and parsing the slices in parallel.
 *
 * <p>Excel data files are flat arrays of objects, so a single 130 MB file used to be bound to a
 * single thread. Scanning the file once for its top level commas lets us hand contiguous slices to
 * the common ForkJoinPool, which makes loading these files scale with the core count.
 *
 * <p>Anything that is not a well formed top level array - or that fails to parse as a slice - falls
 * back to the regular serial parse, so the result is always identical to {@link
 * JsonUtils#loadToList(Path, Class)}.
 */
public final class ParallelJsonArrayLoader {

    /** Files below this size are parsed normally; splitting them is not worth the overhead. */
    private static final long MIN_SPLIT_SIZE = 2L * 1024 * 1024;

    /** Target slice size; also prevents creating more slices than there is actual work. */
    private static final long TARGET_SLICE_SIZE = 4L * 1024 * 1024;

    /** Upper bound on parallel slices. */
    private static final int MAX_SLICES = 32;

    private static final byte[] SLICE_OPEN = {'['};
    private static final byte[] SLICE_CLOSE = {']'};

    private ParallelJsonArrayLoader() {}

    /**
     * Loads a JSON array file, parsing it in parallel when it is large enough to benefit.
     *
     * @param file The file to load.
     * @param type The type of the array elements.
     * @return The parsed elements, in file order.
     */
    public static <T> List<T> loadList(Path file, Class<T> type) throws IOException {
        val size = Files.size(file);
        if (size < MIN_SPLIT_SIZE) return JsonUtils.loadToList(file, type);

        int slices = (int) Math.min(MAX_SLICES, size / TARGET_SLICE_SIZE);
        slices = Math.min(slices, Math.max(1, Runtime.getRuntime().availableProcessors()));
        if (slices < 2) return JsonUtils.loadToList(file, type);

        val bounds = scanTopLevelArray(file);
        if (bounds == null || bounds.cuts().size() < slices) return JsonUtils.loadToList(file, type);

        try {
            return splitIntoRanges(bounds, slices).parallelStream()
                    .map(range -> parseSlice(file, type, range))
                    .flatMap(List::stream)
                    .toList();
        } catch (Exception exception) {
            // Never let a splitting problem break loading - just parse the whole file serially.
            Grasscutter.getLogger()
                    .error(
                            "Parallel parse of {} failed, falling back to a serial parse.",
                            file.getFileName().toString(),
                            exception);
            return JsonUtils.loadToList(file, type);
        }
    }

    /** The byte layout of a flat JSON array: where it starts, ends, and its element separators. */
    private record ArrayBounds(long arrayStart, long arrayEnd, List<Long> cuts) {}

    /**
     * Scans the file once, recording the position of every comma separating two elements of the top
     * level array. Returns null when the file is not a flat, well formed JSON array.
     */
    private static ArrayBounds scanTopLevelArray(Path file) throws IOException {
        val cuts = new ArrayList<Long>();
        boolean inString = false;
        boolean escaped = false;
        boolean started = false;
        boolean ended = false;
        int bracketDepth = 0;
        int braceDepth = 0;
        long arrayStart = -1;
        long arrayEnd = -1;
        long offset = 0;

        try (var in = new BufferedInputStream(Files.newInputStream(file), 1 << 20)) {
            val buffer = new byte[1 << 20];
            int read;
            while ((read = in.read(buffer)) > 0) {
                for (int i = 0; i < read; i++) {
                    byte b = buffer[i];
                    long position = offset + i;

                    if (!started) {
                        // The document must be a top level array - skip whitespace until '['.
                        if (b == '[') {
                            started = true;
                            bracketDepth = 1;
                            arrayStart = position;
                        } else if (!isIgnorable(b)) {
                            return null;
                        }
                        continue;
                    }

                    if (inString) {
                        if (escaped) escaped = false;
                        else if (b == '\\') escaped = true;
                        else if (b == '"') inString = false;
                        continue;
                    }

                    switch (b) {
                        case '"' -> inString = true;
                        case '[' -> bracketDepth++;
                        case ']' -> {
                            bracketDepth--;
                            if (bracketDepth == 0 && !ended) {
                                ended = true;
                                arrayEnd = position;
                            }
                        }
                        case '{' -> braceDepth++;
                        case '}' -> braceDepth--;
                        case ',' -> {
                            // Only commas between top level elements delimit slices.
                            if (!ended && bracketDepth == 1 && braceDepth == 0) cuts.add(position);
                        }
                        default -> {}
                    }
                }
                offset += read;
            }
        }

        if (!started || !ended || arrayEnd <= arrayStart) return null;
        return new ArrayBounds(arrayStart, arrayEnd, cuts);
    }

    /** Whitespace (and a UTF-8 BOM) is allowed before the top level array. */
    private static boolean isIgnorable(byte b) {
        return b == ' ' || b == '\t' || b == '\n' || b == '\r' || (b & 0xFF) >= 0x80;
    }

    /**
     * Distributes the elements across at most {@code slices} contiguous byte ranges. Every range is
     * cut at a top level comma, so each one holds a whole number of elements.
     */
    private static List<long[]> splitIntoRanges(ArrayBounds bounds, int slices) {
        val cuts = bounds.cuts();
        val ranges = new ArrayList<long[]>(slices);
        long start = bounds.arrayStart() + 1;
        long span = bounds.arrayEnd() - bounds.arrayStart();

        for (int slice = 1; slice < slices; slice++) {
            long target = bounds.arrayStart() + (span * slice) / slices;
            int index = Collections.binarySearch(cuts, target);
            if (index < 0) index = -index - 1;

            if (index >= cuts.size()) break;
            long cut = cuts.get(index);
            if (cut < start) continue;

            ranges.add(new long[] {start, cut});
            start = cut + 1;
        }

        ranges.add(new long[] {start, bounds.arrayEnd()});
        return ranges;
    }

    /** Parses a single byte range as a JSON array of the given type. */
    private static <T> List<T> parseSlice(Path file, Class<T> type, long[] range) {
        try (var in = openSlice(file, range[0], range[1]);
                var reader = new InputStreamReader(in, StandardCharsets.UTF_8)) {
            val result = JsonUtils.loadToList(reader, type);
            return result == null ? List.<T>of() : result;
        } catch (IOException exception) {
            throw new UncheckedIOException(exception);
        }
    }

    /** Opens a stream over the given byte range, wrapped so that it reads as a JSON array. */
    private static InputStream openSlice(Path file, long start, long end) throws IOException {
        val channel = FileChannel.open(file, StandardOpenOption.READ);
        channel.position(start);

        return new SequenceInputStream(
                new SequenceInputStream(
                        new ByteArrayInputStream(SLICE_OPEN),
                        new BoundedInputStream(Channels.newInputStream(channel), end - start)),
                new ByteArrayInputStream(SLICE_CLOSE));
    }

    /** Limits the amount of bytes read from the underlying stream (and closes its channel). */
    private static final class BoundedInputStream extends FilterInputStream {
        private long remaining;

        BoundedInputStream(InputStream in, long limit) {
            super(in);
            this.remaining = Math.max(0, limit);
        }

        @Override
        public int read() throws IOException {
            if (this.remaining <= 0) return -1;
            int result = super.read();
            if (result >= 0) this.remaining--;
            return result;
        }

        @Override
        public int read(byte[] data, int offset, int length) throws IOException {
            if (this.remaining <= 0) return -1;

            int read = super.read(data, offset, (int) Math.min(length, this.remaining));
            if (read > 0) this.remaining -= read;
            return read;
        }
    }
}