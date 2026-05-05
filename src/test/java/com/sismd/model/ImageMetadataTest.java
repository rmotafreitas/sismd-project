package com.sismd.model;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ImageMetadataTest {

    @Test
    void fromFile_extractsNameAndFormat(@TempDir Path tmp) throws IOException {
        // Arrange
        File file = tmp.resolve("photo.png").toFile();
        Files.write(file.toPath(), new byte[2048]);

        // Act
        ImageMetadata meta = ImageMetadata.fromFile(file, 100, 80);

        // Assert
        assertThat(meta.getName()).isEqualTo("photo.png");
        assertThat(meta.getFormat()).isEqualTo("PNG");
    }

    @Test
    void fromFile_readsSizeBytes(@TempDir Path tmp) throws IOException {
        // Arrange
        File file = tmp.resolve("img.jpg").toFile();
        Files.write(file.toPath(), new byte[4096]);

        // Act
        ImageMetadata meta = ImageMetadata.fromFile(file, 10, 10);

        // Assert
        assertThat(meta.getSizeBytes()).isEqualTo(4096L);
    }

    @Test
    void getPixelCount_returnsWidthTimesHeight() {
        // Arrange
        ImageMetadata meta = builder(640, 480).build();

        // Act
        long count = meta.getPixelCount();

        // Assert
        assertThat(count).isEqualTo(307_200L);
    }

    @Test
    void getDimensions_formatsCorrectly() {
        // Arrange
        ImageMetadata meta = builder(1920, 1080).build();

        // Act
        String dims = meta.getDimensions();

        // Assert
        assertThat(dims).isEqualTo("1920 × 1080 px");
    }

    @ParameterizedTest
    @CsvSource({
            "512,   '512 B'",
            "1024,  '1.0 KB'",
            "2048,  '2.0 KB'",
            "1048576,  '1.00 MB'",
            "2097152,  '2.00 MB'"
    })
    void getHumanSize_formatsCorrectly(long bytes, String expected) {
        // Arrange
        ImageMetadata meta = ImageMetadata.builder()
                .name("x.jpg").format("JPG").sizeBytes(bytes).width(1).height(1)
                .build();

        // Act
        String humanSize = meta.getHumanSize();

        // Assert
        assertThat(humanSize).isEqualTo(expected);
    }

    @Test
    void fromFile_invalidWidth_throws(@TempDir Path tmp) throws IOException {
        // Arrange
        File file = tmp.resolve("img.jpg").toFile();
        Files.write(file.toPath(), new byte[1]);

        // Act & Assert
        assertThatThrownBy(() -> ImageMetadata.fromFile(file, 0, 100))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("width");
    }

    private static ImageMetadata.ImageMetadataBuilder builder(int w, int h) {
        return ImageMetadata.builder()
                .name("test.jpg").format("JPG").sizeBytes(1024).width(w).height(h);
    }
}
