package com.sismd.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.GridPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;

import java.lang.management.ManagementFactory;

/**
 * Modal dialog for configuring algorithm parameters at runtime — thread count,
 * ForkJoin granularity threshold, and GC collector inspection.
 */
public final class DeveloperOptionsDialog extends Dialog<DeveloperOptionsDialog.Settings> {

    private final Spinner<Integer> threadSpinner;
    private final Spinner<Integer> thresholdSpinner;

    public DeveloperOptionsDialog(int currentThreads, int currentThreshold) {
        setTitle("Developer Options");
        setHeaderText("Configure algorithm parameters");
        setResizable(false);

        DialogPane pane = getDialogPane();
        pane.getButtonTypes().addAll(ButtonType.APPLY, ButtonType.CANCEL);
        pane.setPrefWidth(380);

        // ── thread count ──────────────────────────────────────────────────
        Label threadLabel = new Label("Thread Count");
        threadLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        Label threadDesc = new Label(
                "Number of worker threads for ManualThread, ThreadPool, and CompletableFuture.\n"
                        + "ForkJoin always uses the common pool (all available cores).");
        threadDesc.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px; -fx-wrap-text: true;");
        threadDesc.setMaxWidth(340);

        threadSpinner = new Spinner<>(1, 64, currentThreads);
        threadSpinner.setEditable(true);
        threadSpinner.setPrefWidth(100);
        threadSpinner.getStyleClass().add(Spinner.STYLE_CLASS_SPLIT_ARROWS_HORIZONTAL);

        // ── ForkJoin threshold ────────────────────────────────────────────
        Label thresholdLabel = new Label("ForkJoin Granularity Threshold (columns)");
        thresholdLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        Label thresholdDesc = new Label(
                "Maximum columns per leaf task in the recursive decomposition.\n"
                        + "Lower values → more tasks, higher scheduling overhead.\n"
                        + "Higher values → fewer tasks, risk of load imbalance.");
        thresholdDesc.setStyle("-fx-text-fill: #64748b; -fx-font-size: 11px; -fx-wrap-text: true;");
        thresholdDesc.setMaxWidth(340);

        thresholdSpinner = new Spinner<>(1, 2000, currentThreshold);
        thresholdSpinner.setEditable(true);
        thresholdSpinner.setPrefWidth(100);
        thresholdSpinner.getStyleClass().add(Spinner.STYLE_CLASS_SPLIT_ARROWS_HORIZONTAL);

        // ── GC info (read-only) ───────────────────────────────────────────
        Label gcLabel = new Label("Current Garbage Collector");
        gcLabel.setStyle("-fx-font-weight: bold; -fx-font-size: 12px;");
        String gcNames = ManagementFactory.getGarbageCollectorMXBeans().stream()
                .map(gc -> gc.getName())
                .reduce((a, b) -> a + " + " + b)
                .orElse("Unknown");
        Label gcValue = new Label(gcNames);
        gcValue.setStyle("-fx-text-fill: #ea580c; -fx-font-size: 12px; -fx-font-weight: bold;");
        Label gcNote = new Label("(cannot be changed at runtime — use JVM flags at startup)");
        gcNote.setStyle("-fx-text-fill: #94a3b8; -fx-font-size: 10px;");

        // ── layout ────────────────────────────────────────────────────────
        VBox content = new VBox(12);
        content.setPadding(new Insets(8, 0, 0, 0));

        content.getChildren().addAll(
                threadLabel, threadDesc, threadSpinner,
                new Separator(),
                thresholdLabel, thresholdDesc, thresholdSpinner,
                new Separator(),
                gcLabel, gcValue, gcNote);

        pane.setContent(content);

        // ── result converter ──────────────────────────────────────────────
        setResultConverter(buttonType -> {
            if (buttonType == ButtonType.APPLY) {
                return new Settings(threadSpinner.getValue(), thresholdSpinner.getValue());
            }
            return null;
        });
    }

    /**
     * Immutable result returned when the user clicks Apply.
     */
    public record Settings(int threadCount, int forkJoinThreshold) {
    }
}
