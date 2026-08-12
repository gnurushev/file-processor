package com.gnurushev.fileprocessor;

import java.time.Duration;

public record AppConfig(
    String applicationTitle,
    int singleInstancePort,
    int previewDpi,
    Duration mockLatency
) {
    public static AppConfig load() {
        return new AppConfig(
            System.getProperty("fileprocessor.appTitle", "Patient Document Manager"),
            Integer.getInteger("fileprocessor.singleInstancePort", 44567),
            Integer.getInteger("fileprocessor.previewDpi", 120),
            Duration.ofMillis(Long.getLong("fileprocessor.mockLatencyMs", 350L))
        );
    }
}
