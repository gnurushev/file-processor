package com.gnurushev.fileprocessor;

import java.nio.file.Path;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.DatePicker;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.PasswordField;
import javafx.scene.control.ProgressIndicator;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.control.Spinner;
import javafx.scene.control.TextField;
import javafx.scene.image.ImageView;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.ColumnConstraints;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;

public final class AppShell {
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("M/d/yyyy");

    private final StackPane root = new StackPane();
    private final BorderPane contentPane = new BorderPane();
    private final VBox loginOverlay = new VBox(14);

    private final Label sessionLabel = new Label("Not signed in");
    private final Label statusLabel = new Label();
    private final Label fileLabel = new Label("No PDF loaded");
    private final Label pendingStatusLabel = new Label("Pending status: Waiting for file");
    private final Label approvedStatusLabel = new Label("Approved status: Waiting for patient lookup");
    private final Label loginMessageLabel = new Label();

    private final TextField patientIdField = new TextField();
    private final TextField templateField = new TextField("CT Order Intake");
    private final TextField accountField = new TextField();
    private final TextField usernameField = new TextField();
    private final PasswordField passwordField = new PasswordField();

    private final ComboBox<String> authoredByCombo = new ComboBox<>(FXCollections.observableArrayList("Tim Nurushev, PhD", "Amy Patel, RN", "Sandra Lewis, RN"));
    private final ComboBox<String> supervisedByCombo = new ComboBox<>(FXCollections.observableArrayList("Krisha Howell, MD", "Daniel Kim, MD", "Ashley Rivers, MD"));
    private final ComboBox<String> documentTypeCombo = new ComboBox<>(FXCollections.observableArrayList("CT Order", "Authorization", "Lab Result", "Diagnostic Summary"));
    private final Spinner<Integer> hourSpinner = new Spinner<>(1, 12, 5);
    private final Spinner<Integer> minuteSpinner = new Spinner<>(0, 59, 42);
    private final DatePicker serviceDatePicker = new DatePicker(java.time.LocalDate.now());

    private final Button loginButton = new Button("Sign In");
    private final Button lookupButton = new Button("Fetch Patient Docs");
    private final Button promptPatientButton = new Button("Prompt for Patient");
    private final Button saveDraftButton = new Button("Save as Draft");
    private final Button discardButton = new Button("Discard");
    private final Button logoutButton = new Button("Sign Out");
    private final Button zoomInButton = new Button("+");
    private final Button zoomOutButton = new Button("-");

    private final ProgressIndicator previewProgress = new ProgressIndicator();
    private final ProgressIndicator lookupProgress = new ProgressIndicator();

    private final ListView<PagePreview> thumbnailList = new ListView<>();
    private final ListView<DocumentSummary> documentList = new ListView<>();
    private final ImageView previewImageView = new ImageView();
    private final Label previewPlaceholderLabel = new Label("Open a PDF to start the workflow.");

    private double zoomFactor = 1.0;

    public AppShell(
        String applicationTitle,
        BiConsumer<String, String> loginHandler,
        Consumer<String> patientLookupHandler,
        Runnable saveDraftHandler,
        Runnable discardHandler,
        Runnable logoutHandler,
        Runnable promptPatientHandler
    ) {
        contentPane.getStyleClass().add("app-shell");
        root.getChildren().addAll(contentPane, loginOverlay);

        contentPane.setTop(buildTopRegion(applicationTitle, loginHandler, patientLookupHandler, saveDraftHandler, discardHandler, logoutHandler, promptPatientHandler));
        contentPane.setCenter(buildCenterRegion());
        contentPane.setBottom(buildStatusRegion());

        buildLoginOverlay(loginHandler);
        configurePreviewArea();
        configureDocumentList();
        configureActions(patientLookupHandler, saveDraftHandler, discardHandler, logoutHandler, promptPatientHandler);
        setStatus("Ready.");
    }

    public Parent root() {
        return root;
    }

    public void setSession(UserSession session) {
        if (session == null) {
            sessionLabel.setText("Not signed in");
            logoutButton.setDisable(true);
            return;
        }

        sessionLabel.setText("Signed in as %s".formatted(session.username()));
        logoutButton.setDisable(false);
    }

    public void requireLogin(boolean required) {
        loginOverlay.setVisible(required);
        loginOverlay.setManaged(required);
    }

    public void setLoginBusy(boolean busy) {
        usernameField.setDisable(busy);
        passwordField.setDisable(busy);
        loginButton.setDisable(busy);
        loginButton.setText(busy ? "Signing In..." : "Sign In");
    }

