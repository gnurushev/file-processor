package com.gnurushev.fileprocessor;

import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.List;
import java.util.Optional;

public record LaunchOptions(
    Optional<Path> startupPdf,
    Optional<PrintJobWatchConfig> printJobWatchConfig
) {
    public static LaunchOptions parse(String[] rawArguments) {
        return parse(List.of(rawArguments));
    }

    public static LaunchOptions parse(List<String> rawArguments) {
        Optional<Path> startupPdf = Optional.empty();
        Path watchDirectory = null;
        Path archiveDirectory = null;
        Path ghostscriptExecutable = null;

        for (int index = 0; index < rawArguments.size(); index++) {
            String argument = rawArguments.get(index);
            switch (argument) {
                case "--watch-print-jobs" -> watchDirectory = requirePathValue(rawArguments, ++index, argument);
                case "--archive-directory" -> archiveDirectory = requirePathValue(rawArguments, ++index, argument);
                case "--ghostscript" -> ghostscriptExecutable = requirePathValue(rawArguments, ++index, argument);
                default -> {
                    if (startupPdf.isEmpty() && argument.toLowerCase().endsWith(".pdf")) {
                        startupPdf = Optional.of(Paths.get(argument).toAbsolutePath().normalize());
                    }
                }
            }
        }

        Optional<PrintJobWatchConfig> watchConfig = Optional.empty();
        if (watchDirectory != null) {
            Path normalizedWatchDirectory = watchDirectory.toAbsolutePath().normalize();
            Path normalizedArchiveDirectory = archiveDirectory == null
                ? normalizedWatchDirectory.resolve("processed")
                : archiveDirectory.toAbsolutePath().normalize();

            watchConfig = Optional.of(new PrintJobWatchConfig(
                normalizedWatchDirectory,
                normalizedArchiveDirectory,
                Optional.ofNullable(ghostscriptExecutable).map(path -> path.toAbsolutePath().normalize())
            ));
        }

        return new LaunchOptions(startupPdf, watchConfig);
    }

    private static Path requirePathValue(List<String> rawArguments, int index, String flagName) {
        if (index >= rawArguments.size()) {
            throw new IllegalArgumentException("Missing value for " + flagName);
        }
        return Paths.get(rawArguments.get(index));
    }

    public record PrintJobWatchConfig(
        Path watchDirectory,
        Path archiveDirectory,
        Optional<Path> ghostscriptExecutable
    ) {
    }
}
