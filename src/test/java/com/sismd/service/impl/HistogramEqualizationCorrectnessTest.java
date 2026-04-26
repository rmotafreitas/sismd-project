package com.sismd.service.impl;

import com.sismd.model.ImageData;
import com.sismd.service.ImageProcessingService;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.MethodSource;

import java.awt.Color;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Golden test — every ImageProcessingService implementation must produce
 * pixel-identical output to the sequential baseline.
 *
 * Add new implementations to {@link #implementations()} as they are built.
 */
class HistogramEqualizationCorrectnessTest {

    static Stream<ImageProcessingService> implementations() {
        return Stream.of(
                new SequentialImageProcessingService(),
                new ManualThreadImageProcessingService(),
                new ThreadPoolImageProcessingService(),
                new ForkJoinImageProcessingService(),
                new CompletableFutureImageProcessingService()
        );
    }

    @ParameterizedTest(name = "{0} — small image")
    @MethodSource("implementations")
    void smallImage_matchesSequentialBaseline(ImageProcessingService impl) {
        assertMatchesBaseline(impl, TestImageFactory.createSmall());
    }

    @ParameterizedTest(name = "{0} — medium image")
    @MethodSource("implementations")
    void mediumImage_matchesSequentialBaseline(ImageProcessingService impl) {
        assertMatchesBaseline(impl, TestImageFactory.createMedium());
    }

    @ParameterizedTest(name = "{0} — large image")
    @MethodSource("implementations")
    void largeImage_matchesSequentialBaseline(ImageProcessingService impl) {
        assertMatchesBaseline(impl, TestImageFactory.createLarge());
    }

    @ParameterizedTest(name = "{0} — gradient image")
    @MethodSource("implementations")
    void gradientImage_matchesSequentialBaseline(ImageProcessingService impl) {
        assertMatchesBaseline(impl, TestImageFactory.createGradient(150, 150));
    }

    @ParameterizedTest(name = "{0} — uniform image")
    @MethodSource("implementations")
    void uniformImage_matchesSequentialBaseline(ImageProcessingService impl) {
        assertMatchesBaseline(impl, TestImageFactory.createUniform(50, 50, new Color(128, 64, 200)));
    }

    // ── helpers ───────────────────────────────────────────────────────────────────

    private void assertMatchesBaseline(ImageProcessingService impl, ImageData input) {
        ImageData expected = new SequentialImageProcessingService().process(input);
        ImageData actual   = impl.process(input);

        assertThat(actual.getWidth()).isEqualTo(expected.getWidth());
        assertThat(actual.getHeight()).isEqualTo(expected.getHeight());

        for (int x = 0; x < expected.getWidth(); x++)
            for (int y = 0; y < expected.getHeight(); y++)
                assertThat(actual.getPixel(x, y).getRGB())
                        .describedAs("pixel mismatch at (%d, %d)", x, y)
                        .isEqualTo(expected.getPixel(x, y).getRGB());
    }
}
