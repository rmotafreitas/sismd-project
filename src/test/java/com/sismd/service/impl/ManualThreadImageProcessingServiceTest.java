package com.sismd.service.impl;

import com.sismd.model.ImageData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ManualThreadImageProcessingServiceTest {

    // ── output contract
    // ───────────────────────────────────────────────────────────

    @Test
    void process_preservesDimensions() {
        // Arrange
        ImageData input = TestImageFactory.createSmall();

        // Act
        ImageData output = new ManualThreadImageProcessingService().process(input);

        // Assert
        assertThat(output.getWidth()).isEqualTo(input.getWidth());
        assertThat(output.getHeight()).isEqualTo(input.getHeight());
    }

    @Test
    void process_allOutputPixelsAreGrayscale() {
        // Arrange
        ImageData input = TestImageFactory.createMedium();

        // Act
        ImageData output = new ManualThreadImageProcessingService().process(input);

        // Assert
        for (int x = 0; x < output.getWidth(); x++)
            for (int y = 0; y < output.getHeight(); y++) {
                int px = output.getPixel(x, y);
                assertThat((px >> 16) & 0xFF).isEqualTo((px >> 8) & 0xFF).isEqualTo(px & 0xFF);
            }
    }

    @Test
    void process_allOutputValuesInValidRange() {
        // Arrange
        ImageData input = TestImageFactory.createMedium();

        // Act
        ImageData output = new ManualThreadImageProcessingService().process(input);

        // Assert
        for (int x = 0; x < output.getWidth(); x++)
            for (int y = 0; y < output.getHeight(); y++)
                assertThat((output.getPixel(x, y) >> 16) & 0xFF).isBetween(0, 255);
    }

    // ── correctness vs sequential baseline ───────────────────────────────────────

    @Test
    void process_matchesSequentialBaseline() {
        // Arrange
        ImageData input = TestImageFactory.createMedium();
        ImageData expected = new SequentialImageProcessingService().process(input);

        // Act
        ImageData actual = new ManualThreadImageProcessingService().process(input);

        // Assert
        assertPixelEqual(expected, actual);
    }

    // ── thread count variation
    // ────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(ints = { 1, 2, 4, 8, 16 })
    void process_consistentAcrossThreadCounts(int n) {
        // Arrange
        ImageData input = TestImageFactory.createSmall();
        ImageData expected = new SequentialImageProcessingService().process(input);

        // Act
        ImageData actual = new ManualThreadImageProcessingService(n).process(input);

        // Assert
        assertPixelEqual(expected, actual);
    }

    // ── edge cases
    // ────────────────────────────────────────────────────────────────

    @Test
    void process_singlePixel_doesNotThrow() {
        // Arrange
        int[] px = { 0x0000FF }; // blue
        ImageData input = ImageData.of(px, 1, 1);

        // Act
        ImageData result = new ManualThreadImageProcessingService().process(input);

        // Assert
        assertThat(result).isNotNull();
    }

    @Test
    void process_moreThreadsThanColumns_correctResult() {
        // Arrange — 3 cols, 32 threads (capped at image width internally)
        ImageData input = TestImageFactory.createRandom(3, 50, 7L);
        ImageData expected = new SequentialImageProcessingService().process(input);

        // Act
        ImageData actual = new ManualThreadImageProcessingService(32).process(input);

        // Assert
        assertPixelEqual(expected, actual);
    }

    @Test
    void process_uniformImage_allPixelsSame() {
        // Arrange
        int[] pixels = new int[4 * 4];
        Arrays.fill(pixels, (100 << 16) | (100 << 8) | 100);
        ImageData input = ImageData.of(pixels, 4, 4);

        // Act
        ImageData output = new ManualThreadImageProcessingService().process(input);

        // Assert
        int first = (output.getPixel(0, 0) >> 16) & 0xFF;
        for (int x = 0; x < 4; x++)
            for (int y = 0; y < 4; y++)
                assertThat((output.getPixel(x, y) >> 16) & 0xFF).isEqualTo(first);
    }

    @Test
    void constructor_rejectsZeroThreads() {
        // Arrange & Act & Assert
        assertThatThrownBy(() -> new ManualThreadImageProcessingService(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── concurrency stress
    // ────────────────────────────────────────────────────────

    @Test
    void process_noRaceConditions_repeatedExecution() {
        // Arrange
        ImageData input = TestImageFactory.createMedium();
        ImageData expected = new SequentialImageProcessingService().process(input);
        var svc = new ManualThreadImageProcessingService(8);

        // Act & Assert — 20 iterations to stress concurrency
        for (int i = 0; i < 20; i++)
            assertPixelEqual(expected, svc.process(input));
    }

    // ── helpers
    // ───────────────────────────────────────────────────────────────────

    private static void assertPixelEqual(ImageData expected, ImageData actual) {
        assertThat(actual.getWidth()).isEqualTo(expected.getWidth());
        assertThat(actual.getHeight()).isEqualTo(expected.getHeight());
        for (int x = 0; x < expected.getWidth(); x++)
            for (int y = 0; y < expected.getHeight(); y++)
                assertThat(actual.getPixel(x, y))
                        .describedAs("pixel (%d, %d)", x, y)
                        .isEqualTo(expected.getPixel(x, y));
    }
}
