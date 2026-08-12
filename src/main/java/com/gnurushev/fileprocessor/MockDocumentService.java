package com.gnurushev.fileprocessor;

import java.nio.file.Path;
import java.time.LocalDate;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;

public final class MockDocumentService implements DocumentService {
    private final AppConfig config;

    public MockDocumentService(AppConfig config) {
        this.config = config;
    }

    @Override
    public CompletionStage<List<DocumentSummary>> fetchDocuments(String patientId, Path sourcePdf) {
        return CompletableFuture.supplyAsync(() -> {
            pause();

            String normalizedPatientId = patientId == null ? "" : patientId.strip();
            if (normalizedPatientId.isEmpty()) {
                throw new IllegalArgumentException("Patient ID is required.");
            }

            String fileName = sourcePdf == null ? "No PDF loaded" : sourcePdf.getFileName().toString();
            LocalDate today = LocalDate.now();

            return List.of(
                new DocumentSummary(normalizedPatientId + "-CT-1", "Primary intake for " + fileName, "CT Order", "Ready for review", "Tim Nurushev, PhD", today.minusDays(1)),
                new DocumentSummary(normalizedPatientId + "-LAB-2", "Lab follow-up", "Lab Result", "Pending upload", "Krisha Howell, MD", today.minusDays(3)),
                new DocumentSummary(normalizedPatientId + "-AUTH-3", "Authorization note", "Authorization", "Approved", "Nurse Coordinator", today.minusDays(7)),
                new DocumentSummary(normalizedPatientId + "-SCAN-4", "Historical scan summary", "Diagnostic Summary", "Draft", "Radiology Intake", today.minusDays(14))
            );
        });
    }

    private void pause() {
        try {
            Thread.sleep(config.mockLatency().toMillis());
        } catch (InterruptedException error) {
            Thread.currentThread().interrupt();
            throw new IllegalStateException("Document lookup was interrupted.", error);
        }
    }
}

