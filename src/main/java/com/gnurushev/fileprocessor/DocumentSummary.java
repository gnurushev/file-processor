package com.gnurushev.fileprocessor;

import java.time.LocalDate;

public record DocumentSummary(
    String documentId,
    String title,
    String type,
    String status,
    String author,
    LocalDate dateOfService
) {
}

