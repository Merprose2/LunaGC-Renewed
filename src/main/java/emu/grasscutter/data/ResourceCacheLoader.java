package emu.grasscutter.data;

import emu.grasscutter.Grasscutter;
import emu.grasscutter.utils.JsonUtils;
import it.unimi.dsi.fastutil.ints.Int2ObjectMap;

import java.io.ByteArrayInputStream;
import java.io.InputStreamReader;
import java.lang.reflect.Modifier;
import java.lang.reflect.Field;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public final class ResourceCacheLoader {
    public static final String DEFAULT_CACHE_FILE_NAME =
            "LunaGC-6.6-resources.cache";

    public static final String MAGIC = "LGCRES66";
    public static final int FORMAT_VERSION = 1;
    public static final String GAME_VERSION = "6.6.0";

    /*
     * Increment this manually if the meaning of the cache changes even when
     * no Java resource field changed.
     */
    public static final String SCHEMA_NAME = "annotated-excel-resources-v1";

    public static final String MANIFEST_ENTRY = "manifest.json";
	
	public static final String RAW_ROOT = "raw";

	private static final List<String> REQUIRED_RAW_DIRECTORIES =
			List.of(
					"BinOutput",
					"ExcelBinOutput",
					"ScriptSceneData",
					"Scripts",
					"Server",
					"TextMap");

    private ResourceCacheLoader() {
    }

    /**
     * Attempts to load the annotated Excel resource cache.
     *
     * @return true when the cache was loaded; false when the cache was
     *         absent or failed validation before modifying GameData.
     */
    public static boolean tryLoad(Path cachePath) {
        if (cachePath == null || !Files.isRegularFile(cachePath)) {
            Grasscutter.getLogger()
                    .info(
                            "Resource cache was not found at {}. "
                                    + "Using the normal resources folder.",
                            cachePath);

            return false;
        }

        final CacheContents contents;

        /*
         * Validate and deserialize everything before touching GameData.
         * This permits a safe folder fallback when the file is corrupt.
         */
        try {
            contents = readAndValidate(cachePath);
        } catch (Exception exception) {
            Grasscutter.getLogger()
                    .warn(
                            "Resource cache {} is invalid. "
                                    + "Using the normal resources folder.",
                            cachePath,
                            exception);

            return false;
        }

        /*
         * Once applying begins, an exception is considered fatal.
         * Falling back after partially running GameResource.onLoad() could
         * leave derived maps containing duplicated or inconsistent data.
         */
        apply(contents);

        Grasscutter.getLogger()
                .info(
                        "Loaded {} resource classes containing {} objects "
                                + "from {}.",
                        contents.sections.size(),
                        contents.totalObjectCount(),
                        cachePath);

        return true;
    }

    /**
     * Validates a cache without modifying GameData.
     */
    public static ValidationResult validate(Path cachePath) {
        try {
            CacheContents contents = readAndValidate(cachePath);

            return new ValidationResult(
                    true,
                    "Cache is valid.",
                    contents.sections.size(),
                    contents.totalObjectCount());
        } catch (Exception exception) {
            return new ValidationResult(
                    false,
                    exception.getMessage(),
                    0,
                    0);
        }
    }

    private static CacheContents readAndValidate(Path cachePath)
            throws Exception {
        try (ZipFile zip = new ZipFile(cachePath.toFile())) {
            ZipEntry manifestEntry = zip.getEntry(MANIFEST_ENTRY);

            if (manifestEntry == null) {
                throw new IllegalStateException(
                        "The cache does not contain manifest.json.");
            }

            Manifest manifest;

            try (InputStreamReader reader =
                    new InputStreamReader(
                            zip.getInputStream(manifestEntry),
                            StandardCharsets.UTF_8)) {
                manifest =
                        JsonUtils.loadToClass(
                                reader,
                                Manifest.class);
            }

            validateManifestHeader(manifest);
			validateRawResources(zip, manifest);

            if (manifest.sections == null
                    || manifest.sections.isEmpty()) {
                throw new IllegalStateException(
                        "The cache manifest contains no resource sections.");
            }

            List<LoadedSection> loadedSections =
                    new ArrayList<>();

            Set<String> seenEntries = new HashSet<>();
            Set<String> seenClasses = new HashSet<>();

            for (Section section : manifest.sections) {
                if (section == null) {
                    throw new IllegalStateException(
                            "The manifest contains a null section.");
                }

                if (!seenEntries.add(section.entryName)) {
                    throw new IllegalStateException(
                            "Duplicate cache entry: "
                                    + section.entryName);
                }

                if (!seenClasses.add(section.className)) {
                    throw new IllegalStateException(
                            "Duplicate resource class: "
                                    + section.className);
                }

                ZipEntry sectionEntry =
                        zip.getEntry(section.entryName);

                if (sectionEntry == null) {
                    throw new IllegalStateException(
                            "Missing cache section: "
                                    + section.entryName);
                }

                byte[] sectionBytes =
                        zip.getInputStream(sectionEntry)
                                .readAllBytes();

                String actualHash =
                        sha256Hex(sectionBytes);

                if (!actualHash.equalsIgnoreCase(section.sha256)) {
                    throw new IllegalStateException(
                            "Checksum mismatch for "
                                    + section.entryName);
                }

                Class<?> rawClass =
                        Class.forName(section.className);

                if (!GameResource.class.isAssignableFrom(rawClass)) {
                    throw new IllegalStateException(
                            section.className
                                    + " is not a GameResource.");
                }

                @SuppressWarnings("unchecked")
                Class<? extends GameResource> resourceClass =
                        (Class<? extends GameResource>) rawClass;

                ResourceType resourceType =
                        resourceClass.getAnnotation(ResourceType.class);

                if (resourceType == null) {
                    throw new IllegalStateException(
                            section.className
                                    + " no longer has @ResourceType.");
                }

                if (resourceType.loadPriority().value()
                        != section.loadPriority) {
                    throw new IllegalStateException(
                            "Load priority mismatch for "
                                    + section.className);
                }

                List<? extends GameResource> resources;

				try (InputStreamReader reader =
						new InputStreamReader(
								new ByteArrayInputStream(sectionBytes),
								StandardCharsets.UTF_8)) {
					resources =
							JsonUtils.loadResourceCacheList(
									reader,
									resourceClass);
                }

                if (resources == null) {
                    throw new IllegalStateException(
                            "Unable to deserialize "
                                    + section.entryName);
                }

                if (resources.size() != section.objectCount) {
                    throw new IllegalStateException(
                            "Object count mismatch for "
                                    + section.entryName
                                    + ": expected "
                                    + section.objectCount
                                    + ", got "
                                    + resources.size());
                }

                loadedSections.add(
                        new LoadedSection(
                                section,
                                resourceClass,
                                resources));
            }

            String currentSchemaHash =
                    computeSchemaFingerprint(
                            loadedSections.stream()
                                    .map(LoadedSection::resourceClass)
                                    .toList());

            if (!currentSchemaHash.equalsIgnoreCase(
                    manifest.schemaHash)) {
                throw new IllegalStateException(
                        "The cache schema does not match this server build.");
            }

            loadedSections.sort(
                    Comparator
                            .comparingInt(
                                    (LoadedSection section) ->
                                            section.metadata.loadPriority)
                            .reversed()
                            .thenComparing(
                                    section ->
                                            section.metadata.className));

            return new CacheContents(
                    manifest,
                    loadedSections);
        }
    }
	
	private static void validateRawResources(ZipFile zip, Manifest manifest) {
		if (manifest.rawRoot == null
				|| manifest.rawRoot.isBlank()) {
			throw new IllegalStateException(
					"The resource cache has no raw resource root.");
		}

		String rawPrefix =
				manifest.rawRoot.endsWith("/")
						? manifest.rawRoot
						: manifest.rawRoot + "/";

		List<String> rawFiles =
				zip.stream()
						.filter(entry -> !entry.isDirectory())
						.map(ZipEntry::getName)
						.filter(name -> name.startsWith(rawPrefix))
						.toList();

		if (rawFiles.size() != manifest.rawFileCount) {
			throw new IllegalStateException(
					"Raw resource file count mismatch: expected "
							+ manifest.rawFileCount
							+ ", got "
							+ rawFiles.size());
		}

		for (String requiredDirectory :
				REQUIRED_RAW_DIRECTORIES) {
			String requiredPrefix =
					rawPrefix
							+ requiredDirectory
							+ "/";

			boolean found =
					rawFiles.stream()
							.anyMatch(
									name ->
											name.startsWith(
													requiredPrefix));

			if (!found) {
				throw new IllegalStateException(
						"The cache is missing required raw directory: "
								+ requiredDirectory);
			}
		}
	}

    private static void validateManifestHeader(Manifest manifest) {
        if (manifest == null) {
            throw new IllegalStateException(
                    "Unable to decode the cache manifest.");
        }

        if (!MAGIC.equals(manifest.magic)) {
            throw new IllegalStateException(
                    "Invalid cache magic.");
        }

        if (manifest.formatVersion != FORMAT_VERSION) {
            throw new IllegalStateException(
                    "Unsupported cache format version: "
                            + manifest.formatVersion);
        }

        if (!GAME_VERSION.equals(manifest.gameVersion)) {
            throw new IllegalStateException(
                    "This cache targets game version "
                            + manifest.gameVersion
                            + ", not "
                            + GAME_VERSION);
        }

        if (!SCHEMA_NAME.equals(manifest.schemaName)) {
            throw new IllegalStateException(
                    "Unsupported cache schema: "
                            + manifest.schemaName);
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void apply(CacheContents contents) {
        /*
         * The cache is loaded during a fresh startup. Clear the annotated
         * maps before inserting cache data.
         */
        for (LoadedSection loaded : contents.sections) {
            Int2ObjectMap map =
                    (Int2ObjectMap)
                            GameData.getMapByResourceDef(
                                    loaded.resourceClass);

            if (map == null) {
                throw new IllegalStateException(
                        "No GameData map exists for "
                                + loaded.resourceClass.getName());
            }

            map.clear();
        }

        Set<String> loadedSimpleNames =
                new HashSet<>();

        for (LoadedSection loaded : contents.sections) {
            Int2ObjectMap map =
                    (Int2ObjectMap)
                            GameData.getMapByResourceDef(
                                    loaded.resourceClass);

            for (GameResource resource : loaded.resources) {
                if (resource == null) {
                    continue;
                }

                /*
                 * Rebuild transient and derived information exactly as the
                 * normal ResourceLoader does.
                 */
                resource.onLoad();
                map.put(resource.getId(), resource);
            }

            loadedSimpleNames.add(
                    loaded.resourceClass.getSimpleName());
        }

        ResourceLoader.markCachedResourcesLoaded(
                loadedSimpleNames);
    }

    public static String computeSchemaFingerprint(
            Collection<Class<? extends GameResource>> classes) {
        try {
            MessageDigest digest =
                    MessageDigest.getInstance("SHA-256");

            classes.stream()
                    .sorted(Comparator.comparing(Class::getName))
                    .forEach(
                            resourceClass -> {
                                updateDigest(
                                        digest,
                                        resourceClass.getName());

                                Class<?> current = resourceClass;

                                while (current != null
                                        && current != Object.class) {
                                    List<String> fieldDescriptions =
                                            new ArrayList<>();

                                    for (Field field :
                                            current.getDeclaredFields()) {
                                        int modifiers =
                                                field.getModifiers();

										if (Modifier.isStatic(modifiers)
												|| Modifier.isTransient(modifiers)
												|| field.isSynthetic()
												|| field.getAnnotation(
																dev.morphia.annotations.Transient.class)
														!= null) {
											continue;
										}

                                        fieldDescriptions.add(
                                                current.getName()
                                                        + "#"
                                                        + field.getName()
                                                        + ":"
                                                        + field
                                                                .getGenericType()
                                                                .getTypeName());
                                    }

                                    fieldDescriptions.stream()
                                            .sorted()
                                            .forEach(
                                                    description ->
                                                            updateDigest(
                                                                    digest,
                                                                    description));

                                    current =
                                            current.getSuperclass();
                                }
                            });

            return HexFormat.of()
                    .formatHex(digest.digest());
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to calculate resource schema hash.",
                    exception);
        }
    }

    private static void updateDigest(
            MessageDigest digest,
            String value) {
        digest.update(
                value.getBytes(StandardCharsets.UTF_8));

        digest.update((byte) 0);
    }

    public static String sha256Hex(byte[] bytes) {
        try {
            return HexFormat.of()
                    .formatHex(
                            MessageDigest
                                    .getInstance("SHA-256")
                                    .digest(bytes));
        } catch (Exception exception) {
            throw new IllegalStateException(
                    "Unable to calculate SHA-256.",
                    exception);
        }
    }

    private record LoadedSection(
            Section metadata,
            Class<? extends GameResource> resourceClass,
            List<? extends GameResource> resources) {
    }

    private record CacheContents(
            Manifest manifest,
            List<LoadedSection> sections) {
        long totalObjectCount() {
            return sections.stream()
                    .mapToLong(
                            section ->
                                    section.resources.size())
                    .sum();
        }
    }

    public record ValidationResult(
            boolean valid,
            String message,
            int sectionCount,
            long objectCount) {
    }

    /*
     * These use ordinary classes rather than records because they are
     * deserialized by the repository's existing Gson version.
     */
    public static final class Manifest {
        public String magic;
        public int formatVersion;
        public String gameVersion;
        public String schemaName;
        public String schemaHash;

        public String serverCommit;
        public String resourcesCommit;
        public String resourcesSourceHash;
        public String createdAtUtc;
		
		public String rawRoot = RAW_ROOT;
		public int rawFileCount;

        public List<Section> sections =
                new ArrayList<>();
    }

    public static final class Section {
        public String className;
        public int loadPriority;
        public String entryName;
        public int objectCount;
        public String sha256;

        public List<String> sourceFiles =
                new ArrayList<>();
    }
}