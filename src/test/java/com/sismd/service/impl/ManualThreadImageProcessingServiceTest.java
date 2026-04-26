package com.sismd.service.impl;

import com.sismd.model.ImageData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManualThreadImageProcessingServiceTest {

    // ── output contract ───────────────────────────────────────────────────────────

    @Test
    void process_preservesDimensions() {
        ImageData input  = TestImageFactory.createSmall();
        ImageData output = new ManualThreadImageProcessingService().process(input);
        assertThat(output.getWidth()).isEqualTo(input.getWidth());
        assertThat(output.getHeight()).isEqualTo(input.getHeight());
    }

    @Test
    void process_allOutputPixelsAreGrayscale() {
        ImageData output = new ManualThreadImageProcessingService().process(TestImageFactory.createMedium());
        for (int x = 0; x < output.getWidth(); x++)
            for (int y = 0; y < output.getHeight(); y++) {
                Color c = output.getPixel(x, y);
                assertThat(c.getRed()).isEqualTo(c.getGreen()).isEqualTo(c.getBlue());
            }
    }

    @Test
    void process_allOutputValuesInValidRange() {
        ImageData output = new ManualThreadImageProcessingService().process(TestImageFactory.createMedium());
        for (int x = 0; x < output.getWidth(); x++)
            for (int y = 0; y < output.getHeight(); y++)
                assertThat(output.getPixel(x, y).getRed()).isBetween(0, 255);
    }

    // ── correctness vs sequential baseline ───────────────────────────────────────

    @Test
    void process_matchesSequentialBaseline() {
        ImageData input    = TestImageFactory.createMedium();
        ImageData expected = new SequentialImageProcessingService().process(input);
        ImageData actual   = new ManualThreadImageProcessingService().process(input);
        assertPixelEqual(expected, actual);
    }

    // ── thread count variation ────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8, 16})
    void process_consistentAcrossThreadCounts(int n) {
        ImageData input    = TestImageFactory.createSmall();
        ImageData expected = new SequentialImageProcessingService().process(input);
        ImageData actual   = new ManualThreadImageProcessingService(n).process(input);
        assertPixelEqual(expected, actual);
    }

    // ── edge cases ────────────────────────────────────────────────────────────────

    @Test
    void process_singlePixel_doesNotThrow() {
        Color[][] px = { { Color.BLUE } };
        ImageData result = new ManualThreadImageProcessingService().process(ImageData.of(px, 1, 1));
        assertThat(result).isNotNull();
    }

    @Test
    void process_moreThreadsThanColumns_correctResult() {
        // 3 cols, 32 threads — threads are capped at image width internally
        ImageData input    = TestImageFactory.createRandom(3, 50, 7L);
        ImageData expected = new SequentialImageProcessingService().process(input);
        ImageData actual   = new ManualThreadImageProcessingService(32).process(input);
        assertPixelEqual(expected, actual);
    }

    @Test
    void process_uniformImage_allPixelsSame() {
        Color[][] pixels = new Color[4][4];
        for (int x = 0; x < 4; x++)
            for (int y = 0; y < 4; y++)
                pixels[x][y] = new Color(100, 100, 100);
        ImageData output = new ManualThreadImageProcessingService().process(ImageData.of(pixels, 4, 4));
        int first = output.getPixel(0, 0).getRed();
        for (int x = 0; x < 4; x++)
            for (int y = 0; y < 4; y++)
                assertThat(output.getPixel(x, y).getRed()).isEqualTo(first);
    }

    @Test
    void constructor_rejectsZeroThreads() {
        assertThatThrownBy(() -> new ManualThreadImageProcessingService(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── concurrency stress ────────────────────────────────────────────────────────

    @Test
    void process_noRaceConditions_repeatedExecution() {
        ImageData input    = TestImageFactory.createMedium();
        ImageData expected = new SequentialImageProcessingService().process(input);
        ManualThreadImageProcessingService svc = new ManualThreadImageProcessingService(8);
        for (int i = 0; i < 20; i++)
            assertPixelEqual(expected, svc.process(input));
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    private static void assertPixelEqual(ImageData expected, ImageData actual) {
        assertThat(actual.getWidth()).isEqualTo(expected.getWidth());
        assertThat(actual.getHeight()).isEqualTo(expected.getHeight());
        for (int x = 0; x < expected.getWidth(); x++)
            for (int y = 0; y < expected.getHeight(); y++)
                assertThat(actual.getPixel(x, y).getRGB())
                        .describedAs("pixel (%d, %d)", x, y)
                        .isEqualTo(expected.getPixel(x, y).getRGB());
    }
}
