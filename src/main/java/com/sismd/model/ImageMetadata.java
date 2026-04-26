package com.sismd.model;

import lombok.Builder;
import lombok.Getter;

import java.io.File;

@Getter
@Builder
public class ImageMetadata {

    private final String name;
    private final String format;
    private final long   sizeBytes;
    private final int    width;
    private final int    height;

    public static ImageMetadata fromFile(File file, int width, int height) {
        if (width  <= 0) throw new IllegalStateException("width must be positive");
        if (height <= 0) throw new IllegalStateException("height must be positive");
        String name = file.getName();
        int dot = name.lastIndexOf('.');
        String format = dot >= 0 ? name.substring(dot + 1).toUpperCase() : "?";
        return ImageMetadata.builder()
                .name(name)
                .format(format)
                .sizeBytes(file.length())
                .width(width)
                .height(height)
                .build();
    }

    public long getPixelCount() {
        return (long) width * height;
    }

    public String getHumanSize() {
        if (sizeBytes < 1_024)     return sizeBytes + " B";
        if (sizeBytes < 1_048_576) return String.format("%.1f KB", sizeBytes / 1_024.0);
        return String.format("%.2f MB", sizeBytes / 1_048_576.0);
    }

    public String getDimensions() {
        return width + " × " + height + " px";
    }
}
