package emu.grasscutter;

import java.io.InputStream;
import java.util.Properties;

/**
 * Build information injected by Gradle.
 *
 * <p>The values are read from the generated {@code lunagc-build-info.properties} resource
 * instead of being generated into Java sources. A new commit therefore does not change the
 * input of {@code compileJava}, which keeps the compiled classes reusable from the build
 * cache instead of being rebuilt from scratch.
 */
public final class BuildConfig {
    private static final String BUILD_INFO_RESOURCE = "/lunagc-build-info.properties";

    public static final String VERSION;
    public static final String GIT_HASH;

    static {
        Properties properties = new Properties();
        try (InputStream stream = BuildConfig.class.getResourceAsStream(BUILD_INFO_RESOURCE)) {
            if (stream != null) {
                properties.load(stream);
            }
        } catch (Exception ignored) {
            // Fall back to the defaults below.
        }

        VERSION = properties.getProperty("version", "dev");
        GIT_HASH = properties.getProperty("gitHash", "GIT_NOT_FOUND");
    }

    // Prevent instantiation.
    private BuildConfig() {}
}