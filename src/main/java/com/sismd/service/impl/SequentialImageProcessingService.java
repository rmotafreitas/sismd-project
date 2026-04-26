package com.sismd.service.impl;

import com.sismd.model.ImageData;
import com.sismd.service.ImageProcessingService;

import java.awt.Color;

/**
 * Histogram equalization applied pixel-by-pixel on the calling thread.
 * Replace with ParallelImageProcessingService (ForkJoin / parallel streams)
 * to keep the same contract while distributing work across cores.
 */
public class SequentialImageProcessingService implements ImageProcessingService {

    @Override
    public ImageData process(ImageData input) {
        int w = input.getWidth(), h = input.getHeight();
        int totalPixels = w * h;

        int[] hist       = buildHistogram(input, w, h);
        int[] cumulative = buildCumulativeHistogram(hist);
        int   cdfMin     = firstNonZero(cumulative);

        Color[][] out = new Color[w][h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int lum    = luminosity(input.getPixel(x, y));
                int newLum = equalize(cumulative[lum], totalPixels, cdfMin);
                out[x][y]  = new Color(newLum, newLum, newLum);
            }
        }

        return ImageData.of(out, w, h);
    }

    // --- algorithm helpers -------------------------------------------------------

    private static int[] buildHistogram(ImageData img, int w, int h) {
        int[] hist = new int[256];
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                hist[luminosity(img.getPixel(x, y))]++;
        return hist;
    }

    private static int[] buildCumulativeHistogram(int[] hist) {
        int[] cum = new int[256];
        cum[0] = hist[0];
        for (int i = 1; i < 256; i++) cum[i] = cum[i - 1] + hist[i];
        return cum;
    }

    private static int firstNonZero(int[] arr) {
        for (int v : arr) if (v != 0) return v;
        return 0;
    }

    private static int equalize(int cdf, int total, int cdfMin) {
        int val = (int) Math.round(255.0 * cdf / (double) (total - cdfMin));
        return Math.max(0, Math.min(255, val));
    }

    private static int luminosity(Color c) {
        return (int) Math.round(0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue());
    }
}
