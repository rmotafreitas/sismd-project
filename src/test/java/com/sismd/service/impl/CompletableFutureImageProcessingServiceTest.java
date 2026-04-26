package com.sismd.service.impl;

import com.sismd.model.ImageData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CompletableFutureImageProcessingServiceTest {

    // ── output contract ───────────────────────────────────────────────────────────

    @Test
    void process_preservesDimensions() {
        ImageData input  = TestImageFactory.createSmall();
        ImageData output = new CompletableFutureImageProcessingService().process(input);
        assertThat(output.getWidth()).isEqualTo(input.getWidth());
        assertThat(output.getHeight()).isEqualTo(input.getHeight());
    }

    @Test
    void process_allOutputPixelsAreGrayscale() {
        ImageData output = new CompletableFutureImageProcessingService().process(TestImageFactory.createMedium());
        for (int x = 0; x < output.getWidth(); x++)
            for (int y = 0; y < output.getHeight(); y++) {
                Color c = output.getPixel(x, y);
                assertThat(c.getRed()).isEqualTo(c.getGreen()).isEqualTo(c.getBlue());
            }
    }

    @Test
    void process_allOutputValuesInValidRange() {
        ImageData output = new CompletableFutureImageProcessingService().process(TestImageFactory.createMedium());
        for (int x = 0; x < output.getWidth(); x++)
            for (int y = 0; y < output.getHeight(); y++)
                assertThat(output.getPixel(x, y).getRed()).isBetween(0, 255);
    }

    // ── correctness vs sequential baseline ───────────────────────────────────────

    @Test
    void process_matchesSequentialBaseline() {
        ImageData input    = TestImageFactory.createMedium();
        ImageData expected = new SequentialImageProcessingService().process(input);
        ImageData actual   = new CompletableFutureImageProcessingService().process(input);
        assertPixelEqual(expected, actual);
    }

    // ── partition count variation ─────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(ints = {1, 2, 4, 8, 16})
    void process_consistentAcrossPartitionCounts(int n) {
        ImageData input    = TestImageFactory.createSmall();
        ImageData expected = new SequentialImageProcessingService().process(input);
        ImageData actual   = new CompletableFutureImageProcessingService(n).process(input);
        assertPixelEqual(expected, actual);
    }

    // ── edge cases ────────────────────────────────────────────────────────────────

    @Test
    void process_singlePixel_doesNotThrow() {
        Color[][] px = { { Color.CYAN } };
        assertThat(new CompletableFutureImageProcessingService().process(ImageData.of(px, 1, 1))).isNotNull();
    }

    @Test
    void process_morePartitionsThanColumns_correctResult() {
        ImageData input    = TestImageFactory.createRandom(3, 50, 11L);
        ImageData expected = new SequentialImageProcessingService().process(input);
        ImageData actual   = new CompletableFutureImageProcessingService(32).process(input);
        assertPixelEqual(expected, actual);
    }

    @Test
    void process_pipelineCompletesWithoutBlocking() {
        // Verify process() returns and doesn't hang (executor is properly shut down)
        CompletableFutureImageProcessingService svc = new CompletableFutureImageProcessingService(4);
        for (int i = 0; i < 5; i++)
            assertThat(svc.process(TestImageFactory.createSmall())).isNotNull();
    }

    @Test
    void constructor_rejectsZeroPartitions() {
        assertThatThrownBy(() -> new CompletableFutureImageProcessingService(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── concurrency stress ────────────────────────────────────────────────────────

    @Test
    void process_noRaceConditions_repeatedExecution() {
        ImageData input    = TestImageFactory.createMedium();
        ImageData expected = new SequentialImageProcessingService().process(input);
        CompletableFutureImageProcessingService svc = new CompletableFutureImageProcessingService(8);
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
