package emu.grasscutter.utils;

import emu.grasscutter.Grasscutter;
import java.io.*;
import java.net.URI;
import java.net.URISyntaxException;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.nio.file.FileSystem;
import java.util.*;
import java.util.stream.*;
import lombok.val;

public final class FileUtils {
    private static final Path DATA_DEFAULT_PATH;
    private static final Path DATA_USER_PATH = Path.of(Grasscutter.config.folderStructure.data);
    private static final Path PACKETS_PATH = Path.of(Grasscutter.config.folderStructure.packets);
    private static final Path PLUGINS_PATH = Path.of(Grasscutter.config.folderStructure.plugins);
    private static final Path CACHE_PATH = Path.of(Grasscutter.config.folderStructure.cache);
    private static final Path RESOURCES_PATH;
    private static final Path SCRIPTS_PATH;
	@SuppressWarnings("unused")
	private static final FileSystem RESOURCE_ARCHIVE_FILE_SYSTEM;
    private static final String[] TSJ_JSON_TSV = {"tsj", "json", "tsv"};

    static {
        FileSystem fs = null;
        Path path = null;
        // Setup access to jar resources
        try {
            var uri = Grasscutter.class.getResource("/defaults/data").toURI();
            switch (uri.getScheme()) {
                case "jar": // When running normally, as a jar
                case "zip": // Honestly I have no idea what setup would result in this, but this should work
                    // regardless
                    fs =
                            FileSystems.newFileSystem(
                                    uri,
                                    Map.of()); // Have to mount zip filesystem. This leaks, but we want to keep it
                    // forever anyway.
                    // Fall-through
                case "file": // When running in an IDE
                    path = Path.of(uri); // Can access directly
                    break;
                default:
                    Grasscutter.getLogger()
                            .error("Invalid URI scheme for class resources: " + uri.getScheme());
                    break;
            }
        } catch (URISyntaxException | IOException e) {
            // Failed to load this jar. How?
            Grasscutter.getLogger().error("Failed to load jar?!");
        } finally {
            DATA_DEFAULT_PATH = path;
            Grasscutter.getLogger().debug("Setting path for default data: " + path.toAbsolutePath());
        }

		// Setup Resources path.
		final String configuredResources = Grasscutter.config.folderStructure.resources;

		FileSystem mountedResourceFileSystem = null;
		Path resolvedResourcesPath;

		if (isResourceArchive(configuredResources)) {
			Path archivePath =
					Path.of(configuredResources)
							.toAbsolutePath()
							.normalize();

			try {
				URI archiveUri =
						URI.create(
								"jar:"
										+ archivePath.toUri());

				mountedResourceFileSystem =
						FileSystems.newFileSystem(
								archiveUri,
								Map.of());

				resolvedResourcesPath =
						findResourcesRoot(
								mountedResourceFileSystem);

				if (resolvedResourcesPath == null) {
					throw new IOException(
							"ExcelBinOutput was not found inside "
									+ archivePath);
				}

				Grasscutter.getLogger()
						.info(
								"Resources will be loaded from archive {} at {}",
								archivePath,
								resolvedResourcesPath);
			} catch (Exception exception) {
				Grasscutter.getLogger()
						.error(
								"Failed to mount resource archive \""
										+ archivePath
										+ "\". Falling back to ./resources/.",
								exception);

				if (mountedResourceFileSystem != null) {
					try {
						mountedResourceFileSystem.close();
					} catch (IOException ignored) {
					}
				}

				mountedResourceFileSystem = null;
				resolvedResourcesPath =
						Path.of("./resources/");
			}
		} else {
			resolvedResourcesPath =
					Path.of(configuredResources);
		}

		RESOURCE_ARCHIVE_FILE_SYSTEM = mountedResourceFileSystem;

		RESOURCES_PATH = resolvedResourcesPath;

        // Setup Scripts path
        final String scripts = Grasscutter.config.folderStructure.scripts;
        SCRIPTS_PATH =
                (scripts.startsWith("resources:"))
                        ? RESOURCES_PATH.resolve(scripts.substring("resources:".length()))
                        : Path.of(scripts);
    }

    /* Apply after initialization. */
    private static final Path[] DATA_PATHS = {DATA_USER_PATH, DATA_DEFAULT_PATH};

    public static Path getDataPathTsjJsonTsv(String filename) {
        return getDataPathTsjJsonTsv(filename, true);
    }

    public static Path getDataPathTsjJsonTsv(String filename, boolean fallback) {
        val name = getFilenameWithoutExtension(filename);
        for (val data_path : DATA_PATHS) {
            for (val ext : TSJ_JSON_TSV) {
                val path = data_path.resolve(name + "." + ext);
                if (Files.exists(path)) return path;
            }
        }
        return fallback
                ? DATA_USER_PATH.resolve(name + ".tsj")
                : null; // Maybe they want to write to a new file
    }
	
	private static boolean isResourceArchive(
			String resourcePath) {
		if (resourcePath == null) {
			return false;
		}

		String normalized =
				resourcePath.toLowerCase(
						Locale.ROOT);

		return normalized.endsWith(".zip")
				|| normalized.endsWith(".cache");
	}

