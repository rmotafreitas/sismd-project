package com.sismd.model;

import org.junit.jupiter.api.Test;

import java.awt.Color;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageDataTest {

    @Test
    void of_setsFieldsCorrectly() {
        Color[][] pixels = new Color[4][3];
        for (int x = 0; x < 4; x++)
            for (int y = 0; y < 3; y++)
                pixels[x][y] = Color.RED;

        ImageData data = ImageData.of(pixels, 4, 3);

        assertThat(data.getWidth()).isEqualTo(4);
        assertThat(data.getHeight()).isEqualTo(3);
        assertThat(data.getPixels()).isSameAs(pixels);
    }

    @Test
    void getPixelCount_returnsWidthTimesHeight() {
        ImageData data = ImageData.of(makePixels(5, 7), 5, 7);
        assertThat(data.getPixelCount()).isEqualTo(35L);
    }

    @Test
    void getPixel_returnsCorrectPixel() {
        Color[][] pixels = makePixels(3, 3);
        Color expected = new Color(11, 22, 33);
        pixels[1][2] = expected;

        ImageData data = ImageData.of(pixels, 3, 3);

        assertThat(data.getPixel(1, 2)).isEqualTo(expected);
    }

    @Test
    void builder_producesEquivalentToFactory() {
        Color[][] pixels = makePixels(2, 2);
        ImageData viaFactory = ImageData.of(pixels, 2, 2);
        ImageData viaBuilder = ImageData.builder().pixels(pixels).width(2).height(2).build();

        assertThat(viaBuilder.getWidth()).isEqualTo(viaFactory.getWidth());
        assertThat(viaBuilder.getHeight()).isEqualTo(viaFactory.getHeight());
    }

    @Test
    void of_nullPixels_throws() {
        assertThatThrownBy(() -> ImageData.of(null, 2, 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pixels");
    }

    @Test
    void of_zeroWidth_throws() {
        assertThatThrownBy(() -> ImageData.of(makePixels(1, 1), 0, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("width");
    }

    @Test
    void of_negativeHeight_throws() {
        assertThatThrownBy(() -> ImageData.of(makePixels(1, 1), 1, -1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("height");
    }

    private static Color[][] makePixels(int w, int h) {
        Color[][] p = new Color[w][h];
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                p[x][y] = Color.BLACK;
        return p;
    }
}
