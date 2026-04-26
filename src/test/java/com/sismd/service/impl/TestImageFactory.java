package com.sismd.service.impl;

import com.sismd.model.ImageData;

import java.awt.Color;
import java.util.Random;

/**
 * Reproducible test images for histogram equalization tests.
 * All factory methods with a {@code seed} produce the same image across runs.
 */
public final class TestImageFactory {

    private TestImageFactory() {}

    /** Every pixel has the same color. */
    public static ImageData createUniform(int width, int height, Color color) {
        Color[][] pixels = new Color[width][height];
        for (int x = 0; x < width; x++)
            for (int y = 0; y < height; y++)
                pixels[x][y] = color;
        return ImageData.of(pixels, width, height);
    }

    /** Brightness increases left-to-right, top-to-bottom (full 0-255 range). */
    public static ImageData createGradient(int width, int height) {
        Color[][] pixels = new Color[width][height];
        for (int x = 0; x < width; x++)
            for (int y = 0; y < height; y++) {
                int v = (int) ((x / (double) (width - 1) + y / (double) (height - 1)) / 2.0 * 255);
                v = Math.max(0, Math.min(255, v));
                pixels[x][y] = new Color(v, (255 - v) % 256, (v * 2) % 256);
            }
        return ImageData.of(pixels, width, height);
    }

    /** Seeded random pixels — same seed → same image every run. */
    public static ImageData createRandom(int width, int height, long seed) {
        Random rng = new Random(seed);
        Color[][] pixels = new Color[width][height];
        for (int x = 0; x < width; x++)
            for (int y = 0; y < height; y++)
                pixels[x][y] = new Color(rng.nextInt(256), rng.nextInt(256), rng.nextInt(256));
        return ImageData.of(pixels, width, height);
    }

    public static ImageData createSmall()  { return createRandom(10,  10,  42L); }
    public static ImageData createMedium() { return createRandom(200, 200, 42L); }
    public static ImageData createLarge()  { return createRandom(800, 800, 42L); }
}
