package com.sismd.service.impl;

import com.sismd.model.ImageData;
import com.sismd.service.ImageProcessingService;

import java.awt.Color;
import java.util.concurrent.atomic.AtomicIntegerArray;

/**
 * Histogram equalization using manually managed threads — no pools.
 *
 * Stage 1 (histogram):   N threads each compute a partial histogram for their
 *                        column slice; results are accumulated atomically.
 * Stage 2 (cumulative):  Sequential prefix-sum on the calling thread.
 * Stage 3 (pixel write): N threads each rewrite their column slice; writes
 *                        are to disjoint columns so no synchronization is needed.
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
        if (numThreads < 1) throw new IllegalArgumentException("numThreads must be >= 1");
        this.numThreads = numThreads;
    }

    @Override
    public ImageData process(ImageData input) {
        int w = input.getWidth(), h = input.getHeight();
        int totalPixels = w * h;

        // Stage 1 — parallel histogram (thread-safe via AtomicIntegerArray)
        int[] hist = buildHistogramParallel(input, w, h);

        // Stage 2 — cumulative prefix-sum (inherently sequential)
        int[] cumulative = HistogramUtils.buildCumulativeHistogram(hist);
        int   cdfMin     = HistogramUtils.firstNonZero(cumulative);

        // Stage 3 — parallel pixel rewrite (disjoint writes → no contention)
        Color[][] out = applyEqualizationParallel(input, w, h, cumulative, totalPixels, cdfMin);

        return ImageData.of(out, w, h);
    }

    // ── stage 1 ──────────────────────────────────────────────────────────────────

    private int[] buildHistogramParallel(ImageData img, int w, int h) {
        int threads = Math.min(numThreads, w);
        int colsPerThread = (w + threads - 1) / threads;
        AtomicIntegerArray shared = new AtomicIntegerArray(256);

        Thread[] pool = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            final int startCol = t * colsPerThread;
            final int endCol   = Math.min(startCol + colsPerThread, w);
            pool[t] = new Thread(() -> {
                for (int x = startCol; x < endCol; x++)
                    for (int y = 0; y < h; y++)
                        shared.getAndIncrement(HistogramUtils.luminosity(img.getPixel(x, y)));
            });
            pool[t].start();
        }
        joinAll(pool);

        int[] hist = new int[256];
        for (int i = 0; i < 256; i++) hist[i] = shared.get(i);
        return hist;
    }

    // ── stage 3 ──────────────────────────────────────────────────────────────────

    private Color[][] applyEqualizationParallel(ImageData img, int w, int h,
                                                 int[] cumulative, int totalPixels, int cdfMin) {
        int threads = Math.min(numThreads, w);
        int colsPerThread = (w + threads - 1) / threads;
        Color[][] out = new Color[w][h];

        Thread[] pool = new Thread[threads];
        for (int t = 0; t < threads; t++) {
            final int startCol = t * colsPerThread;
            final int endCol   = Math.min(startCol + colsPerThread, w);
            pool[t] = new Thread(() -> {
                for (int x = startCol; x < endCol; x++)
                    for (int y = 0; y < h; y++) {
                        int lum    = HistogramUtils.luminosity(img.getPixel(x, y));
                        int newLum = HistogramUtils.equalize(cumulative[lum], totalPixels, cdfMin);
                        out[x][y]  = new Color(newLum, newLum, newLum);
                    }
            });
            pool[t].start();
        }
        joinAll(pool);
        return out;
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    private static void joinAll(Thread[] pool) {
        for (Thread t : pool) {
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