	private static Path findResourcesRoot(
			FileSystem fileSystem)
			throws IOException {
		Path root =
				fileSystem.getPath("/");

		/*
		 * Supports:
		 *
		 * /ExcelBinOutput
		 * /some-folder/ExcelBinOutput
		 * /raw/ExcelBinOutput
		 */
		try (Stream<Path> paths =
				Files.find(
						root,
						4,
						(candidate, attributes) -> {
							if (!attributes.isDirectory()) {
								return false;
							}

							Path filename =
									candidate.getFileName();

							return filename != null
									&& "ExcelBinOutput"
											.equals(
													filename.toString());
						})) {
			Optional<Path> excelDirectory =
					paths.findFirst();

			if (excelDirectory.isEmpty()) {
				return null;
			}

			Path parent =
					excelDirectory.get().getParent();

			return parent != null
					? parent
					: root;
		}
	}

    public static Path getDataPath(String path) {
        Path userPath = DATA_USER_PATH.resolve(path);
        if (Files.exists(userPath)) return userPath;
        Path defaultPath = DATA_DEFAULT_PATH.resolve(path);
        if (Files.exists(defaultPath)) return defaultPath;
        return userPath; // Maybe they want to write to a new file
    }

    public static Path getDataUserPath(String path) {
        return DATA_USER_PATH.resolve(path);
    }

    public static Path getCachePath(String path) {
        return CACHE_PATH.resolve(path);
    }

    public static Path getPacketPath(String path) {
        return PACKETS_PATH.resolve(path);
    }

    public static Path getPluginPath(String path) {
        return PLUGINS_PATH.resolve(path);
    }

    public static Path getResourcePath(String path) {
        return RESOURCES_PATH.resolve(path);
    }

    public static Path getExcelPath(String filename) {
        Path p = getTsjJsonTsv(RESOURCES_PATH.resolve("Server"), filename);
        return Files.exists(p) ? p : getTsjJsonTsv(RESOURCES_PATH.resolve("ExcelBinOutput"), filename);
    }

    // Gets path of a resource.
    // If multiple formats of it exist, priority is TSJ > JSON > TSV
    // If none exist, return the TSJ path, in case it wants to create a file
    public static Path getTsjJsonTsv(Path root, String filename) {
        val name = getFilenameWithoutExtension(filename);
        for (val ext : TSJ_JSON_TSV) {
            val path = root.resolve(name + "." + ext);
            if (Files.exists(path)) return path;
        }
        return root.resolve(name + ".tsj");
    }

    public static Path getScriptPath(String path) {
        return SCRIPTS_PATH.resolve(path);
    }

    public static void write(String dest, byte[] bytes) {
        Path path = Path.of(dest);

        try {
            Files.write(path, bytes);
        } catch (IOException e) {
            Grasscutter.getLogger().warn("Failed to write file: " + dest);
        }
    }

    public static byte[] read(String dest) {
        return read(Path.of(dest));
    }

    public static byte[] read(Path path) {
        try {
            return Files.readAllBytes(path);
        } catch (IOException e) {
            Grasscutter.getLogger().warn("Failed to read file: " + path);
        }

        return new byte[0];
    }

    public static InputStream readResourceAsStream(String resourcePath) {
        return Grasscutter.class.getResourceAsStream(resourcePath);
    }

    public static byte[] readResource(String resourcePath) {
        try (InputStream is = Grasscutter.class.getResourceAsStream(resourcePath)) {
            return is.readAllBytes();
        } catch (Exception exception) {
            Grasscutter.getLogger().warn("Failed to read resource: " + resourcePath);
            Grasscutter.getLogger().debug("Failed to load resource: " + resourcePath, exception);
        }

        return new byte[0];
    }

    public static byte[] read(File file) {
        return read(file.getPath());
    }

    public static void copyResource(String resourcePath, String destination) {
        try {
            byte[] resource = FileUtils.readResource(resourcePath);
            FileUtils.write(destination, resource);
        } catch (Exception exception) {
            Grasscutter.getLogger().warn("Failed to copy resource: " + resourcePath + "\n" + exception);
        }
    }

    @Deprecated // Misnamed legacy function
    public static String getFilenameWithoutPath(String filename) {
        return getFilenameWithoutExtension(filename);
    }

    public static String getFilenameWithoutExtension(String filename) {
        int i = filename.lastIndexOf(".");
        return (i < 0) ? filename : filename.substring(0, i);
    }

    public static String getFileExtension(Path path) {
        val filename = path.toString();
        int i = filename.lastIndexOf(".");
        return (i < 0) ? "" : filename.substring(i + 1);
    }

    public static List<Path> getPathsFromResource(String folder) throws URISyntaxException {
        try {
            // file walks JAR
            return Files.walk(Path.of(Grasscutter.class.getResource(folder).toURI()))
                    .filter(Files::isRegularFile)
                    .collect(Collectors.toList());
        } catch (IOException e) {
            // Eclipse puts resources in its bin folder
            try {
                return Files.walk(Path.of(System.getProperty("user.dir"), folder))
                        .filter(Files::isRegularFile)
                        .collect(Collectors.toList());
            } catch (IOException ignored) {
                return null;
            }
        }
    }

    @SuppressWarnings("ResultOfMethodCallIgnored")
    public static String readToString(InputStream file) throws IOException {
        byte[] content = file.readAllBytes();

        return new String(content, StandardCharsets.UTF_8);
    }
}
