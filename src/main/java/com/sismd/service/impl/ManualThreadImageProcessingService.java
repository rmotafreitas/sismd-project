package com.sismd.service.impl;

import com.sismd.model.ImageData;
import com.sismd.service.ImageProcessingService;

import java.util.function.IntUnaryOperator;

/**
 * Histogram equalization using manually managed threads — no pools.
 *
 * Stage 1 (histogram): N threads each compute a <b>local</b> partial histogram
 * for their column slice; the main thread merges them after
 * all workers join — zero contention, no atomics needed.
 * Stage 2 (cumulative): Sequential prefix-sum on the calling thread.
 * Stage 3 (pixel write): N threads each rewrite their column slice; writes
 * are to disjoint columns so no synchronization is needed.
 *
 * Thread count defaults to the number of available CPU cores and is capped
 * at the image width so we never spawn idle threads.
 */
public class ManualThreadImageProcessingService implements ImageProcessingService {

    private final int numThreads;

    public ManualThreadImageProcessingService() {
        this(Runtime.getRuntime().availableProcessors());
    }

    public ManualThreadImageProcessingService(int numThreads) {
        if (numThreads < 1)
            throw new IllegalArgumentException("numThreads must be >= 1");
        this.numThreads = numThreads;
    }

    @Override
    public ImageData process(ImageData input) {
        var w = input.getWidth();
        var h = input.getHeight();
        var totalPixels = w * h;

        // Stage 1 — parallel histogram (thread-local partials → merge)
        var hist = buildHistogramParallel(input, w, h);

        // Stage 2 — cumulative prefix-sum (inherently sequential)
        var cumulative = HistogramUtils.buildCumulativeHistogram(hist);

        // Stage 3 — parallel pixel rewrite (disjoint writes → no contention)
        var out = applyEqualizationParallel(input, w, h, cumulative, totalPixels);

        return ImageData.of(out, w, h);
    }

    // ── stage 1 ──────────────────────────────────────────────────────────────────

    private int[] buildHistogramParallel(ImageData img, int w, int h) {
        int threads = Math.min(numThreads, w);
        int colsPerThread = (w + threads - 1) / threads;

        // Each thread writes to its OWN int[256] — no sharing, no atomics
        int[][] partials = new int[threads][256];
        Thread[] workers = new Thread[threads];

        for (int t = 0; t < threads; t++) {
            final int tid = t;
            final int startCol = t * colsPerThread;
            final int endCol = Math.min(startCol + colsPerThread, w);
            workers[t] = new Thread(() -> {
                var local = partials[tid];
                for (int x = startCol; x < endCol; x++)
                    for (int y = 0; y < h; y++)
                        local[HistogramUtils.luminosity(img.getPixel(x, y))]++;
            });
            workers[t].start();
        }
        joinAll(workers);

        // Merge all partial histograms (sequential — only 256 × N additions)
        var merged = partials[0];
        for (int t = 1; t < threads; t++)
            merged = HistogramUtils.mergeHistograms(merged, partials[t]);
        return merged;
    }

    // ── stage 3 ──────────────────────────────────────────────────────────────────

    private int[] applyEqualizationParallel(ImageData img, int w, int h,
            int[] cumulative, int totalPixels) {
        int threads = Math.min(numThreads, w);
        int colsPerThread = (w + threads - 1) / threads;
        var out = new int[w * h];

        // Functional pixel mapper — shared (stateless), created once
        IntUnaryOperator eq = HistogramUtils.equalizer(cumulative, totalPixels);

        Thread[] workers = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            final int startCol = t * colsPerThread;
            final int endCol = Math.min(startCol + colsPerThread, w);
            workers[t] = new Thread(() -> {
                for (int x = startCol; x < endCol; x++)
                    for (int y = 0; y < h; y++)
                        out[x * h + y] = eq.applyAsInt(img.getPixel(x, y));
            });
            workers[t].start();
        }
        joinAll(workers);
        return out;
    }

    // ── helpers
    // ───────────────────────────────────────────────────────────────────

    private static void joinAll(Thread[] workers) {
        for (Thread t : workers) {
            try {
                t.join();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted during parallel histogram equalization", e);
            }
        }
    }

    @Override
    public String toString() {
        return "Manual Threads (" + numThreads + ")";
    }
}
