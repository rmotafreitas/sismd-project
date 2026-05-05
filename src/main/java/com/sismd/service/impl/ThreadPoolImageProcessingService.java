package com.sismd.service.impl;

import com.sismd.model.ImageData;
import com.sismd.service.ImageProcessingService;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.function.IntUnaryOperator;

/**
 * Histogram equalization using a fixed-size thread pool (ExecutorService).
 *
 * Stage 1 (histogram): each partition submits a Callable that returns a
 * partial int[256]; results are merged after all futures complete.
 * Stage 2 (cumulative): sequential prefix-sum on the calling thread.
 * Stage 3 (pixel write): each partition submits a Runnable; writes to disjoint
 * columns so no synchronisation is needed on the output array.
 *
 * The pool is created per process() call and shut down in a finally block
 * to guarantee no thread leak even when an exception is thrown.
 */
public class ThreadPoolImageProcessingService implements ImageProcessingService {

    private final int poolSize;

    public ThreadPoolImageProcessingService() {
        this(Runtime.getRuntime().availableProcessors());
    }

    public ThreadPoolImageProcessingService(int poolSize) {
        if (poolSize < 1)
            throw new IllegalArgumentException("poolSize must be >= 1");
        this.poolSize = poolSize;
    }

    @Override
    public ImageData process(ImageData input) {
        var w = input.getWidth();
        var h = input.getHeight();
        var totalPixels = w * h;
        var threads = Math.min(poolSize, w);

        ExecutorService pool = Executors.newFixedThreadPool(threads);
        try {
            var hist = computeHistogramParallel(input, w, h, threads, pool);
            var cumulative = HistogramUtils.buildCumulativeHistogram(hist);
            var out = applyEqualizationParallel(input, w, h, cumulative, totalPixels, threads, pool);
            return ImageData.of(out, w, h);
        } finally {
            pool.shutdown();
        }
    }

    // ── stage 1 ──────────────────────────────────────────────────────────────────

    private int[] computeHistogramParallel(ImageData img, int w, int h,
            int threads, ExecutorService pool) {
        int colsPerThread = (w + threads - 1) / threads;
        List<Future<int[]>> futures = new ArrayList<>(threads);

        for (int t = 0; t < threads; t++) {
            final int startCol = t * colsPerThread;
            final int endCol = Math.min(startCol + colsPerThread, w);
            futures.add(pool.submit(() -> {
                int[] partial = new int[256];
                for (int x = startCol; x < endCol; x++)
                    for (int y = 0; y < h; y++)
                        partial[HistogramUtils.luminosity(img.getPixel(x, y))]++;
                return partial;
            }));
        }

        int[] merged = new int[256];
        for (Future<int[]> f : futures) {
            try {
                merged = HistogramUtils.mergeHistograms(merged, f.get());
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted during histogram computation", e);
            } catch (ExecutionException e) {
                throw new RuntimeException("Task failed during histogram computation", e.getCause());
            }
        }
        return merged;
    }

    // ── stage 3 ──────────────────────────────────────────────────────────────────

    private int[] applyEqualizationParallel(ImageData img, int w, int h, int[] cumulative,
            int totalPixels,
            int threads, ExecutorService pool) {
        int colsPerThread = (w + threads - 1) / threads;
        var out = new int[w * h];
        List<Future<?>> futures = new ArrayList<>(threads);

        // Functional pixel mapper — shared (stateless), created once
        IntUnaryOperator eq = HistogramUtils.equalizer(cumulative, totalPixels);

        for (int t = 0; t < threads; t++) {
            final int startCol = t * colsPerThread;
            final int endCol = Math.min(startCol + colsPerThread, w);
            futures.add(pool.submit(() -> {
                for (int x = startCol; x < endCol; x++)
                    for (int y = 0; y < h; y++)
                        out[x * h + y] = eq.applyAsInt(img.getPixel(x, y));
            }));
        }

        for (Future<?> f : futures) {
            try {
                f.get();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted during pixel equalization", e);
            } catch (ExecutionException e) {
                throw new RuntimeException("Task failed during pixel equalization", e.getCause());
            }
        }
        return out;
    }

    @Override
    public String toString() {
        return "Thread Pool (" + poolSize + ")";
    }
}
