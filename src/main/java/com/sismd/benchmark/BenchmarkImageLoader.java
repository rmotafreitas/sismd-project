package com.sismd.benchmark;

import com.sismd.model.ImageData;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Loads pre-scaled benchmark images from {@code images/benchmark/}.
 *
 * The directory contains four JPEG files (Small, Medium, Large, Original)
 * derived from {@code input2.jpg}. This class reads them and converts
 * each to an {@link ImageData} instance for the benchmark harness.
 */
public final class BenchmarkImageLoader {

    private BenchmarkImageLoader() {
    }

    private static final Path BENCHMARK_DIR = Path.of("images", "benchmark");

    /** Labels in display order — filenames without extension. */
    private static final String[] LABELS = { "Small", "Medium", "Large", "Original" };

    /**
     * Loads every pre-scaled image from {@code images/benchmark/}.
     *
     * @return ordered map of {@code "Label(WxH)"} → {@link ImageData}
     * @throws IllegalStateException if the directory or images are missing
     */
    public static Map<String, ImageData> loadAllSizes() {
        // ── Arrange ──────────────────────────────────────────────────────────
        if (!Files.isDirectory(BENCHMARK_DIR)) {
            throw new IllegalStateException(
                    "Benchmark images not found in " + BENCHMARK_DIR
                            + " — place Small.jpg, Medium.jpg, Large.jpg, Original.jpg there");
        }

        // ── Act ──────────────────────────────────────────────────────────────
        Map<String, ImageData> result = new LinkedHashMap<>();
        for (var label : LABELS) {
            var file = BENCHMARK_DIR.resolve(label + ".jpg");
            if (!Files.exists(file)) {
                System.err.println("  WARN: missing " + file + " — skipping");
                continue;
            }
            var img = readImage(file);
            var data = toImageData(img);
            result.put(label + "(" + img.getWidth() + "x" + img.getHeight() + ")", data);
        }

        // ── Assert ───────────────────────────────────────────────────────────
        if (result.isEmpty()) {
            throw new IllegalStateException("No images found in " + BENCHMARK_DIR);
        }
        return result;
    }

    // ── internals ────────────────────────────────────────────────────────────

    private static BufferedImage readImage(Path path) {
        try {
            var img = ImageIO.read(path.toFile());
            if (img == null)
                throw new IllegalArgumentException("Cannot decode: " + path);
            return img;
        } catch (IOException e) {
            throw new UncheckedIOException("Failed to read " + path, e);
        }
    }

    private static ImageData toImageData(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        var pixels = new int[w * h];
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                pixels[x * h + y] = img.getRGB(x, y) & 0x00FFFFFF;
        return ImageData.of(pixels, w, h);
    }
}
