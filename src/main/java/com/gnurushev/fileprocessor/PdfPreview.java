package com.gnurushev.fileprocessor;

import java.nio.file.Path;
import java.util.List;

public record PdfPreview(
    Path sourceFile,
    int totalPages,
    boolean truncated,
    List<PagePreview> pages
) {
}

