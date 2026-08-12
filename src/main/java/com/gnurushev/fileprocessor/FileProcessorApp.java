package com.gnurushev.fileprocessor;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Optional;
import java.util.concurrent.CompletionException;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import javafx.application.Application;
import javafx.application.Platform;
import javafx.scene.Scene;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Stage;

public final class FileProcessorApp extends Application {
    private final ExecutorService workerPool = Executors.newCachedThreadPool(new NamedThreadFactory());

    private Stage primaryStage;
    private AppConfig config;
    private AppShell shell;
    private SessionStore sessionStore;
    private AuthService authService;
    private DocumentService documentService;
    private PdfPreviewService pdfPreviewService;
    private SingleInstanceService singleInstanceService;

    private UserSession currentSession;
    private Path currentPdf;
    private Path queuedPdf;

    public static void main(String[] args) throws Exception {
        LaunchOptions launchOptions = LaunchOptions.parse(args);
        if (launchOptions.printJobWatchConfig().isPresent()) {
            PrintJobInboxWatcher.run(launchOptions.printJobWatchConfig().orElseThrow(), AppConfig.load());
            return;
        }
        launch(args);
    }

    @Override
    public void start(Stage stage) throws IOException {
        config = AppConfig.load();
        sessionStore = new PreferencesSessionStore();
        authService = new MockAuthService(config);
        documentService = new MockDocumentService(config);
        pdfPreviewService = new PdfPreviewService(config);
        primaryStage = stage;

        Optional<Path> startupPdf = LaunchOptions.parse(getParameters().getRaw()).startupPdf();
        singleInstanceService = new SingleInstanceService(config.singleInstancePort());
        if (singleInstanceService.forwardToRunningInstance(startupPdf.orElse(null))) {
            Platform.exit();
            return;
        }

        shell = new AppShell(
            config.applicationTitle(),
            this::handleLogin,
            this::lookupPatientDocuments,
            this::saveDraft,
            this::discardWorkspace,
            this::logout,
            this::promptForPatientId
        );

        Scene scene = new Scene(shell.root(), 1500, 920);
        scene.getStylesheets().add(getClass().getResource("/com/gnurushev/fileprocessor/app.css").toExternalForm());

        stage.setTitle(config.applicationTitle());
        stage.setMinWidth(1200);
        stage.setMinHeight(760);
        stage.setScene(scene);

        currentSession = sessionStore.load().orElse(null);
        shell.setSession(currentSession);
        if (currentSession == null) {
            shell.requireLogin(true);
            shell.setStatus("Sign in to load PDF files and fetch patient documents.");
        } else {
            shell.requireLogin(false);
            shell.setStatus("Ready. Open a PDF with Patient Document Manager to begin.");
        }

        singleInstanceService.start(message -> Platform.runLater(() -> handleInstanceMessage(message)));

        stage.show();
        bringToFront();

        startupPdf.ifPresent(this::receivePdfFromWindows);
    }

    @Override
    public void stop() throws Exception {
        try {
            if (singleInstanceService != null) {
                singleInstanceService.close();
            }
        } finally {
            workerPool.shutdownNow();
        }
        super.stop();
    }

    private void handleLogin(String username, String password) {
        shell.setLoginBusy(true);
        shell.setLoginMessage(null);

        CompletionStage<UserSession> login = authService.login(new LoginCredentials(username, password));
        login.whenComplete((session, error) -> Platform.runLater(() -> {
            shell.setLoginBusy(false);
            if (error != null) {
                shell.setLoginMessage(unwrap(error).getMessage());
                shell.setStatus("Unable to sign in.");
                return;
            }

            currentSession = session;
            sessionStore.save(session);
            shell.setSession(session);
            shell.requireLogin(false);
            shell.setLoginMessage(null);
            shell.setStatus("Signed in as %s.".formatted(session.username()));

            if (queuedPdf != null) {
                loadPdfIntoWorkspace(queuedPdf);
            }
        }));
    }

