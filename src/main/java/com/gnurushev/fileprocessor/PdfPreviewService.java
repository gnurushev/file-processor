package com.gnurushev.fileprocessor;

import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import javafx.embed.swing.SwingFXUtils;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.rendering.ImageType;
import org.apache.pdfbox.rendering.PDFRenderer;

public final class PdfPreviewService {
    private static final int MAX_RENDERED_PAGES = 12;

    private final AppConfig config;

    public PdfPreviewService(AppConfig config) {
        this.config = config;
    }

    public PdfPreview load(Path file) throws IOException {
        Path normalized = file.toAbsolutePath().normalize();
        if (!Files.exists(normalized)) {
            throw new IOException("The selected file does not exist.");
        }
        if (!Files.isRegularFile(normalized)) {
            throw new IOException("The selected path is not a file.");
        }

        try (PDDocument document = Loader.loadPDF(normalized.toFile())) {
            PDFRenderer renderer = new PDFRenderer(document);
            int totalPages = document.getNumberOfPages();
            int renderedPages = Math.min(totalPages, MAX_RENDERED_PAGES);
            List<PagePreview> previews = new ArrayList<>(renderedPages);

            for (int pageIndex = 0; pageIndex < renderedPages; pageIndex++) {
                BufferedImage image = renderer.renderImageWithDPI(pageIndex, config.previewDpi(), ImageType.RGB);
                previews.add(new PagePreview(pageIndex + 1, SwingFXUtils.toFXImage(image, null)));
            }

            return new PdfPreview(normalized, totalPages, renderedPages < totalPages, previews);
        }
    }
}

