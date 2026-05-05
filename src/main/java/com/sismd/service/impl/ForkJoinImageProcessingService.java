package com.sismd.service.impl;

import com.sismd.model.ImageData;
import com.sismd.service.ImageProcessingService;

import java.util.concurrent.ForkJoinPool;
import java.util.concurrent.RecursiveAction;
import java.util.concurrent.RecursiveTask;

/**
 * Histogram equalization using the Fork/Join framework.
 *
 * Stage 1 (histogram): {@link HistogramTask} recursively splits the column
 * range until ≤ threshold columns remain, computes a
 * partial histogram, then merges up the call tree.
 * Stage 2 (cumulative): sequential prefix-sum on the calling thread.
 * Stage 3 (pixel write): {@link EqualizationAction} mirrors stage 1's splitting
 * strategy; leaf tasks write disjoint columns so no
 * synchronisation is needed on the output array.
 *
 * Both tasks run on {@link ForkJoinPool#commonPool()} — the JVM-managed pool
 * with work-stealing that is optimised for recursive divide-and-conquer.
 */
public class ForkJoinImageProcessingService implements ImageProcessingService {

    private final int threshold;

    public ForkJoinImageProcessingService() {
        this(50);
    }

    public ForkJoinImageProcessingService(int threshold) {
        if (threshold < 1)
            throw new IllegalArgumentException("threshold must be >= 1");
        this.threshold = threshold;
    }

    @Override
    public ImageData process(ImageData input) {
        var w = input.getWidth();
        var h = input.getHeight();
        var totalPixels = w * h;

        var hist = ForkJoinPool.commonPool()
                .invoke(new HistogramTask(input, 0, w, h, threshold));
        var cumulative = HistogramUtils.buildCumulativeHistogram(hist);

        var out = new int[w * h];
        ForkJoinPool.commonPool()
                .invoke(new EqualizationAction(input, out, 0, w, h, cumulative, totalPixels, threshold));

        return ImageData.of(out, w, h);
    }

    // ── Stage 1: RecursiveTask<int[]> ────────────────────────────────────────────

    static final class HistogramTask extends RecursiveTask<int[]> {

        private final ImageData img;
        private final int startCol, endCol, h, threshold;

        HistogramTask(ImageData img, int startCol, int endCol, int h, int threshold) {
            this.img = img;
            this.startCol = startCol;
            this.endCol = endCol;
            this.h = h;
            this.threshold = threshold;
        }

        @Override
        protected int[] compute() {
            if (endCol - startCol <= threshold) {
                int[] hist = new int[256];
                for (int x = startCol; x < endCol; x++)
                    for (int y = 0; y < h; y++)
                        hist[HistogramUtils.luminosity(img.getPixel(x, y))]++;
                return hist;
            }
            int mid = (startCol + endCol) >>> 1;
            HistogramTask left = new HistogramTask(img, startCol, mid, h, threshold);
            HistogramTask right = new HistogramTask(img, mid, endCol, h, threshold);
            left.fork();
            int[] rightResult = right.compute();
            int[] leftResult = left.join();
            return HistogramUtils.mergeHistograms(leftResult, rightResult);
        }
    }

    // ── Stage 3: RecursiveAction ─────────────────────────────────────────────────

    static final class EqualizationAction extends RecursiveAction {

        private final ImageData img;
        private final int[] out;
        private final int startCol, endCol, h;
        private final int[] cumulative;
        private final int totalPixels, threshold;

        EqualizationAction(ImageData img, int[] out, int startCol, int endCol, int h,
                int[] cumulative, int totalPixels, int threshold) {
            this.img = img;
            this.out = out;
            this.startCol = startCol;
            this.endCol = endCol;
            this.h = h;
            this.cumulative = cumulative;
            this.totalPixels = totalPixels;
            this.threshold = threshold;
        }

        @Override
        protected void compute() {
            if (endCol - startCol <= threshold) {
                // Leaf task — apply equalization via functional pixel mapper
                var eq = HistogramUtils.equalizer(cumulative, totalPixels);
                for (int x = startCol; x < endCol; x++)
                    for (int y = 0; y < h; y++)
                        out[x * h + y] = eq.applyAsInt(img.getPixel(x, y));
                return;
            }
            int mid = (startCol + endCol) >>> 1;
            var left = new EqualizationAction(img, out, startCol, mid, h, cumulative, totalPixels, threshold);
            var right = new EqualizationAction(img, out, mid, endCol, h, cumulative, totalPixels, threshold);
            left.fork();
            right.compute();
            left.join();
        }
    }

    @Override
    public String toString() {
        return "Fork / Join (t=" + threshold + ")";
    }
}
