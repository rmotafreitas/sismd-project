package com.sismd.service.impl;

import java.awt.Color;

/**
 * Pure-function helpers shared by all histogram equalization implementations
 * (sequential, manual threads, thread pool, fork/join, completable-future).
 *
 * Every method is stateless and thread-safe.
 */
public final class HistogramUtils {

    private HistogramUtils() {}

    /**
     * Perceived luminosity of a pixel using the ITU-R BT.601 coefficients.
     * Result is in [0, 255].
     */
    public static int luminosity(Color c) {
        return (int) Math.round(0.299 * c.getRed() + 0.587 * c.getGreen() + 0.114 * c.getBlue());
    }

    /**
     * Builds the cumulative distribution from a luminosity histogram.
     * The last element equals the total pixel count.
     */
    public static int[] buildCumulativeHistogram(int[] hist) {
        int[] cum = new int[256];
        cum[0] = hist[0];
        for (int i = 1; i < 256; i++) cum[i] = cum[i - 1] + hist[i];
        return cum;
    }

    /** Returns the first non-zero value in an array, or 0 if all are zero. */
    public static int firstNonZero(int[] arr) {
        for (int v : arr) if (v != 0) return v;
        return 0;
    }

    /**
     * Applies the standard histogram equalization formula to a single CDF value.
     * Result is clamped to [0, 255].
     *
     * @param cdf      cumulative frequency at this luminosity level
     * @param total    total number of pixels in the image
     * @param cdfMin   minimum non-zero CDF value (used for normalization)
     */
    public static int equalize(int cdf, int total, int cdfMin) {
        int val = (int) Math.round(255.0 * cdf / (double) (total - cdfMin));
        return Math.max(0, Math.min(255, val));
    }
}
