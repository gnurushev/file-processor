package com.gnurushev.fileprocessor;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Paths;
import java.time.Duration;
import java.util.List;
import java.util.concurrent.CompletionException;
import org.junit.jupiter.api.Test;

class MockDocumentServiceTest {
    @Test
    void returnsDocumentsForPatientAndPdf() {
        MockDocumentService service = new MockDocumentService(new AppConfig("Test", 44567, 96, Duration.ZERO));

        List<DocumentSummary> documents = service.fetchDocuments("P-1204", Paths.get("sample.pdf"))
            .toCompletableFuture()
            .join();

        assertEquals(4, documents.size());
        assertTrue(documents.getFirst().title().contains("sample.pdf"));
        assertFalse(documents.getFirst().documentId().isBlank());
    }

    @Test
    void rejectsMissingPatientIds() {
        MockDocumentService service = new MockDocumentService(new AppConfig("Test", 44567, 96, Duration.ZERO));

        CompletionException error = assertThrows(
            CompletionException.class,
            () -> service.fetchDocuments("   ", Paths.get("sample.pdf")).toCompletableFuture().join()
        );

        assertEquals("Patient ID is required.", error.getCause().getMessage());
    }
}