    public void setLoginMessage(String message) {
        loginMessageLabel.setText(message == null ? "" : message);
        loginMessageLabel.setVisible(message != null && !message.isBlank());
    }

    public void setPreviewBusy(boolean busy) {
        previewProgress.setVisible(busy);
        previewProgress.setManaged(busy);
    }

    public void setDocumentLookupBusy(boolean busy) {
        lookupButton.setDisable(busy);
        promptPatientButton.setDisable(busy);
        lookupProgress.setVisible(busy);
        lookupProgress.setManaged(busy);
    }

    public void setStatus(String message) {
        statusLabel.setText(message);
    }

    public void setSourceFile(Path file) {
        fileLabel.setText(file == null ? "No PDF loaded" : file.toString());
    }

    public void setPatientId(String patientId) {
        patientIdField.setText(patientId);
    }

    public String patientId() {
        return patientIdField.getText() == null ? "" : patientIdField.getText().strip();
    }

    public void setPendingStatus(String value) {
        pendingStatusLabel.setText(value);
    }

    public void setApprovedStatus(String value) {
        approvedStatusLabel.setText(value);
    }

    public void setPreview(PdfPreview preview) {
        thumbnailList.getItems().setAll(preview.pages());
        if (!preview.pages().isEmpty()) {
            thumbnailList.getSelectionModel().selectFirst();
            previewPlaceholderLabel.setVisible(false);
        }
    }

    public void setDocuments(List<DocumentSummary> documents) {
        documentList.getItems().setAll(documents);
    }

    public void clearWorkspace() {
        thumbnailList.getItems().clear();
        documentList.getItems().clear();
        previewImageView.setImage(null);
        previewPlaceholderLabel.setVisible(true);
        patientIdField.clear();
        fileLabel.setText("No PDF loaded");
        pendingStatusLabel.setText("Pending status: Waiting for file");
        approvedStatusLabel.setText("Approved status: Waiting for patient lookup");
    }

    private Node buildTopRegion(
        String applicationTitle,
        BiConsumer<String, String> loginHandler,
        Consumer<String> patientLookupHandler,
        Runnable saveDraftHandler,
        Runnable discardHandler,
        Runnable logoutHandler,
        Runnable promptPatientHandler
    ) {
        VBox container = new VBox(12);
        container.setPadding(new Insets(18, 18, 12, 18));

        HBox titleBar = new HBox(16);
        titleBar.setAlignment(Pos.CENTER_LEFT);
        Label titleLabel = new Label(applicationTitle);
        titleLabel.setFont(Font.font("Segoe UI", FontWeight.BOLD, 26));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        titleBar.getChildren().addAll(titleLabel, spacer, sessionLabel, logoutButton);

        GridPane summaryGrid = new GridPane();
        summaryGrid.setHgap(14);
        summaryGrid.setVgap(10);
        summaryGrid.getColumnConstraints().addAll(
            createColumn(120),
            createGrowColumn(),
            createColumn(120),
            createGrowColumn(),
            createColumn(120),
            createGrowColumn()
        );

        authoredByCombo.getSelectionModel().selectFirst();
        supervisedByCombo.getSelectionModel().selectFirst();
        documentTypeCombo.getSelectionModel().selectFirst();
        templateField.setEditable(false);
        accountField.setPromptText("Account number");
        patientIdField.setPromptText("Enter patient ID");
        hourSpinner.setEditable(true);
        minuteSpinner.setEditable(true);
        minuteSpinner.getValueFactory().setWrapAround(true);

        VBox statusCards = new VBox(10, statusCard(pendingStatusLabel), statusCard(approvedStatusLabel));
        statusCards.setPrefWidth(200);

        HBox patientLookupRow = new HBox(8, patientIdField, lookupButton, promptPatientButton, lookupProgress);
        HBox.setHgrow(patientIdField, Priority.ALWAYS);
        lookupProgress.setPrefSize(20, 20);
        lookupProgress.setVisible(false);
        lookupProgress.setManaged(false);

        HBox actionButtons = new HBox(10, saveDraftButton, discardButton);

        HBox dateRow = new HBox(8, serviceDatePicker, hourSpinner, minuteSpinner);
        HBox timeSuffix = new HBox(new Label("PM"));
        timeSuffix.setAlignment(Pos.CENTER_LEFT);
        dateRow.getChildren().add(timeSuffix);

        summaryGrid.add(new Label("Patient ID:"), 0, 0);
        summaryGrid.add(patientLookupRow, 1, 0);
        summaryGrid.add(new Label("Template Name:"), 2, 0);
        summaryGrid.add(templateField, 3, 0);
        summaryGrid.add(new Label("Document Type:"), 4, 0);
        summaryGrid.add(documentTypeCombo, 5, 0);

        summaryGrid.add(new Label("Authored By:"), 0, 1);
        summaryGrid.add(authoredByCombo, 1, 1);
        summaryGrid.add(new Label("Date of Service:"), 2, 1);
        summaryGrid.add(dateRow, 3, 1);
        summaryGrid.add(actionButtons, 4, 1);
        summaryGrid.add(statusCards, 5, 0, 1, 3);

        summaryGrid.add(new Label("Supervised By:"), 0, 2);
        summaryGrid.add(supervisedByCombo, 1, 2);
        summaryGrid.add(new Label("Account No:"), 2, 2);
        summaryGrid.add(accountField, 3, 2);
        summaryGrid.add(new Label("Loaded File:"), 4, 2);
        summaryGrid.add(fileLabel, 5, 2);

        container.getChildren().addAll(titleBar, summaryGrid, new Separator());
        return container;
    }

