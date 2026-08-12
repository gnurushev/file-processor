package com.gnurushev.fileprocessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Path;
import org.junit.jupiter.api.Test;

class LaunchOptionsTest {
    @Test
    void parsesStartupPdfArguments() {
        LaunchOptions options = LaunchOptions.parse(new String[] {"C:\\temp\\example.pdf"});

        assertEquals(Path.of("C:\\temp\\example.pdf").toAbsolutePath().normalize(), options.startupPdf().orElseThrow());
        assertTrue(options.printJobWatchConfig().isEmpty());
    }

    @Test
    void parsesPrintWatchArgumentsWithDefaults() {
        LaunchOptions options = LaunchOptions.parse(new String[] {
            "--watch-print-jobs", "C:\\spool\\incoming",
            "--ghostscript", "C:\\Ghostscript\\bin\\gswin64c.exe"
        });

        LaunchOptions.PrintJobWatchConfig watchConfig = options.printJobWatchConfig().orElseThrow();
        assertEquals(Path.of("C:\\spool\\incoming").toAbsolutePath().normalize(), watchConfig.watchDirectory());
        assertEquals(Path.of("C:\\spool\\incoming").toAbsolutePath().normalize().resolve("processed"), watchConfig.archiveDirectory());
        assertEquals(Path.of("C:\\Ghostscript\\bin\\gswin64c.exe").toAbsolutePath().normalize(), watchConfig.ghostscriptExecutable().orElseThrow());
    }
}
