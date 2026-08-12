package com.gnurushev.fileprocessor;

import java.nio.file.Path;
import java.util.List;
import java.util.concurrent.CompletionStage;

public interface DocumentService {
    CompletionStage<List<DocumentSummary>> fetchDocuments(String patientId, Path sourcePdf);
}

