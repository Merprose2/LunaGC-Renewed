package emu.grasscutter.tools;

import emu.grasscutter.data.GameResource;
import emu.grasscutter.data.ResourceCacheLoader;
import emu.grasscutter.data.ResourceType;
import emu.grasscutter.utils.JsonUtils;
import emu.grasscutter.utils.TsvUtils;

import org.reflections.Reflections;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.AtomicMoveNotSupportedException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

public final class ResourceCacheCompiler {
    private ResourceCacheCompiler() {
    }

    public static void main(String[] args) {
        try {
            Arguments arguments =
                    Arguments.parse(args);

            compile(arguments);

            System.out.println(
                    "Resource cache created successfully: "
                            + arguments.output);
        } catch (Exception exception) {
            System.err.println(
                    "Resource cache compilation failed:");

            exception.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static void compile(Arguments arguments)
            throws Exception {
        Path resourcesRoot =
                arguments.resources
                        .toAbsolutePath()
                        .normalize();

        Path output =
                arguments.output
                        .toAbsolutePath()
                        .normalize();
						
		List<Path> rawResourceFiles =
				discoverRawResourceFiles(
						resourcesRoot,
						output);

        if (!Files.isDirectory(resourcesRoot)) {
            throw new IllegalArgumentException(
                    "Resource directory does not exist: "
                            + resourcesRoot);
        }

        List<Class<? extends GameResource>> resourceClasses =
                discoverResourceClasses();

        if (resourceClasses.isEmpty()) {
            throw new IllegalStateException(
                    "No @ResourceType classes were discovered.");
        }

        List<PendingSection> pendingSections =
                new ArrayList<>();

        for (Class<? extends GameResource> resourceClass :
                resourceClasses) {
            ResourceType resourceType =
                    resourceClass.getAnnotation(ResourceType.class);

            List<GameResource> combinedResources =
                    new ArrayList<>();

            List<String> relativeSourceFiles =
                    new ArrayList<>();

            for (String resourceName : resourceType.name()) {
                Path source =
                        locateResourceFile(
                                resourcesRoot,
                                resourceName);

                if (source == null) {
                    throw new IllegalStateException(
                            "Unable to find "
                                    + resourceName
                                    + " for "
                                    + resourceClass.getName());
                }

                List<? extends GameResource> parsed =
                        readResourceFile(
                                source,
                                resourceClass);

                if (parsed == null) {
                    throw new IllegalStateException(
                            "Unable to parse "
                                    + source);
                }

                combinedResources.addAll(parsed);

                relativeSourceFiles.add(
                        resourcesRoot
                                .relativize(source)
                                .toString()
                                .replace('\\', '/'));
            }

            String entryName =
                    "excel/"
                            + resourceClass
                                    .getName()
                                    .replace('.', '/')
                            + ".json";

			byte[] payload;

			try {
				payload = JsonUtils.encodeResourceCache(combinedResources).getBytes(StandardCharsets.UTF_8);
			} catch (Exception exception) {
				throw new IllegalStateException(
						"Unable to serialize cache section for "
								+ resourceClass.getName()
								+ " containing "
								+ combinedResources.size()
								+ " objects.",
						exception);
			}

            ResourceCacheLoader.Section section =
                    new ResourceCacheLoader.Section();

            section.className =
                    resourceClass.getName();

            section.loadPriority =
                    resourceType.loadPriority().value();

            section.entryName = entryName;
            section.objectCount =
                    combinedResources.size();

            section.sha256 =
                    ResourceCacheLoader.sha256Hex(payload);

            section.sourceFiles.addAll(
                    relativeSourceFiles);

            pendingSections.add(
                    new PendingSection(
                            section,
                            payload));

            System.out.printf(
                    "Prepared %-70s %,d objects%n",
                    resourceClass.getSimpleName(),
                    combinedResources.size());
        }

        ResourceCacheLoader.Manifest manifest =
                new ResourceCacheLoader.Manifest();

        manifest.magic =
                ResourceCacheLoader.MAGIC;

        manifest.formatVersion =
                ResourceCacheLoader.FORMAT_VERSION;

        manifest.gameVersion =
                ResourceCacheLoader.GAME_VERSION;

        manifest.schemaName =
                ResourceCacheLoader.SCHEMA_NAME;

        manifest.schemaHash =
                ResourceCacheLoader.computeSchemaFingerprint(
                        resourceClasses);

		manifest.serverCommit =
				arguments.serverCommit;

		manifest.resourcesCommit =
				arguments.resourcesCommit;

		manifest.rawRoot =
				ResourceCacheLoader.RAW_ROOT;

		manifest.rawFileCount =
				rawResourceFiles.size();

		manifest.resourcesSourceHash =
				calculateSourceHash(
						resourcesRoot,
						new LinkedHashSet<>(
								rawResourceFiles));

		manifest.createdAtUtc =
				Instant.now().toString();

        pendingSections.stream()
                .map(PendingSection::metadata)
                .forEach(manifest.sections::add);

		writeCache(
				output,
				manifest,
				pendingSections,
				resourcesRoot,
				rawResourceFiles);

        ResourceCacheLoader.ValidationResult validation =
                ResourceCacheLoader.validate(output);

        if (!validation.valid()) {
            Files.deleteIfExists(output);

            throw new IllegalStateException(
                    "Generated cache failed validation: "
                            + validation.message());
        }

        System.out.printf(
                "Validated %,d sections containing %,d objects.%n",
                validation.sectionCount(),
                validation.objectCount());
    }
	
	private static List<Path> discoverRawResourceFiles(
			Path resourcesRoot,
			Path output)
			throws IOException {
		Path normalizedOutput =
				output.toAbsolutePath().normalize();

		Path temporaryOutput =
				normalizedOutput.resolveSibling(
						normalizedOutput.getFileName()
								+ ".tmp");

		try (var stream = Files.walk(resourcesRoot)) {
			return stream
					.filter(Files::isRegularFile)
					.map(path -> path.toAbsolutePath().normalize())
					.filter(
							path ->
									!path.equals(normalizedOutput)
											&& !path.equals(
													temporaryOutput))
					.filter(
							path -> {
								Path relative =
										resourcesRoot.relativize(path);

								if (relative.getNameCount() == 0) {
									return false;
								}

								String firstComponent =
										relative.getName(0)
												.toString();

								/*
								 * Repository metadata is not needed by LunaGC
								 * at runtime.
								 */
								return !".git".equals(firstComponent)
										&& !".github".equals(
												firstComponent);
							})
					.sorted(
							Comparator.comparing(
									path ->
											resourcesRoot
													.relativize(path)
													.toString()
													.replace(
															'\\',
															'/')))
					.toList();
		}
	}

    private static List<Class<? extends GameResource>>
            discoverResourceClasses() {
        Reflections reflections =
                new Reflections("emu.grasscutter");

        List<Class<? extends GameResource>> classes =
                new ArrayList<>(
                        reflections.getSubTypesOf(
                                GameResource.class));

        classes.removeIf(
                resourceClass ->
                        resourceClass.getAnnotation(
                                ResourceType.class)
                                == null);

        classes.sort(
                Comparator
                        .comparingInt(
                                (Class<? extends GameResource> c) ->
                                        c.getAnnotation(
                                                        ResourceType.class)
                                                .loadPriority()
                                                .value())
                        .reversed()
                        .thenComparing(Class::getName));

        return classes;
    }

    private static Path locateResourceFile(
            Path resourcesRoot,
            String resourceName) {
        String nameWithoutExtension =
                removeExtension(resourceName);

        /*
         * Replicates LunaGC's current preference:
         *
         * Server before ExcelBinOutput
         * TSJ before JSON before TSV
         */
        for (String directory :
                List.of("Server", "ExcelBinOutput")) {
            for (String extension :
                    List.of("tsj", "json", "tsv")) {
                Path candidate =
                        resourcesRoot
                                .resolve(directory)
                                .resolve(
                                        nameWithoutExtension
                                                + "."
                                                + extension);

                if (Files.isRegularFile(candidate)) {
                    return candidate;
                }
            }
        }

        return null;
    }

    @SuppressWarnings("unchecked")
    private static List<? extends GameResource> readResourceFile(
            Path source,
            Class<? extends GameResource> resourceClass)
            throws Exception {
        String extension =
                extensionOf(source);

        return switch (extension) {
            case "json" ->
                    (List<? extends GameResource>)
                            JsonUtils.loadToList(
                                    source,
                                    resourceClass);

            case "tsj" ->
                    (List<? extends GameResource>)
                            TsvUtils.loadTsjToListSetField(
                                    source,
                                    resourceClass);

            case "tsv" ->
                    (List<? extends GameResource>)
                            TsvUtils.loadTsvToListSetField(
                                    source,
                                    resourceClass);

            default ->
                    throw new IllegalArgumentException(
                            "Unsupported resource extension: "
                                    + source);
        };
    }

	private static void writeCache(
			Path output,
			ResourceCacheLoader.Manifest manifest,
			List<PendingSection> sections,
			Path resourcesRoot,
			List<Path> rawResourceFiles)
			throws IOException {
		Path parent = output.getParent();

		if (parent != null) {
			Files.createDirectories(parent);
		}

		Path temporary =
				output.resolveSibling(
						output.getFileName()
								+ ".tmp");

		Files.deleteIfExists(temporary);

		try (ZipOutputStream zip =
				new ZipOutputStream(
						Files.newOutputStream(temporary))) {
			zip.setLevel(9);

			writeZipEntry(
					zip,
					ResourceCacheLoader.MANIFEST_ENTRY,
					JsonUtils.encode(manifest)
							.getBytes(
									StandardCharsets.UTF_8));

			/*
			 * Write the normalized GameResource sections.
			 */
			for (PendingSection section : sections) {
				writeZipEntry(
						zip,
						section.metadata.entryName,
						section.payload);
			}

			/*
			 * Write the complete original resource tree.
			 *
			 * Existing loaders will read these files through Java's ZIP
			 * filesystem provider.
			 */
			for (Path rawFile : rawResourceFiles) {
				String relative =
						resourcesRoot
								.relativize(rawFile)
								.toString()
								.replace('\\', '/');

				String entryName =
						ResourceCacheLoader.RAW_ROOT
								+ "/"
								+ relative;

				writeZipFileEntry(
						zip,
						entryName,
						rawFile);
			}
		}

		try {
			Files.move(
					temporary,
					output,
					StandardCopyOption.REPLACE_EXISTING,
					StandardCopyOption.ATOMIC_MOVE);
		} catch (AtomicMoveNotSupportedException ignored) {
			Files.move(
					temporary,
					output,
					StandardCopyOption.REPLACE_EXISTING);
		}
	}

    private static void writeZipEntry(
            ZipOutputStream zip,
            String name,
            byte[] contents)
            throws IOException {
        ZipEntry entry = new ZipEntry(name);

        /*
         * Keep output deterministic. Otherwise each build contains current
         * ZIP timestamps and produces a different checksum.
         */
        entry.setTime(0L);

        zip.putNextEntry(entry);
        zip.write(contents);
        zip.closeEntry();
    }
	
	private static void writeZipFileEntry(
			ZipOutputStream zip,
			String name,
			Path source)
			throws IOException {
		ZipEntry entry =
				new ZipEntry(name);

		/*
		 * Use a fixed timestamp so identical inputs produce stable archives.
		 */
		entry.setTime(0L);

		zip.putNextEntry(entry);
		Files.copy(source, zip);
		zip.closeEntry();
	}

    private static String calculateSourceHash(
            Path root,
            Set<Path> files)
            throws Exception {
        MessageDigest digest =
                MessageDigest.getInstance("SHA-256");

        List<Path> sortedFiles =
                files.stream()
                        .sorted(
                                Comparator.comparing(
                                        path ->
                                                root
                                                        .relativize(path)
                                                        .toString()))
                        .toList();

        for (Path file : sortedFiles) {
            String relative =
                    root.relativize(file)
                            .toString()
                            .replace('\\', '/');

            digest.update(
                    relative.getBytes(
                            StandardCharsets.UTF_8));

            digest.update((byte) 0);
            digest.update(Files.readAllBytes(file));
            digest.update((byte) 0);
        }

        return HexFormat.of()
                .formatHex(digest.digest());
    }

    private static String extensionOf(Path path) {
        String filename =
                path.getFileName().toString();

        int separator =
                filename.lastIndexOf('.');

        return separator < 0
                ? ""
                : filename.substring(separator + 1)
                        .toLowerCase();
    }

    private static String removeExtension(String filename) {
        int separator =
                filename.lastIndexOf('.');

        return separator < 0
                ? filename
                : filename.substring(0, separator);
    }

    private record PendingSection(
            ResourceCacheLoader.Section metadata,
            byte[] payload) {
    }

    private static final class Arguments {
        private Path resources;
        private Path output;

        private String serverCommit = "unknown";
        private String resourcesCommit = "unknown";

        private static Arguments parse(String[] args) {
            Map<String, String> values =
                    new TreeMap<>();

            for (int index = 0; index < args.length; index++) {
                String argument = args[index];

                if (!argument.startsWith("--")) {
                    throw new IllegalArgumentException(
                            "Unexpected argument: "
                                    + argument);
                }

                if (index + 1 >= args.length) {
                    throw new IllegalArgumentException(
                            "Missing value after "
                                    + argument);
                }

                values.put(
                        argument,
                        args[++index]);
            }

            Arguments parsed = new Arguments();

            String resources =
                    values.get("--resources");

            String output =
                    values.get("--output");

            if (resources == null || resources.isBlank()) {
                throw new IllegalArgumentException(
                        "--resources is required.");
            }

            if (output == null || output.isBlank()) {
                throw new IllegalArgumentException(
                        "--output is required.");
            }

            parsed.resources =
                    Path.of(resources);

            parsed.output =
                    Path.of(output);

            parsed.serverCommit =
                    values.getOrDefault(
                            "--server-commit",
                            "unknown");

            parsed.resourcesCommit =
                    values.getOrDefault(
                            "--resources-commit",
                            "unknown");

            return parsed;
        }
    }
}