    private Node buildCenterRegion() {
        BorderPane previewPane = new BorderPane();
        previewPane.getStyleClass().add("preview-pane");
        previewPane.setPadding(new Insets(0, 18, 18, 18));

        VBox thumbnailColumn = new VBox(10, sectionHeader("Document Builder"), thumbnailList);
        thumbnailColumn.setPrefWidth(170);
        thumbnailColumn.setMinWidth(150);

        previewPlaceholderLabel.getStyleClass().add("preview-placeholder");
        previewImageView.setPreserveRatio(true);
        previewImageView.setSmooth(true);
        previewImageView.setFitWidth(820);
        previewImageView.setFitHeight(1000);

        previewProgress.setPrefSize(52, 52);
        previewProgress.setVisible(false);
        previewProgress.setManaged(false);

        StackPane previewCanvas = new StackPane(previewImageView, previewPlaceholderLabel, previewProgress);
        previewCanvas.setPadding(new Insets(20));
        ScrollPane scrollPane = new ScrollPane(previewCanvas);
        scrollPane.setFitToWidth(true);
        scrollPane.setFitToHeight(true);
        scrollPane.getStyleClass().add("preview-scroll");

        VBox previewColumn = new VBox(10, sectionHeader("PDF Preview"), scrollPane);
        VBox.setVgrow(scrollPane, Priority.ALWAYS);

        VBox zoomPanel = new VBox(12, zoomInButton, zoomOutButton);
        zoomPanel.setAlignment(Pos.TOP_CENTER);
        zoomPanel.getStyleClass().add("zoom-panel");

        VBox documentsColumn = new VBox(10, sectionHeader("Related Documents"), documentList);
        documentsColumn.setPrefWidth(340);
        VBox.setVgrow(documentList, Priority.ALWAYS);

        BorderPane centerLayout = new BorderPane();
        centerLayout.setLeft(thumbnailColumn);
        centerLayout.setCenter(previewColumn);
        centerLayout.setRight(new VBox(12, zoomPanel, documentsColumn));
        BorderPane.setMargin(previewColumn, new Insets(0, 14, 0, 14));

        previewPane.setCenter(centerLayout);
        return previewPane;
    }

