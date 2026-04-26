package com.sismd.service.impl;

import com.sismd.model.ImageData;
import com.sismd.service.ImageProcessingService;

import java.awt.Color;

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
        int w = input.getWidth(), h = input.getHeight();
        int totalPixels = w * h;

        int[] hist       = buildHistogram(input, w, h);
        int[] cumulative = HistogramUtils.buildCumulativeHistogram(hist);
        int   cdfMin     = HistogramUtils.firstNonZero(cumulative);

        Color[][] out = new Color[w][h];
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++) {
                int lum   = HistogramUtils.luminosity(input.getPixel(x, y));
                int newLum = HistogramUtils.equalize(cumulative[lum], totalPixels, cdfMin);
                out[x][y] = new Color(newLum, newLum, newLum);
            }

        return ImageData.of(out, w, h);
    }

    // ── algorithm stages (protected — override in parallel implementations) ───────

    protected int[] buildHistogram(ImageData img, int w, int h) {
        int[] hist = new int[256];
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                hist[HistogramUtils.luminosity(img.getPixel(x, y))]++;
        return hist;
    }
}
