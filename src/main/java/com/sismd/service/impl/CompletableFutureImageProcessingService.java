package com.sismd.service.impl;

import com.sismd.model.ImageData;
import com.sismd.service.ImageProcessingService;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.IntUnaryOperator;

/**
 * Histogram equalization composed as an asynchronous CompletableFuture
 * pipeline.
 *
 * Stage 1 (histogram): N supplyAsync tasks each return a partial int[256].
 * allOf waits for completion; thenApply merges the partials.
 * Stage 2 (cumulative): sequential prefix-sum, chained via join().
 * Stage 3 (pixel write): N runAsync tasks write disjoint columns; allOf waits.
 *
 * A dedicated fixed-thread-pool executor is created per process() call and
 * shut down in a finally block to prevent thread leaks.
 */
public class CompletableFutureImageProcessingService implements ImageProcessingService {

    private final int partitions;

    public CompletableFutureImageProcessingService() {
        this(Runtime.getRuntime().availableProcessors());
    }

    public CompletableFutureImageProcessingService(int partitions) {
        if (partitions < 1)
            throw new IllegalArgumentException("partitions must be >= 1");
        this.partitions = partitions;
    }

    @Override
    public ImageData process(ImageData input) {
        var w = input.getWidth();
        var h = input.getHeight();
        var totalPixels = w * h;
        var parts = Math.min(partitions, w);

        ExecutorService exec = Executors.newFixedThreadPool(parts);
        try {
            var hist = computeHistogramAsync(input, w, h, parts, exec);
            var cumulative = HistogramUtils.buildCumulativeHistogram(hist);
            var out = applyEqualizationAsync(input, w, h, cumulative, totalPixels, parts, exec);
            return ImageData.of(out, w, h);
        } finally {
            exec.shutdown();
        }
    }

    // ── stage 1 ──────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private int[] computeHistogramAsync(ImageData img, int w, int h,
            int parts, ExecutorService exec) {
        int colsPerPart = (w + parts - 1) / parts;
        CompletableFuture<int[]>[] futures = new CompletableFuture[parts];

        for (int p = 0; p < parts; p++) {
            final int startCol = p * colsPerPart;
            final int endCol = Math.min(startCol + colsPerPart, w);
            futures[p] = CompletableFuture.supplyAsync(() -> {
                int[] partial = new int[256];
                for (int x = startCol; x < endCol; x++)
                    for (int y = 0; y < h; y++)
                        partial[HistogramUtils.luminosity(img.getPixel(x, y))]++;
                return partial;
            }, exec);
        }

        // allOf guarantees every future is done before thenApply runs,
        // so the inner f.join() calls return immediately without blocking.
        return CompletableFuture.allOf(futures)
                .thenApply(v -> {
                    int[] merged = new int[256];
                    for (CompletableFuture<int[]> f : futures)
                        merged = HistogramUtils.mergeHistograms(merged, f.join());
                    return merged;
                })
                .join();
    }

    // ── stage 3 ──────────────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    private int[] applyEqualizationAsync(ImageData img, int w, int h, int[] cumulative,
            int totalPixels,
            int parts, ExecutorService exec) {
        int colsPerPart = (w + parts - 1) / parts;
        var out = new int[w * h];
        CompletableFuture<Void>[] futures = new CompletableFuture[parts];

        // Functional pixel mapper — shared (stateless), created once
        IntUnaryOperator eq = HistogramUtils.equalizer(cumulative, totalPixels);

        for (int p = 0; p < parts; p++) {
            final int startCol = p * colsPerPart;
            final int endCol = Math.min(startCol + colsPerPart, w);
            futures[p] = CompletableFuture.runAsync(() -> {
                for (int x = startCol; x < endCol; x++)
                    for (int y = 0; y < h; y++)
                        out[x * h + y] = eq.applyAsInt(img.getPixel(x, y));
            }, exec);
        }

        CompletableFuture.allOf(futures).join();
        return out;
    }

    @Override
    public String toString() {
        return "CompletableFuture (" + partitions + ")";
    }
}
