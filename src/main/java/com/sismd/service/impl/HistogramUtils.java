package com.sismd.service.impl;

import java.util.function.IntUnaryOperator;
import java.util.stream.IntStream;

/**
 * Pure-function helpers shared by all histogram equalization implementations
 * (sequential, manual threads, thread pool, fork/join, completable-future).
 *
 * Every method is stateless and thread-safe.
 */
public final class HistogramUtils {

    private HistogramUtils() {
    }

    /** ITU-R BT.601 luminosity coefficients. */
    private static final double LUMA_R = 0.299, LUMA_G = 0.587, LUMA_B = 0.114;

    /**
     * Perceived luminosity from a packed RGB int using ITU-R BT.601 coefficients.
     * Result is in [0, 255].
     */
    public static int luminosity(int packedRgb) {
        int r = (packedRgb >> 16) & 0xFF;
        int g = (packedRgb >> 8) & 0xFF;
        int b = packedRgb & 0xFF;
        return (int) Math.round(LUMA_R * r + LUMA_G * g + LUMA_B * b);
    }

    /** Packs a grayscale luminosity value into a packed RGB int (R=G=B=lum). */
    public static int grayPixel(int lum) {
        return (lum << 16) | (lum << 8) | lum;
    }

    /**
     * Returns an {@link IntUnaryOperator} that maps a packed RGB pixel to its
     * equalized grayscale value using the supplied cumulative histogram.
     * Suitable for passing to parallel streams or lambda-based pipelines.
     */
    public static IntUnaryOperator equalizer(int[] cumulative, int totalPixels) {
        return packedRgb -> {
            int lum = luminosity(packedRgb);
            int newLum = equalize(cumulative[lum], totalPixels);
            return grayPixel(newLum);
        };
    }

    /**
     * Builds the cumulative distribution from a luminosity histogram.
     * The last element equals the total pixel count.
     */
    public static int[] buildCumulativeHistogram(int[] hist) {
        // prefix-sum via simple loop (256 elements — sequential is optimal)
        int[] cum = new int[256];
        cum[0] = hist[0];
        for (int i = 1; i < 256; i++)
            cum[i] = cum[i - 1] + hist[i];
        return cum;
    }

    /** Element-wise sum of two 256-bucket histograms into a new array. */
    public static int[] mergeHistograms(int[] a, int[] b) {
        return IntStream.range(0, 256)
                .map(i -> a[i] + b[i])
                .toArray();
    }

    /**
     * Applies the histogram equalization formula from the project specification:
     * 
     * <pre>
     * newLuminosity = ⌊255 × cumulativeHist[L] / totalPixels⌋
     * </pre>
     * 
     * Result is clamped to [0, 255].
     *
     * @param cdf   cumulative frequency at this luminosity level
     * @param total total number of pixels in the image
     */
    public static int equalize(int cdf, int total) {
        int val = (int) Math.round(255.0 * cdf / (double) total);
        return Math.max(0, Math.min(255, val));
    }
}