    private Node buildStatusRegion() {
        HBox statusBar = new HBox(12);
        statusBar.getStyleClass().add("status-bar");
        statusBar.setPadding(new Insets(10, 18, 14, 18));
        Label refreshLabel = new Label("Last mock data refresh: " + DATE_FORMAT.format(java.time.LocalDate.now()));
        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);
        statusBar.getChildren().addAll(statusLabel, spacer, refreshLabel);
        return statusBar;
    }

    private void buildLoginOverlay(BiConsumer<String, String> loginHandler) {
        loginOverlay.getStyleClass().add("login-overlay");
        loginOverlay.setAlignment(Pos.CENTER);
        loginOverlay.setPadding(new Insets(32));
        loginOverlay.setVisible(false);
        loginOverlay.setManaged(false);

        VBox card = new VBox(12);
        card.getStyleClass().add("login-card");
        card.setMaxWidth(360);
        card.setPadding(new Insets(24));

        Label heading = new Label("Sign in to continue");
        heading.setFont(Font.font("Segoe UI", FontWeight.BOLD, 22));
        Label copy = new Label("File Processor keeps the selected PDF queued until you sign in.");
        copy.setWrapText(true);

        usernameField.setPromptText("Username");
        passwordField.setPromptText("Password");
        loginMessageLabel.getStyleClass().add("login-message");
        loginMessageLabel.setVisible(false);

        loginButton.setMaxWidth(Double.MAX_VALUE);
        loginButton.setOnAction(event -> loginHandler.accept(usernameField.getText(), passwordField.getText()));
        passwordField.setOnAction(event -> loginHandler.accept(usernameField.getText(), passwordField.getText()));

        card.getChildren().addAll(heading, copy, usernameField, passwordField, loginMessageLabel, loginButton);
        loginOverlay.getChildren().add(card);
    }

    private void configurePreviewArea() {
        thumbnailList.setCellFactory(list -> new ListCell<>() {
            private final ImageView thumbnailView = new ImageView();
            private final Label label = new Label();
            private final VBox content = new VBox(6, thumbnailView, label);

            {
                thumbnailView.setPreserveRatio(true);
                thumbnailView.setFitWidth(110);
                thumbnailView.setFitHeight(140);
                content.setPadding(new Insets(8));
                content.setAlignment(Pos.CENTER);
            }

            @Override
            protected void updateItem(PagePreview item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setGraphic(null);
                    return;
                }
                thumbnailView.setImage(item.image());
                label.setText("Page " + item.pageNumber());
                setGraphic(content);
            }
        });

        thumbnailList.getSelectionModel().selectedItemProperty().addListener((ignored, oldValue, selected) -> {
            if (selected == null) {
                previewImageView.setImage(null);
                previewPlaceholderLabel.setVisible(true);
                return;
            }

            previewImageView.setImage(selected.image());
            previewImageView.setScaleX(zoomFactor);
            previewImageView.setScaleY(zoomFactor);
            previewPlaceholderLabel.setVisible(false);
        });
    }

    private void configureDocumentList() {
        documentList.setCellFactory(list -> new ListCell<>() {
            @Override
            protected void updateItem(DocumentSummary item, boolean empty) {
                super.updateItem(item, empty);
                if (empty || item == null) {
                    setText(null);
                    setGraphic(null);
                    return;
                }

                Label title = new Label(item.title());
                title.setFont(Font.font("Segoe UI", FontWeight.SEMI_BOLD, 14));
                Label metadata = new Label("%s | %s | %s".formatted(item.type(), item.status(), item.author()));
                Label date = new Label("Service date: " + DATE_FORMAT.format(item.dateOfService()));
                VBox box = new VBox(4, title, metadata, date);
                box.setPadding(new Insets(8));
                setGraphic(box);
            }
        });
    }

    private void configureActions(
        Consumer<String> patientLookupHandler,
        Runnable saveDraftHandler,
        Runnable discardHandler,
        Runnable logoutHandler,
        Runnable promptPatientHandler
    ) {
        lookupButton.setOnAction(event -> patientLookupHandler.accept(patientId()));
        patientIdField.setOnAction(event -> patientLookupHandler.accept(patientId()));
        promptPatientButton.setOnAction(event -> promptPatientHandler.run());
        saveDraftButton.setOnAction(event -> saveDraftHandler.run());
        discardButton.setOnAction(event -> discardHandler.run());
        logoutButton.setOnAction(event -> logoutHandler.run());
        zoomInButton.setOnAction(event -> updateZoom(0.15));
        zoomOutButton.setOnAction(event -> updateZoom(-0.15));
    }

    private void updateZoom(double delta) {
        zoomFactor = Math.max(0.5, Math.min(2.25, zoomFactor + delta));
        previewImageView.setScaleX(zoomFactor);
        previewImageView.setScaleY(zoomFactor);
    }

    private static ColumnConstraints createColumn(double width) {
        ColumnConstraints column = new ColumnConstraints();
        column.setMinWidth(width);
        return column;
    }

    private static ColumnConstraints createGrowColumn() {
        ColumnConstraints column = new ColumnConstraints();
        column.setHgrow(Priority.ALWAYS);
        return column;
    }

    private static Node sectionHeader(String title) {
        Label label = new Label(title);
        label.setFont(Font.font("Segoe UI", FontWeight.BOLD, 16));
        return label;
    }

    private static VBox statusCard(Label contentLabel) {
        VBox card = new VBox(contentLabel);
        card.getStyleClass().add("status-card");
        card.setPadding(new Insets(10));
        return card;
    }
}
