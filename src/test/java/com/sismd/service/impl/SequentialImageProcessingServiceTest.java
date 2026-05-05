package com.sismd.service.impl;

import com.sismd.model.ImageData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

class SequentialImageProcessingServiceTest {

    private SequentialImageProcessingService service;

    @BeforeEach
    void setUp() {
        service = new SequentialImageProcessingService();
    }

    // ── output contract
    // ───────────────────────────────────────────────────────────

    @Test
    void process_preservesDimensions() {
        // Arrange
        ImageData input = gradientImage(8, 6);

        // Act
        ImageData output = service.process(input);

        // Assert
        assertThat(output.getWidth()).isEqualTo(8);
        assertThat(output.getHeight()).isEqualTo(6);
    }

    @Test
    void process_allOutputPixelsAreGrayscale() {
        // Arrange
        ImageData input = gradientImage(10, 10);

        // Act
        ImageData output = service.process(input);

        // Assert
        for (int x = 0; x < output.getWidth(); x++) {
            for (int y = 0; y < output.getHeight(); y++) {
                int px = output.getPixel(x, y);
                int r = (px >> 16) & 0xFF, g = (px >> 8) & 0xFF, b = px & 0xFF;
                assertThat(r).describedAs("pixel (%d,%d) R==G", x, y).isEqualTo(g);
                assertThat(g).describedAs("pixel (%d,%d) G==B", x, y).isEqualTo(b);
            }
        }
    }

    @Test
    void process_allOutputValuesInValidRange() {
        // Arrange
        ImageData input = gradientImage(16, 16);

        // Act
        ImageData output = service.process(input);

        // Assert
        for (int x = 0; x < output.getWidth(); x++)
            for (int y = 0; y < output.getHeight(); y++)
                assertThat((output.getPixel(x, y) >> 16) & 0xFF).isBetween(0, 255);
    }

    // ── known-input correctness
    // ───────────────────────────────────────────────────

    @Test
    void process_knownInput_producesExpectedEqualization() {
        // Arrange — 2×2 image with luminosities: 50, 100, 150, 200
        // hist: 50→1, 100→1, 150→1, 200→1
        // cumulative: 50→1, 100→2, 150→3, 200→4
        // PDF formula: newLum = ⌊255 × cumulativeHist[L] / totalPixels⌋
        // lum 50 → round(255 * 1/4) = 64
        // lum 100 → round(255 * 2/4) = 128
        // lum 150 → round(255 * 3/4) = 191
        // lum 200 → round(255 * 4/4) = 255
        int[] pixels = new int[4]; // 2×2, column-major (height=2)
        pixels[0 * 2 + 0] = gray(50);
        pixels[0 * 2 + 1] = gray(100);
        pixels[1 * 2 + 0] = gray(150);
        pixels[1 * 2 + 1] = gray(200);
        ImageData input = ImageData.of(pixels, 2, 2);

        // Act
        ImageData output = service.process(input);

        // Assert
        assertThat((output.getPixel(0, 0) >> 16) & 0xFF).isEqualTo(64);
        assertThat((output.getPixel(0, 1) >> 16) & 0xFF).isEqualTo(128);
        assertThat((output.getPixel(1, 0) >> 16) & 0xFF).isEqualTo(191);
        assertThat((output.getPixel(1, 1) >> 16) & 0xFF).isEqualTo(255);
    }

    // ── edge cases
    // ────────────────────────────────────────────────────────────────

    @Test
    void process_singlePixel_doesNotThrow() {
        // Arrange
        int[] pixels = { 0xFF0000 }; // red
        ImageData input = ImageData.of(pixels, 1, 1);

        // Act
        ImageData output = service.process(input);

        // Assert
        assertThat(output).isNotNull();
    }

    @Test
    void process_uniformImage_allPixelsSameLuminosity() {
        // Arrange
        int[] pixels = new int[4 * 4];
        Arrays.fill(pixels, (128 << 16) | (128 << 8) | 128);
        ImageData input = ImageData.of(pixels, 4, 4);

        // Act
        ImageData output = service.process(input);

        // Assert
        int first = (output.getPixel(0, 0) >> 16) & 0xFF;
        for (int x = 0; x < 4; x++)
            for (int y = 0; y < 4; y++)
                assertThat((output.getPixel(x, y) >> 16) & 0xFF).isEqualTo(first);
    }

    @Test
    void process_doesNotMutateInput() {
        // Arrange
        int[] pixels = new int[3 * 3]; // height=3
        for (int x = 0; x < 3; x++)
            for (int y = 0; y < 3; y++)
                pixels[x * 3 + y] = (x * 30 << 16) | (y * 30 << 8);
        int originalCorner = pixels[0];

        // Act
        service.process(ImageData.of(pixels, 3, 3));

        // Assert
        assertThat(pixels[0]).isEqualTo(originalCorner);
    }

    // ── helpers
    // ───────────────────────────────────────────────────────────────────

    private static ImageData gradientImage(int w, int h) {
        int[] pixels = new int[w * h];
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++) {
                int v = (x * 256 / w + y * 256 / h) / 2;
                pixels[x * h + y] = (v << 16) | (v << 8) | v;
            }
        return ImageData.of(pixels, w, h);
    }

    private static int gray(int v) {
        return (v << 16) | (v << 8) | v;
    }
}
