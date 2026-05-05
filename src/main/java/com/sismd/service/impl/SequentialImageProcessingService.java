package com.sismd.service.impl;

import com.sismd.model.ImageData;
import com.sismd.service.ImageProcessingService;

import java.util.function.IntUnaryOperator;

/**
 * Histogram equalization applied pixel-by-pixel on the calling thread.
 *
 * The three algorithm stages are {@code protected} so that parallel
 * sub-classes (ManualThread, ThreadPool, ForkJoin, CompletableFuture)
 * can override individual stages while reusing the rest.
 */
public class SequentialImageProcessingService implements ImageProcessingService {

    @Override
    public ImageData process(ImageData input) {
        var w = input.getWidth();
        var h = input.getHeight();
        var totalPixels = w * h;

        var hist = buildHistogram(input, w, h);
        var cumulative = HistogramUtils.buildCumulativeHistogram(hist);

        // Stage 3 — apply equalization via functional pixel mapper
        IntUnaryOperator eq = HistogramUtils.equalizer(cumulative, totalPixels);
        var out = new int[totalPixels];
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                out[x * h + y] = eq.applyAsInt(input.getPixel(x, y));

        return ImageData.of(out, w, h);
    }

    // ── algorithm stages (protected — override in parallel implementations)
    // ───────

    protected int[] buildHistogram(ImageData img, int w, int h) {
        var hist = new int[256];
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                hist[HistogramUtils.luminosity(img.getPixel(x, y))]++;
        return hist;
    }
}
