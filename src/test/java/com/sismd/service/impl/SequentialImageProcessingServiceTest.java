package com.sismd.service.impl;

import com.sismd.model.ImageData;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;

class SequentialImageProcessingServiceTest {

    private SequentialImageProcessingService service;

    @BeforeEach
    void setUp() {
        service = new SequentialImageProcessingService();
    }

    // ── output contract ───────────────────────────────────────────────────────────

    @Test
    void process_preservesDimensions() {
        ImageData input = gradientImage(8, 6);
        ImageData output = service.process(input);

        assertThat(output.getWidth()).isEqualTo(8);
        assertThat(output.getHeight()).isEqualTo(6);
    }

    @Test
    void process_allOutputPixelsAreGrayscale() {
        ImageData output = service.process(gradientImage(10, 10));

        for (int x = 0; x < output.getWidth(); x++) {
            for (int y = 0; y < output.getHeight(); y++) {
                Color c = output.getPixel(x, y);
                assertThat(c.getRed())
                        .describedAs("pixel (%d,%d) R==G", x, y)
                        .isEqualTo(c.getGreen());
                assertThat(c.getGreen())
                        .describedAs("pixel (%d,%d) G==B", x, y)
                        .isEqualTo(c.getBlue());
            }
        }
    }

    @Test
    void process_allOutputValuesInValidRange() {
        ImageData output = service.process(gradientImage(16, 16));

        for (int x = 0; x < output.getWidth(); x++) {
            for (int y = 0; y < output.getHeight(); y++) {
                int v = output.getPixel(x, y).getRed();
                assertThat(v).isBetween(0, 255);
            }
        }
    }

    // ── known-input correctness ───────────────────────────────────────────────────

    @Test
    void process_knownInput_producesExpectedEqualization() {
        // 2×2 image with luminosities: 50, 100, 150, 200
        // hist:       50→1, 100→1, 150→1, 200→1
        // cumulative: 50→1, 100→2, 150→3, 200→4    cdfMin = 1
        // equalize formula: round(255 * cum / (total - cdfMin))
        //   lum 50  → round(255 * 1/3) = 85
        //   lum 100 → round(255 * 2/3) = 170
        //   lum 150 → round(255 * 3/3) = 255
        //   lum 200 → clamp(round(255 * 4/3), 0, 255) = 255
        Color[][] pixels = {
            { gray(50),  gray(100) },
            { gray(150), gray(200) }
        };
        ImageData input  = ImageData.of(pixels, 2, 2);
        ImageData output = service.process(input);

        assertThat(output.getPixel(0, 0).getRed()).isEqualTo(85);
        assertThat(output.getPixel(0, 1).getRed()).isEqualTo(170);
        assertThat(output.getPixel(1, 0).getRed()).isEqualTo(255);
        assertThat(output.getPixel(1, 1).getRed()).isEqualTo(255);
    }

    // ── edge cases ────────────────────────────────────────────────────────────────

    @Test
    void process_singlePixel_doesNotThrow() {
        Color[][] pixels = { { Color.RED } };
        ImageData input = ImageData.of(pixels, 1, 1);
        assertThat(service.process(input)).isNotNull();
    }

    @Test
    void process_uniformImage_allPixelsSameLuminosity() {
        // All pixels identical → equalization still produces a valid uniform output
        Color[][] pixels = new Color[4][4];
        for (int x = 0; x < 4; x++)
            for (int y = 0; y < 4; y++)
                pixels[x][y] = new Color(128, 128, 128);

        ImageData output = service.process(ImageData.of(pixels, 4, 4));

        int first = output.getPixel(0, 0).getRed();
        for (int x = 0; x < 4; x++)
            for (int y = 0; y < 4; y++)
                assertThat(output.getPixel(x, y).getRed()).isEqualTo(first);
    }

    @Test
    void process_doesNotMutateInput() {
        Color[][] pixels = new Color[3][3];
        for (int x = 0; x < 3; x++)
            for (int y = 0; y < 3; y++)
                pixels[x][y] = new Color(x * 30, y * 30, 0);

        Color originalCorner = pixels[0][0];
        service.process(ImageData.of(pixels, 3, 3));

        assertThat(pixels[0][0]).isEqualTo(originalCorner);
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    private static ImageData gradientImage(int w, int h) {
        Color[][] pixels = new Color[w][h];
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++) {
                int v = (x * 256 / w + y * 256 / h) / 2;
                pixels[x][y] = new Color(v, v, v);
            }
        return ImageData.of(pixels, w, h);
    }

    private static Color gray(int v) {
        return new Color(v, v, v);
    }
}
