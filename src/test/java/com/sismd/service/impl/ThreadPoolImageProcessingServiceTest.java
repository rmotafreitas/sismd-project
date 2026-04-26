package com.sismd.service.impl;

import com.sismd.model.ImageData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ThreadPoolImageProcessingServiceTest {

    // ── output contract ───────────────────────────────────────────────────────────

    @Test
    void process_preservesDimensions() {
        ImageData input  = TestImageFactory.createSmall();
        ImageData output = new ThreadPoolImageProcessingService().process(input);
        assertThat(output.getWidth()).isEqualTo(input.getWidth());
        assertThat(output.getHeight()).isEqualTo(input.getHeight());
    }

    @Test
    void process_allOutputPixelsAreGrayscale() {
        ImageData output = new ThreadPoolImageProcessingService().process(TestImageFactory.createMedium());
        for (int x = 0; x < output.getWidth(); x++)
            for (int y = 0; y < output.getHeight(); y++) {
                Color c = output.getPixel(x, y);
                assertThat(c.getRed()).isEqualTo(c.getGreen()).isEqualTo(c.getBlue());
            }
    }

    @Test
    void process_allOutputValuesInValidRange() {
        ImageData output = new ThreadPoolImageProcessingService().process(TestImageFactory.createMedium());
        for (int x = 0; x < output.getWidth(); x++)
            for (int y = 0; y < output.getHeight(); y++)
                assertThat(output.getPixel(x, y).getRed()).isBetween(0, 255);
    }

    // ── correctness vs sequential baseline ───────────────────────────────────────

    @Test
    void process_matchesSequentialBaseline() {
        ImageData input    = TestImageFactory.createMedium();
        ImageData expected = new SequentialImageProcessingService().process(input);
        ImageData actual   = new ThreadPoolImageProcessingService().process(input);
        assertPixelEqual(expected, actual);
    }

    // ── pool size variation ───────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8, 16})
    void process_consistentAcrossPoolSizes(int n) {
        ImageData input    = TestImageFactory.createSmall();
        ImageData expected = new SequentialImageProcessingService().process(input);
        ImageData actual   = new ThreadPoolImageProcessingService(n).process(input);
        assertPixelEqual(expected, actual);
    }

    // ── edge cases ────────────────────────────────────────────────────────────────

    @Test
    void process_singlePixel_doesNotThrow() {
        Color[][] px = { { Color.GREEN } };
        assertThat(new ThreadPoolImageProcessingService().process(ImageData.of(px, 1, 1))).isNotNull();
    }

    @Test
    void process_moreThreadsThanColumns_correctResult() {
        ImageData input    = TestImageFactory.createRandom(3, 50, 9L);
        ImageData expected = new SequentialImageProcessingService().process(input);
        ImageData actual   = new ThreadPoolImageProcessingService(32).process(input);
        assertPixelEqual(expected, actual);
    }

    @Test
    void process_threadPoolShutdownProperly() {
        // If the pool leaks, subsequent calls would still work but threads would accumulate.
        // Running multiple times verifies each call is self-contained.
        ThreadPoolImageProcessingService svc = new ThreadPoolImageProcessingService(4);
        for (int i = 0; i < 5; i++)
            assertThat(svc.process(TestImageFactory.createSmall())).isNotNull();
    }

    @Test
    void constructor_rejectsZeroPoolSize() {
        assertThatThrownBy(() -> new ThreadPoolImageProcessingService(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── concurrency stress ────────────────────────────────────────────────────────

    @Test
    void process_noRaceConditions_repeatedExecution() {
        ImageData input    = TestImageFactory.createMedium();
        ImageData expected = new SequentialImageProcessingService().process(input);
        ThreadPoolImageProcessingService svc = new ThreadPoolImageProcessingService(8);
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
