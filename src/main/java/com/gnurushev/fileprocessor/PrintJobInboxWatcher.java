package com.gnurushev.fileprocessor;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.nio.file.StandardOpenOption;
import java.nio.file.WatchEvent;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import static java.nio.file.StandardWatchEventKinds.ENTRY_CREATE;
import static java.nio.file.StandardWatchEventKinds.ENTRY_MODIFY;

public final class PrintJobInboxWatcher {
    private static final int MAX_STABILITY_ATTEMPTS = 20;
    private static final long STABILITY_WAIT_MILLIS = 350L;
    private static final DateTimeFormatter FILE_TIMESTAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS")
        .withZone(java.time.ZoneId.systemDefault());

    private final LaunchOptions.PrintJobWatchConfig config;
    private final SingleInstanceService singleInstanceService;

    private PrintJobInboxWatcher(LaunchOptions.PrintJobWatchConfig config, AppConfig appConfig) {
        this.config = config;
        this.singleInstanceService = new SingleInstanceService(appConfig.singleInstancePort());
    }

    public static void run(LaunchOptions.PrintJobWatchConfig config, AppConfig appConfig) throws Exception {
        new PrintJobInboxWatcher(config, appConfig).watchLoop();
    }

    private void watchLoop() throws Exception {
        Files.createDirectories(config.watchDirectory());
        Files.createDirectories(config.archiveDirectory());
        Files.createDirectories(originalArchiveDirectory());

        processExistingJobs();

        try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
            config.watchDirectory().register(watchService, ENTRY_CREATE, ENTRY_MODIFY);
            while (true) {
                WatchKey watchKey = watchService.take();
                for (WatchEvent<?> event : watchKey.pollEvents()) {
                    Object context = event.context();
                    if (context instanceof Path relativePath) {
                        processCandidate(config.watchDirectory().resolve(relativePath));
                    }
                }
                watchKey.reset();
            }
        }
    }

    private void processExistingJobs() throws Exception {
        try (var stream = Files.list(config.watchDirectory())) {
            stream
                .filter(Files::isRegularFile)
                .forEach(path -> {
                    try {
                        processCandidate(path);
                    } catch (Exception error) {
                        System.err.println("Unable to process queued print job " + path + ": " + error.getMessage());
                    }
                });
        }
    }

    private void processCandidate(Path candidate) throws Exception {
        if (!Files.isRegularFile(candidate)) {
            return;
        }

        String fileName = candidate.getFileName().toString().toLowerCase();
        if (fileName.endsWith(".pdf")) {
            Path stagedPdf = claimStableFile(candidate, config.archiveDirectory(), ".pdf");
            handoffPdf(stagedPdf);
            return;
        }

        if (fileName.endsWith(".ps")) {
            Path archivedSource = claimStableFile(candidate, originalArchiveDirectory(), ".ps");
            Path stagedPdf = convertPostScriptToPdf(archivedSource);
            handoffPdf(stagedPdf);
        }
    }

    private Path claimStableFile(Path source, Path destinationDirectory, String extension) throws Exception {
        awaitStableFile(source);
        Files.createDirectories(destinationDirectory);
        Path destination = buildArchivedPath(destinationDirectory, source.getFileName().toString(), extension);
        return Files.move(source, destination, StandardCopyOption.REPLACE_EXISTING);
    }

    private void awaitStableFile(Path source) throws Exception {
        long previousSize = -1L;
        for (int attempt = 0; attempt < MAX_STABILITY_ATTEMPTS; attempt++) {
            if (!Files.exists(source) || !Files.isRegularFile(source)) {
                throw new IOException("Print job disappeared before it could be processed: " + source);
            }

            long currentSize = Files.size(source);
            try (InputStream ignored = Files.newInputStream(source, StandardOpenOption.READ)) {
                if (currentSize > 0 && currentSize == previousSize) {
                    return;
                }
            } catch (IOException error) {
                if (attempt == MAX_STABILITY_ATTEMPTS - 1) {
                    throw error;
                }
            }

            previousSize = currentSize;
            Thread.sleep(STABILITY_WAIT_MILLIS);
        }

        throw new IOException("Timed out waiting for the print job to finish writing: " + source);
    }

    private Path convertPostScriptToPdf(Path archivedSource) throws Exception {
        Optional<Path> ghostscriptExecutable = config.ghostscriptExecutable();
        if (ghostscriptExecutable.isEmpty()) {
            throw new IllegalStateException("A Ghostscript executable is required to convert PostScript print jobs.");
        }

        String baseName = stripExtension(archivedSource.getFileName().toString());
        Path pdfPath = buildArchivedPath(config.archiveDirectory(), baseName, ".pdf");

        List<String> command = new ArrayList<>();
        command.add(ghostscriptExecutable.orElseThrow().toString());
        command.add("-dSAFER");
        command.add("-dBATCH");
        command.add("-dNOPAUSE");
        command.add("-sDEVICE=pdfwrite");
        command.add("-dCompatibilityLevel=1.7");
        command.add("-sOutputFile=" + pdfPath);
        command.add(archivedSource.toString());

        Process process = new ProcessBuilder(command)
            .redirectErrorStream(true)
            .start();

        String output;
        try (InputStream stream = process.getInputStream(); ByteArrayOutputStream buffer = new ByteArrayOutputStream()) {
            stream.transferTo(buffer);
            output = buffer.toString();
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Ghostscript conversion failed for " + archivedSource + ": " + output.strip());
        }

        return pdfPath;
    }

    private void handoffPdf(Path pdfPath) throws IOException {
        if (singleInstanceService.forwardToRunningInstance(pdfPath)) {
            return;
        }

        Optional<String> currentCommand = ProcessHandle.current().info().command();
        if (currentCommand.isEmpty()) {
            throw new IOException("Unable to determine the current launcher path for PDF handoff.");
        }

        new ProcessBuilder(currentCommand.orElseThrow(), pdfPath.toString()).start();
    }

    private Path originalArchiveDirectory() {
        return config.archiveDirectory().resolve("originals");
    }

    private Path buildArchivedPath(Path directory, String fileName, String extension) {
        String baseName = stripExtension(fileName);
        String timestamp = FILE_TIMESTAMP.format(Instant.now());
        return directory.resolve(baseName + "-" + timestamp + extension);
    }

    private String stripExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(0, lastDot) : fileName;
    }
}
