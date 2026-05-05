package com.sismd.model;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ImageData {

    // Packed RGB ints in column-major order: index = x * height + y.
    // No per-pixel object allocation; 4 bytes per pixel instead of ~32.
    private final int[] pixels;
    private final int width;
    private final int height;

    public static ImageData of(int[] pixels, int width, int height) {
        if (pixels == null) throw new IllegalStateException("pixels must be set");
        if (width  <= 0)    throw new IllegalStateException("width must be positive");
        if (height <= 0)    throw new IllegalStateException("height must be positive");
        return ImageData.builder().pixels(pixels).width(width).height(height).build();
    }

    public long getPixelCount() {
        return (long) width * height;
    }

    public int getPixel(int x, int y) {
        return pixels[x * height + y];
    }
}