    private void lookupPatientDocuments(String patientId) {
        if (currentPdf == null) {
            shell.setStatus("Open a PDF before fetching patient documents.");
            return;
        }
        if (patientId == null || patientId.isBlank()) {
            shell.setStatus("Enter a patient ID.");
            return;
        }

        shell.setDocumentLookupBusy(true);
        shell.setStatus("Loading documents for patient %s...".formatted(patientId));

        documentService.fetchDocuments(patientId.strip(), currentPdf)
            .whenComplete((documents, error) -> Platform.runLater(() -> {
                shell.setDocumentLookupBusy(false);
                if (error != null) {
                    shell.setStatus("Document lookup failed: %s".formatted(unwrap(error).getMessage()));
                    return;
                }

                shell.setDocuments(documents);
                shell.setPendingStatus("Pending status: %d documents staged".formatted(documents.size()));
                shell.setApprovedStatus("Approved status: Mocked response loaded");
                shell.setStatus("Loaded %d related documents for patient %s.".formatted(documents.size(), patientId));
            }));
    }

    private void saveDraft() {
        shell.setPendingStatus("Pending status: Draft saved locally");
        shell.setStatus("Draft saved for %s.".formatted(currentPdf == null ? "the current workflow" : currentPdf.getFileName()));
    }

    private void discardWorkspace() {
        currentPdf = null;
        queuedPdf = null;
        shell.clearWorkspace();
        shell.setStatus("Cleared the current PDF and patient document workflow.");
    }

    private void logout() {
        sessionStore.clear();
        currentSession = null;
        shell.setSession(null);
        shell.requireLogin(true);
        if (currentPdf != null) {
            queuedPdf = currentPdf;
        }
        shell.setStatus("Signed out. Sign in to continue.");
    }

    private void promptForPatientId() {
        if (currentPdf == null) {
            shell.setStatus("Open a PDF before entering a patient ID.");
            return;
        }

        TextInputDialog dialog = new TextInputDialog(shell.patientId());
        dialog.initOwner(primaryStage);
        dialog.setTitle(config.applicationTitle());
        dialog.setHeaderText("Enter the patient ID for %s".formatted(currentPdf.getFileName()));
        dialog.setContentText("Patient ID:");
        dialog.showAndWait()
            .map(String::strip)
            .filter(value -> !value.isEmpty())
            .ifPresent(patientId -> {
                shell.setPatientId(patientId);
                lookupPatientDocuments(patientId);
            });
    }

    private void handleInstanceMessage(SingleInstanceService.IncomingMessage message) {
        bringToFront();
        if (message.type() == SingleInstanceService.MessageType.OPEN_FILE && message.path() != null) {
            receivePdfFromWindows(message.path());
        }
    }

    private void receivePdfFromWindows(Path file) {
        queuedPdf = file.toAbsolutePath().normalize();
        shell.setSourceFile(queuedPdf);
        bringToFront();

        if (currentSession == null) {
            shell.requireLogin(true);
            shell.setStatus("PDF received. Sign in to load %s.".formatted(queuedPdf.getFileName()));
            return;
        }

        loadPdfIntoWorkspace(queuedPdf);
    }

    private void loadPdfIntoWorkspace(Path file) {
        shell.setPreviewBusy(true);
        shell.setStatus("Loading PDF preview for %s...".formatted(file.getFileName()));

        workerPool.submit(() -> {
            try {
                PdfPreview preview = pdfPreviewService.load(file);
                Platform.runLater(() -> {
                    currentPdf = file;
                    queuedPdf = file;
                    shell.setPreviewBusy(false);
                    shell.setPreview(preview);
                    shell.setSourceFile(file);
                    shell.setPendingStatus("Pending status: PDF loaded");
                    shell.setApprovedStatus("Approved status: Awaiting patient match");
                    shell.setStatus(
                        preview.truncated()
                            ? "Loaded %d preview pages out of %d total. Enter a patient ID to continue.".formatted(preview.pages().size(), preview.totalPages())
                            : "Loaded %d pages. Enter a patient ID to continue.".formatted(preview.totalPages())
                    );
                    if (shell.patientId().isBlank()) {
                        promptForPatientId();
                    }
                });
            } catch (IOException error) {
                Platform.runLater(() -> {
                    shell.setPreviewBusy(false);
                    shell.setStatus("Unable to load PDF: %s".formatted(error.getMessage()));
                });
            }
        });
    }

    private void bringToFront() {
        primaryStage.setIconified(false);
        primaryStage.show();
        primaryStage.toFront();
        primaryStage.requestFocus();
    }

    private static Throwable unwrap(Throwable error) {
        Throwable current = error;
        while (current instanceof CompletionException && current.getCause() != null) {
            current = current.getCause();
        }
        return current;
    }

    private static final class NamedThreadFactory implements ThreadFactory {
        private int threadIndex = 1;

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "file-processor-worker-" + threadIndex++);
            thread.setDaemon(true);
            return thread;
        }
    }
}
