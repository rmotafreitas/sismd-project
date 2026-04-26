package com.sismd.model;

import lombok.Builder;
import lombok.Getter;

import java.awt.Color;

@Getter
@Builder
public class ImageData {

    private final Color[][] pixels;
    private final int width;
    private final int height;

    public static ImageData of(Color[][] pixels, int width, int height) {
        if (pixels == null) throw new IllegalStateException("pixels must be set");
        if (width  <= 0)    throw new IllegalStateException("width must be positive");
        if (height <= 0)    throw new IllegalStateException("height must be positive");
        return ImageData.builder().pixels(pixels).width(width).height(height).build();
    }

    public long getPixelCount() {
        return (long) width * height;
    }

    public Color getPixel(int x, int y) {
        return pixels[x][y];
    }
}
