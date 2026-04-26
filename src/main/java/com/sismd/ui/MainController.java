package com.sismd.ui;

import com.sismd.model.GenerationRecord;
import com.sismd.model.ImageData;
import com.sismd.model.ImageMetadata;
import com.sismd.monitor.performance.JmxPerformanceAdapter;
import com.sismd.monitor.performance.PerformanceMonitor;
import com.sismd.monitor.performance.PerformanceSnapshot;
import com.sismd.monitor.system.DefaultSystemInfoAdapter;
import com.sismd.monitor.system.SystemInfoService;
import com.sismd.monitor.system.SystemInfoSnapshot;
import com.sismd.repository.CsvExporter;
import com.sismd.repository.FileGenerationRepository;
import com.sismd.repository.GenerationRepository;
import com.sismd.service.ImageIOService;
import com.sismd.service.ImageMetadataService;
import com.sismd.service.ImageProcessingService;
import com.sismd.service.impl.DefaultImageIOService;
import com.sismd.service.impl.DefaultImageMetadataService;
import com.sismd.service.impl.CompletableFutureImageProcessingService;
import com.sismd.service.impl.ForkJoinImageProcessingService;
import com.sismd.service.impl.ManualThreadImageProcessingService;
import com.sismd.service.impl.SequentialImageProcessingService;
import com.sismd.service.impl.ThreadPoolImageProcessingService;
import javafx.animation.ScaleTransition;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ComboBox;
import javafx.scene.control.Label;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.Separator;
import javafx.scene.image.ImageView;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.stage.FileChooser;
import javafx.util.Duration;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.awt.Desktop;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public class MainController {

    // ── service wiring ────────────────────────────────────────────────────────────
    private final ImageIOService       ioService         = new DefaultImageIOService();
    private final ImageMetadataService metadataService   = new DefaultImageMetadataService(ioService);
    private final PerformanceMonitor   monitor           = new JmxPerformanceAdapter();
    private final SystemInfoService    systemInfoService = new DefaultSystemInfoAdapter();
    private final CsvExporter          csvExporter       = new CsvExporter();

    // swapped via the algorithm dropdown
    private ImageProcessingService processingService;

    private final LinkedHashMap<String, ImageProcessingService> implementations = new LinkedHashMap<>();

    private GenerationRepository genRepo;

    // ── history row styles ────────────────────────────────────────────────────────
    private static final String HIST_ROW_BASE =
            "-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 6; " +
            "-fx-background-radius: 6; -fx-padding: 6 8; -fx-cursor: hand;";
    private static final String HIST_ROW_HOVER =
            "-fx-background-color: #f1f5f9; -fx-border-color: #cbd5e1; -fx-border-radius: 6; " +
            "-fx-background-radius: 6; -fx-padding: 6 8; -fx-cursor: hand;";
    private static final String HIST_ROW_ACTIVE =
            "-fx-background-color: #faf5ff; -fx-border-color: #7c3aed; -fx-border-radius: 6; " +
            "-fx-background-radius: 6; -fx-padding: 6 8; -fx-cursor: hand;";

    // ── preview pane styles ───────────────────────────────────────────────────────
    private static final String PANE_BASE =
            "-fx-background-color: #f8fafc; -fx-border-color: #e2e8f0; -fx-border-radius: 6; " +
            "-fx-background-radius: 6; -fx-min-height: 190; -fx-max-height: 190;";
    private static final String INPUT_PANE_HOVER =
            "-fx-background-color: #eff6ff; -fx-border-color: #93c5fd; -fx-border-radius: 6; " +
            "-fx-background-radius: 6; -fx-min-height: 190; -fx-max-height: 190;";
    private static final String OUTPUT_PANE_HOVER =
            "-fx-background-color: #f0fdf4; -fx-border-color: #86efac; -fx-border-radius: 6; " +
            "-fx-background-radius: 6; -fx-min-height: 190; -fx-max-height: 190;";

    // ── FXML bindings ─────────────────────────────────────────────────────────────
    @FXML private StackPane inputPreviewPane;
    @FXML private StackPane outputPreviewPane;
    @FXML private ImageView inputImageView;
    @FXML private ImageView outputImageView;
    @FXML private Label     inputPlaceholder;
    @FXML private Label     outputPlaceholder;

    @FXML private Label lblInputFile;
    @FXML private Label lblInputFormat;
    @FXML private Label lblInputSize;
    @FXML private Label lblInputDimensions;
    @FXML private Label lblInputPixels;

    @FXML private Label lblOutputFile;
    @FXML private Label lblOutputFormat;
    @FXML private Label lblOutputSize;
    @FXML private Label lblOutputDimensions;
    @FXML private Label lblOutputPixels;

    @FXML private VBox   systemInfoBox;
    @FXML private VBox   wallTimeCard;
    @FXML private Label  lblWallTime;
    @FXML private VBox   metricsBox;
    @FXML private VBox   historyBox;
    @FXML private ComboBox<String> algorithmCombo;
    @FXML private Label  lblStatus;
    @FXML private Button btnChoose;
    @FXML private Button btnProcess;
    @FXML private Button btnSave;
    @FXML private Button btnOpenInput;
    @FXML private Button btnOpenOutput;
    @FXML private Button btnReset;
    @FXML private Button btnExportCsv;
    @FXML private Button btnDeleteAll;
    @FXML private Button btnDeleteSelected;

    // ── state ─────────────────────────────────────────────────────────────────────
    private File   selectedFile;
    private File   outputFile;
    private String sessionUuid;
    private Path   uploadsBase;
    private ImageData processedOutput;
    private String loadedHistoryUuid;

    // ── lifecycle ─────────────────────────────────────────────────────────────────

    @FXML
    private void initialize() {
        uploadsBase = Path.of(System.getProperty("user.dir"), "uploads");
        genRepo = new FileGenerationRepository(uploadsBase);

        // build the implementation registry — add new strategies here as they land
        int cores = Runtime.getRuntime().availableProcessors();
        implementations.put("Sequential",                        new SequentialImageProcessingService());
        implementations.put("Manual Threads (" + cores + ")",   new ManualThreadImageProcessingService());
        implementations.put("Thread Pool (" + cores + ")",      new ThreadPoolImageProcessingService());
        implementations.put("Fork / Join",                       new ForkJoinImageProcessingService());
        implementations.put("CompletableFuture (" + cores + ")", new CompletableFutureImageProcessingService());

        algorithmCombo.getItems().addAll(implementations.keySet());
        algorithmCombo.getSelectionModel().selectFirst();
        processingService = implementations.get(algorithmCombo.getValue());
        algorithmCombo.valueProperty().addListener(
                (obs, old, val) -> processingService = implementations.get(val));

        SystemInfoSnapshot info = systemInfoService.read();
        populateInfoBox(systemInfoBox, info.asMap());
        setupInputPreviewPane();
        setupOutputPreviewPane();
        setupButtonHover(btnChoose, btnProcess, btnSave, btnOpenInput, btnOpenOutput,
                         btnReset, btnExportCsv, btnDeleteAll, btnDeleteSelected);

        loadHistoryFromRepo();
    }

    // ── handlers ──────────────────────────────────────────────────────────────────

    @FXML
    private void handleChooseFile() {
        File file = openChooser("Select Image",
                new FileChooser.ExtensionFilter("Images", "*.jpg", "*.jpeg", "*.png", "*.bmp", "*.gif"));
        if (file == null) return;

        selectedFile = file;

        populateInputLabels(metadataService.read(file));
        inputImageView.setImage(new javafx.scene.image.Image(file.toURI().toString()));
        inputPlaceholder.setVisible(false);
        btnProcess.setDisable(false);
        btnOpenInput.setDisable(false);
        resetOutput();
    }

    @FXML
    private void handleProcess() {
        if (selectedFile == null) return;

        sessionUuid = UUID.randomUUID().toString();
        btnProcess.setDisable(true);
        btnSave.setDisable(true);
        setStatus("Processing…", "#ea580c");

        final File source    = selectedFile;
        final String uuid    = sessionUuid;

        final String algo = algorithmCombo.getValue();

        Task<ProcessingResult> task = new Task<>() {
            @Override
            protected ProcessingResult call() {
                // copy original to uploads/input — outside the monitored window
                File inputCopy = copyToUploadsInput(source, uuid);

                // load from the stored copy, then measure only the algorithm
                ImageData input = ioService.load(inputCopy);
                monitor.start();
                ImageData output = processingService.process(input);
                PerformanceSnapshot stats = monitor.stop();

                // persist output — outside the monitored window
                File out = saveToUploadsOutput(output, uuid);
                return ProcessingResult.of(output, stats, inputCopy, out);
            }
        };

        task.setOnSucceeded(e -> {
            ProcessingResult r = task.getValue();
            processedOutput = r.getOutput();
            outputFile      = r.getOutputFile();
            outputPreviewPane.setCursor(Cursor.HAND);
            populateOutputLabels(processedOutput, outputFile);
            populateInfoBox(metricsBox, r.getStats().getMetrics());
            long wallMs = r.getStats().getWallTimeMs();
            lblWallTime.setText(wallMs + " ms");
            wallTimeCard.setVisible(true);
            wallTimeCard.setManaged(true);
            btnProcess.setDisable(false);
            btnSave.setDisable(false);
            btnOpenOutput.setDisable(false);
            setStatus("Done — " + algo + " — " + wallMs + " ms", "#16a34a");

            GenerationRecord rec = GenerationRecord.builder()
                    .uuid(uuid)
                    .algorithmName(algo)
                    .inputFilename(r.getInputFile().getName())
                    .outputFilename(r.getOutputFile().getName())
                    .wallTimeMs(wallMs)
                    .metrics(r.getStats().getMetrics())
                    .createdAt(Instant.now())
                    .imageWidth(r.getOutput().getWidth())
                    .imageHeight(r.getOutput().getHeight())
                    .build();
            genRepo.save(rec);
            addHistoryCard(rec);
        });

        task.setOnFailed(e -> {
            btnProcess.setDisable(false);
            setStatus("Error: " + task.getException().getMessage(), "#dc2626");
        });

        new Thread(task, "image-processor").start();
    }

    @FXML
    private void handleReset() {
        selectedFile      = null;
        sessionUuid       = null;
        loadedHistoryUuid = null;
        inputImageView.setImage(null);
        inputPlaceholder.setVisible(true);
        lblInputFile.setText("—");
        lblInputFormat.setText("—");
        lblInputSize.setText("—");
        lblInputDimensions.setText("—");
        lblInputPixels.setText("—");
        btnProcess.setDisable(true);
        btnOpenInput.setDisable(true);
        refreshHistoryActiveStyle();
        resetOutput();
    }

    @FXML
    private void handleOpenInput() {
        openInViewer(selectedFile);
    }

    @FXML
    private void handleOpenOutput() {
        openInViewer(outputFile);
    }

    @FXML
    private void handleSave() {
        if (processedOutput == null) return;
        File dest = saveChooser("Save Output Image",
                new FileChooser.ExtensionFilter("JPEG Image", "*.jpg"));
        if (dest == null) return;

        ioService.save(processedOutput, dest);
        lblOutputFile.setText(dest.getName());
        lblOutputSize.setText(
                ImageMetadata.fromFile(dest, processedOutput.getWidth(), processedOutput.getHeight())
                        .getHumanSize());
        setStatus("Saved → " + dest.getName(), "#2563eb");
    }

    // ── private helpers ───────────────────────────────────────────────────────────

    private void populateInputLabels(ImageMetadata meta) {
        lblInputFile.setText(meta.getName());
        lblInputFormat.setText(meta.getFormat());
        lblInputSize.setText(meta.getHumanSize());
        lblInputDimensions.setText(meta.getDimensions());
        lblInputPixels.setText(String.format("%,d", meta.getPixelCount()));
    }

    private void populateOutputLabels(ImageData data, File file) {
        BufferedImage bImg = ioService.toBufferedImage(data);
        WritableImage fxImg = new WritableImage(bImg.getWidth(), bImg.getHeight());
        javafx.embed.swing.SwingFXUtils.toFXImage(bImg, fxImg);
        outputImageView.setImage(fxImg);
        outputPlaceholder.setVisible(false);

        lblOutputFile.setText(file != null ? file.getName() : "output.jpg");
        lblOutputFormat.setText("JPG");
        lblOutputDimensions.setText(data.getWidth() + " × " + data.getHeight() + " px");
        lblOutputPixels.setText(String.format("%,d", data.getPixelCount()));
        lblOutputSize.setText(file != null
                ? ImageMetadata.fromFile(file, data.getWidth(), data.getHeight()).getHumanSize()
                : "—");
    }

    private void populateInfoBox(VBox box, Map<String, String> entries) {
        box.getChildren().clear();
        boolean first = true;
        for (Map.Entry<String, String> entry : entries.entrySet()) {
            if (!first) {
                Separator sep = new Separator();
                sep.setStyle("-fx-opacity: 0.4;");
                box.getChildren().add(sep);
            }
            first = false;

            HBox row = new HBox(8);
            row.setAlignment(Pos.CENTER_LEFT);
            row.setStyle("-fx-padding: 3 0;");

            Label key = new Label(entry.getKey());
            key.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 11px;");
            key.setMinWidth(80);

            Label val = new Label(entry.getValue());
            val.setStyle("-fx-text-fill: #1e293b; -fx-font-size: 11px; -fx-font-weight: bold;");
            val.setWrapText(true);
            HBox.setHgrow(val, Priority.ALWAYS);

            row.getChildren().addAll(key, val);
            box.getChildren().add(row);
        }
    }

    private void resetOutput() {
        outputImageView.setImage(null);
        outputPlaceholder.setVisible(true);
        lblOutputFile.setText("—");
        lblOutputFormat.setText("—");
        lblOutputSize.setText("—");
        lblOutputDimensions.setText("—");
        lblOutputPixels.setText("—");
        metricsBox.getChildren().clear();
        wallTimeCard.setVisible(false);
        wallTimeCard.setManaged(false);
        processedOutput = null;
        outputFile      = null;
        outputPreviewPane.setCursor(Cursor.DEFAULT);
        outputPreviewPane.setStyle(PANE_BASE);
        btnSave.setDisable(true);
        btnOpenOutput.setDisable(true);
        setStatus("Ready", "#64748b");
    }

    private void setStatus(String text, String hexColor) {
        lblStatus.setText(text);
        lblStatus.setStyle("-fx-text-fill: " + hexColor + "; -fx-font-size: 11px; -fx-font-style: italic;");
    }

    private File openChooser(String title, FileChooser.ExtensionFilter filter) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.getExtensionFilters().add(filter);
        return fc.showOpenDialog(inputImageView.getScene().getWindow());
    }

    private File saveChooser(String title, FileChooser.ExtensionFilter filter) {
        FileChooser fc = new FileChooser();
        fc.setTitle(title);
        fc.getExtensionFilters().add(filter);
        fc.setInitialFileName("output.jpg");
        return fc.showSaveDialog(outputImageView.getScene().getWindow());
    }

    private void setupInputPreviewPane() {
        inputPreviewPane.setCursor(Cursor.HAND);
        inputPreviewPane.setOnMouseEntered(e -> {
            inputPreviewPane.setStyle(INPUT_PANE_HOVER + " -fx-cursor: hand;");
            animateScale(inputPreviewPane, 1.0, 1.02);
        });
        inputPreviewPane.setOnMouseExited(e -> {
            inputPreviewPane.setStyle(PANE_BASE + " -fx-cursor: hand;");
            animateScale(inputPreviewPane, 1.02, 1.0);
        });
        inputPreviewPane.setOnMouseClicked(e -> {
            if (selectedFile != null) openInViewer(selectedFile);
            else handleChooseFile();
        });
    }

    private void setupOutputPreviewPane() {
        outputPreviewPane.setOnMouseEntered(e -> {
            if (outputFile == null) return;
            outputPreviewPane.setStyle(OUTPUT_PANE_HOVER + " -fx-cursor: hand;");
            animateScale(outputPreviewPane, 1.0, 1.02);
        });
        outputPreviewPane.setOnMouseExited(e -> {
            outputPreviewPane.setStyle(PANE_BASE);
            if (outputFile != null) animateScale(outputPreviewPane, 1.02, 1.0);
        });
        outputPreviewPane.setOnMouseClicked(e -> {
            if (outputFile != null) openInViewer(outputFile);
        });
    }

    private void setupButtonHover(Button... buttons) {
        for (Button btn : buttons) {
            btn.setOnMouseEntered(e -> animateScale(btn, 1.0, 1.04));
            btn.setOnMouseExited(e ->  animateScale(btn, 1.04, 1.0));
        }
    }

    private void animateScale(Node node, double from, double to) {
        ScaleTransition st = new ScaleTransition(Duration.millis(120), node);
        st.setFromX(from); st.setFromY(from);
        st.setToX(to);     st.setToY(to);
        st.play();
    }

    private void openInViewer(File file) {
        if (file == null || !file.exists()) return;
        try {
            Desktop.getDesktop().open(file);
        } catch (IOException e) {
            setStatus("Cannot open viewer: " + e.getMessage(), "#dc2626");
        }
    }

    private File copyToUploadsInput(File source, String uuid) {
        try {
            Path inputDir = uploadsBase.resolve("input");
            Files.createDirectories(inputDir);
            String ext = extension(source.getName());
            File dest = inputDir.resolve("input_" + uuid + ext).toFile();
            Files.copy(source.toPath(), dest.toPath(), StandardCopyOption.REPLACE_EXISTING);
            return dest;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to copy input to uploads", e);
        }
    }

    private File saveToUploadsOutput(ImageData data, String uuid) {
        try {
            Path outputDir = uploadsBase.resolve("output");
            Files.createDirectories(outputDir);
            File dest = outputDir.resolve("output_" + uuid + ".jpg").toFile();
            ioService.save(data, dest);
            return dest;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to save output to uploads", e);
        }
    }

    private static String extension(String filename) {
        int dot = filename.lastIndexOf('.');
        return dot >= 0 ? filename.substring(dot).toLowerCase() : ".jpg";
    }

    @FXML
    private void handleExportCsv() {
        List<GenerationRecord> all = genRepo.findAll();
        if (all.isEmpty()) {
            setStatus("No history to export.", "#64748b");
            return;
        }
        File dest = saveChooser("Export History as CSV",
                new FileChooser.ExtensionFilter("CSV File", "*.csv"));
        if (dest == null) return;
        try {
            csvExporter.export(all, dest);
            setStatus("Exported " + all.size() + " records → " + dest.getName(), "#2563eb");
        } catch (IOException ex) {
            setStatus("Export failed: " + ex.getMessage(), "#dc2626");
        }
    }

    @FXML
    private void handleDeleteAll() {
        genRepo.deleteAll();
        historyBox.getChildren().clear();
        if (loadedHistoryUuid != null) handleReset();
        setStatus("History cleared.", "#64748b");
    }

    @FXML
    private void handleDeleteSelected() {
        var toRemove = historyBox.getChildren().stream()
                .filter(node -> {
                    if (!(node instanceof HBox row)) return false;
                    return row.getChildren().stream()
                            .filter(c -> c instanceof CheckBox)
                            .map(c -> (CheckBox) c)
                            .findFirst()
                            .map(CheckBox::isSelected)
                            .orElse(false);
                })
                .toList();

        boolean deletedLoaded = false;
        for (var node : toRemove) {
            String uuid = (String) node.getUserData();
            if (uuid != null) {
                genRepo.delete(uuid);
                if (uuid.equals(loadedHistoryUuid)) deletedLoaded = true;
            }
        }
        historyBox.getChildren().removeAll(toRemove);
        if (deletedLoaded) handleReset();

        if (!toRemove.isEmpty())
            setStatus("Deleted " + toRemove.size() + " history record(s).", "#64748b");
    }

    private void loadHistoryFromRepo() {
        // oldest→newest from repo; addFirst reverses them so newest appears at top
        List<GenerationRecord> records = genRepo.findAll();
        for (GenerationRecord r : records) {
            addHistoryCard(r);
        }
    }

    private void addHistoryCard(GenerationRecord r) {
        HBox row = new HBox(7);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setUserData(r.getUuid());
        row.setStyle(HIST_ROW_BASE);

        // hover — only when not the active item
        row.setOnMouseEntered(e -> {
            if (!r.getUuid().equals(loadedHistoryUuid)) row.setStyle(HIST_ROW_HOVER);
        });
        row.setOnMouseExited(e -> {
            if (!r.getUuid().equals(loadedHistoryUuid)) row.setStyle(HIST_ROW_BASE);
        });

        // row click → load into UI (ignore clicks on checkbox or delete button)
        row.setOnMouseClicked(e -> {
            if (e.getTarget() instanceof CheckBox) return;
            if (e.getTarget() instanceof Button)  return;
            loadHistoryItem(r);
        });

        // multi-select checkbox — consume click so it doesn't bubble to the row handler
        CheckBox cb = new CheckBox();
        cb.setStyle("-fx-cursor: hand;");
        cb.setOnMouseClicked(javafx.event.Event::consume);

        // thumbnail
        File outFile = uploadsBase.resolve("output").resolve(r.getOutputFilename()).toFile();
        javafx.scene.image.ImageView thumb = new javafx.scene.image.ImageView();
        thumb.setFitWidth(68);
        thumb.setFitHeight(52);
        thumb.setPreserveRatio(true);
        thumb.setCursor(Cursor.HAND);
        if (outFile.exists()) {
            try {
                thumb.setImage(new javafx.scene.image.Image(
                        outFile.toURI().toString(), 68, 52, true, true));
            } catch (Exception ignored) {}
        }
        // thumbnail click → open in OS viewer
        thumb.setOnMouseClicked(e -> {
            e.consume();
            openInViewer(outFile);
        });

        // info block
        VBox info = new VBox(2);
        HBox.setHgrow(info, Priority.ALWAYS);

        // line 1: algorithm + wall time
        HBox line1 = new HBox(6);
        line1.setAlignment(Pos.CENTER_LEFT);

        Label algoLbl = new Label(r.getAlgorithmName());
        algoLbl.setStyle("-fx-text-fill: #2563eb; -fx-font-size: 10px; -fx-font-weight: bold;");
        algoLbl.setWrapText(false);

        Label timeLbl = new Label(r.getWallTimeMs() + " ms");
        timeLbl.setStyle("-fx-text-fill: #c2410c; -fx-font-size: 10px; -fx-font-weight: bold;");
        HBox.setHgrow(timeLbl, Priority.ALWAYS);

        line1.getChildren().addAll(algoLbl, timeLbl);

        // line 2: dimensions + date
        HBox line2 = new HBox(6);
        line2.setAlignment(Pos.CENTER_LEFT);

        Label dimLbl = new Label(r.getImageWidth() + "×" + r.getImageHeight());
        dimLbl.setStyle("-fx-text-fill: #64748b; -fx-font-size: 9px;");
        HBox.setHgrow(dimLbl, Priority.ALWAYS);

        Label dateLbl = new Label(formatTimestamp(r.getCreatedAt()));
        dateLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 9px;");

        line2.getChildren().addAll(dimLbl, dateLbl);

        // line 3: output filename (truncated)
        Label fileLbl = new Label(r.getOutputFilename());
        fileLbl.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 9px;");
        fileLbl.setMaxWidth(Double.MAX_VALUE);

        info.getChildren().addAll(line1, line2, fileLbl);

        // per-row delete button
        Button delBtn = new Button("✕");
        delBtn.setStyle("-fx-background-color: #fef2f2; -fx-text-fill: #dc2626; -fx-font-size: 10px; " +
                        "-fx-font-weight: bold; -fx-background-radius: 4; -fx-border-color: #fecaca; " +
                        "-fx-border-radius: 4; -fx-padding: 2 6; -fx-cursor: hand;");
        delBtn.setOnAction(e -> {
            boolean wasLoaded = r.getUuid().equals(loadedHistoryUuid);
            genRepo.delete(r.getUuid());
            historyBox.getChildren().remove(row);
            if (wasLoaded) handleReset();
        });
        delBtn.setOnMouseEntered(e -> animateScale(delBtn, 1.0, 1.08));
        delBtn.setOnMouseExited(e ->  animateScale(delBtn, 1.08, 1.0));
        // consume mouse click so it doesn't bubble to the row click handler
        delBtn.setOnMouseClicked(javafx.event.Event::consume);

        row.getChildren().addAll(cb, thumb, info, delBtn);
        historyBox.getChildren().addFirst(row);
    }

    private void loadHistoryItem(GenerationRecord r) {
        File inputFile = uploadsBase.resolve("input").resolve(r.getInputFilename()).toFile();
        File outFile   = uploadsBase.resolve("output").resolve(r.getOutputFilename()).toFile();

        if (!inputFile.exists() && !outFile.exists()) {
            setStatus("Files for this history item no longer exist on disk.", "#dc2626");
            return;
        }

        loadedHistoryUuid = r.getUuid();
        refreshHistoryActiveStyle();

        // ── input side ────────────────────────────────────────────
        if (inputFile.exists()) {
            selectedFile = inputFile;
            populateInputLabels(metadataService.read(inputFile));
            inputImageView.setImage(new javafx.scene.image.Image(inputFile.toURI().toString()));
            inputPlaceholder.setVisible(false);
            btnProcess.setDisable(false);
            btnOpenInput.setDisable(false);
        } else {
            lblInputFile.setText(r.getInputFilename());
            lblInputFormat.setText("—");
            lblInputSize.setText("(file removed)");
            lblInputDimensions.setText(r.getImageWidth() + " × " + r.getImageHeight() + " px");
            lblInputPixels.setText(String.format("%,d", (long) r.getImageWidth() * r.getImageHeight()));
        }

        // ── output side ───────────────────────────────────────────
        if (outFile.exists()) {
            try {
                ImageData loadedOutput = ioService.load(outFile);
                processedOutput = loadedOutput;
                outputFile = outFile;
                populateOutputLabels(loadedOutput, outFile);
                outputPreviewPane.setCursor(Cursor.HAND);
                btnSave.setDisable(false);
                btnOpenOutput.setDisable(false);
            } catch (Exception ex) {
                setStatus("Could not reload output image: " + ex.getMessage(), "#dc2626");
            }
        }

        // ── monitor side ──────────────────────────────────────────
        lblWallTime.setText(r.getWallTimeMs() + " ms");
        wallTimeCard.setVisible(true);
        wallTimeCard.setManaged(true);
        if (r.getMetrics() != null && !r.getMetrics().isEmpty()) {
            populateInfoBox(metricsBox, r.getMetrics());
        }

        // restore algorithm selection
        if (algorithmCombo.getItems().contains(r.getAlgorithmName())) {
            algorithmCombo.setValue(r.getAlgorithmName());
        }

        setStatus("Loaded: " + r.getAlgorithmName() + " — " + r.getWallTimeMs() + " ms", "#7c3aed");
    }

    private void refreshHistoryActiveStyle() {
        for (javafx.scene.Node n : historyBox.getChildren()) {
            if (!(n instanceof HBox row)) continue;
            String uuid = (String) row.getUserData();
            boolean active = loadedHistoryUuid != null && loadedHistoryUuid.equals(uuid);
            row.setStyle(active ? HIST_ROW_ACTIVE : HIST_ROW_BASE);
        }
    }

    private static final DateTimeFormatter HIST_FMT =
            DateTimeFormatter.ofPattern("MM/dd HH:mm").withZone(ZoneId.systemDefault());

    private String formatTimestamp(Instant instant) {
        return HIST_FMT.format(instant);
    }

    // ── inner class ───────────────────────────────────────────────────────────────

    @Getter
    @AllArgsConstructor(staticName = "of")
    private static class ProcessingResult {
        private final ImageData           output;
        private final PerformanceSnapshot stats;
        private final File                inputFile;
        private final File                outputFile;
    }
}
