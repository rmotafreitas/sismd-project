package com.sismd.model;

import org.junit.jupiter.api.Test;

import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageDataTest {

    @Test
    void of_setsFieldsCorrectly() {
        // Arrange
        int[] pixels = new int[4 * 3];
        Arrays.fill(pixels, 0xFF0000);

        // Act
        ImageData data = ImageData.of(pixels, 4, 3);

        // Assert
        assertThat(data.getWidth()).isEqualTo(4);
        assertThat(data.getHeight()).isEqualTo(3);
        assertThat(data.getPixels()).isSameAs(pixels);
    }

    @Test
    void getPixelCount_returnsWidthTimesHeight() {
        // Arrange
        ImageData data = ImageData.of(makePixels(5, 7), 5, 7);

        // Act
        long count = data.getPixelCount();

        // Assert
        assertThat(count).isEqualTo(35L);
    }

    @Test
    void getPixel_returnsCorrectPixel() {
        // Arrange
        int[] pixels = makePixels(3, 3);
        int expected = (11 << 16) | (22 << 8) | 33;
        pixels[1 * 3 + 2] = expected; // column-major: x=1, y=2, height=3
        ImageData data = ImageData.of(pixels, 3, 3);

        // Act
        int actual = data.getPixel(1, 2);

        // Assert
        assertThat(actual).isEqualTo(expected);
    }

    @Test
    void builder_producesEquivalentToFactory() {
        // Arrange
        int[] pixels = makePixels(2, 2);

        // Act
        ImageData viaFactory = ImageData.of(pixels, 2, 2);
        ImageData viaBuilder = ImageData.builder().pixels(pixels).width(2).height(2).build();

        // Assert
        assertThat(viaBuilder.getWidth()).isEqualTo(viaFactory.getWidth());
        assertThat(viaBuilder.getHeight()).isEqualTo(viaFactory.getHeight());
    }

    @Test
    void of_nullPixels_throws() {
        // Arrange & Act & Assert
        assertThatThrownBy(() -> ImageData.of(null, 2, 2))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("pixels");
    }

    @Test
    void of_zeroWidth_throws() {
        // Arrange & Act & Assert
        assertThatThrownBy(() -> ImageData.of(makePixels(1, 1), 0, 1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("width");
    }

    @Test
    void of_negativeHeight_throws() {
        // Arrange & Act & Assert
        assertThatThrownBy(() -> ImageData.of(makePixels(1, 1), 1, -1))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("height");
    }

    private static int[] makePixels(int w, int h) {
        return new int[w * h]; // all zeros (black)
    }
}
