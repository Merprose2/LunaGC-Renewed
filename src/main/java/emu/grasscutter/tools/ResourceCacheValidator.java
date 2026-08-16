package emu.grasscutter.tools;

import emu.grasscutter.data.ResourceCacheLoader;

import java.nio.file.Path;

public final class ResourceCacheValidator {
    private ResourceCacheValidator() {
    }

    public static void main(String[] args) {
        try {
            Path input =
                    parseInput(args);

            ResourceCacheLoader.ValidationResult result =
                    ResourceCacheLoader.validate(input);

            if (!result.valid()) {
                System.err.println(
                        "Resource cache validation failed: "
                                + result.message());

                System.exit(1);
            }

            System.out.printf(
                    "Resource cache is valid.%n"
                            + "Sections: %,d%n"
                            + "Objects:  %,d%n",
                    result.sectionCount(),
                    result.objectCount());
        } catch (Exception exception) {
            System.err.println(
                    "Unable to validate resource cache:");

            exception.printStackTrace(System.err);
            System.exit(1);
        }
    }

    private static Path parseInput(String[] args) {
        for (int index = 0;
                index < args.length;
                index++) {
            if ("--input".equals(args[index])) {
                if (index + 1 >= args.length) {
                    throw new IllegalArgumentException(
                            "Missing value after --input.");
                }

                return Path.of(args[index + 1]);
            }
        }

        throw new IllegalArgumentException(
                "--input is required.");
    }
}