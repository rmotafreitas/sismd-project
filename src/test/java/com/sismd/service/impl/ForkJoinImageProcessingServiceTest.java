package com.sismd.service.impl;

import com.sismd.model.ImageData;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ForkJoinImageProcessingServiceTest {

    // ── output contract
    // ───────────────────────────────────────────────────────────

    @Test
    void process_preservesDimensions() {
        // Arrange
        ImageData input = TestImageFactory.createSmall();

        // Act
        ImageData output = new ForkJoinImageProcessingService().process(input);

        // Assert
        assertThat(output.getWidth()).isEqualTo(input.getWidth());
        assertThat(output.getHeight()).isEqualTo(input.getHeight());
    }

    @Test
    void process_allOutputPixelsAreGrayscale() {
        // Arrange
        ImageData input = TestImageFactory.createMedium();

        // Act
        ImageData output = new ForkJoinImageProcessingService().process(input);

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
        ImageData output = new ForkJoinImageProcessingService().process(input);

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
        ImageData actual = new ForkJoinImageProcessingService().process(input);

        // Assert
        assertPixelEqual(expected, actual);
    }

    // ── threshold variation
    // ───────────────────────────────────────────────────────

    @ParameterizedTest
    @ValueSource(ints = { 1, 5, 10, 50, 500 })
    void process_consistentAcrossThresholds(int threshold) {
        // Arrange
        ImageData input = TestImageFactory.createMedium();
        ImageData expected = new SequentialImageProcessingService().process(input);

        // Act
        ImageData actual = new ForkJoinImageProcessingService(threshold).process(input);

        // Assert
        assertPixelEqual(expected, actual);
    }

    // ── edge cases
    // ────────────────────────────────────────────────────────────────

    @Test
    void process_singlePixel_doesNotThrow() {
        // Arrange
        int[] px = { 0xFFC800 }; // orange
        ImageData input = ImageData.of(px, 1, 1);

        // Act
        ImageData result = new ForkJoinImageProcessingService().process(input);

        // Assert
        assertThat(result).isNotNull();
    }

    @Test
    void process_thresholdOne_forcesLeafPerColumn() {
        // Arrange — threshold=1 forces every column into its own leaf task
        ImageData input = TestImageFactory.createSmall();
        ImageData expected = new SequentialImageProcessingService().process(input);

        // Act
        ImageData actual = new ForkJoinImageProcessingService(1).process(input);

        // Assert
        assertPixelEqual(expected, actual);
    }

    @Test
    void process_largeThreshold_noSplitting() {
        // Arrange — threshold > image width means no recursion
        ImageData input = TestImageFactory.createSmall();
        ImageData expected = new SequentialImageProcessingService().process(input);

        // Act
        ImageData actual = new ForkJoinImageProcessingService(10_000).process(input);

        // Assert
        assertPixelEqual(expected, actual);
    }

    @Test
    void constructor_rejectsZeroThreshold() {
        // Arrange & Act & Assert
        assertThatThrownBy(() -> new ForkJoinImageProcessingService(0))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ── concurrency stress
    // ────────────────────────────────────────────────────────

    @Test
    void process_noRaceConditions_repeatedExecution() {
        // Arrange
        ImageData input = TestImageFactory.createMedium();
        ImageData expected = new SequentialImageProcessingService().process(input);
        var svc = new ForkJoinImageProcessingService(10);

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
