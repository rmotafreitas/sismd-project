package com.sismd.service.impl;

import com.sismd.model.ImageData;
import com.sismd.service.ImageIOService;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

public class DefaultImageIOService implements ImageIOService {

    @Override
    public ImageData load(File file) {
        BufferedImage img = readFile(file);
        int w = img.getWidth(), h = img.getHeight();
        Color[][] pixels = new Color[w][h];
        for (int x = 0; x < w; x++) {
            for (int y = 0; y < h; y++) {
                int rgb = img.getRGB(x, y);
                pixels[x][y] = new Color((rgb >> 16) & 0xFF, (rgb >> 8) & 0xFF, rgb & 0xFF);
            }
        }
        return ImageData.of(pixels, w, h);
    }

    @Override
    public void save(ImageData data, File dest) {
        String fmt = dest.getName().toLowerCase().endsWith(".png") ? "png" : "jpg";
        try {
            ImageIO.write(toBufferedImage(data), fmt, dest);
        } catch (IOException e) {
            throw new UncheckedIOException("Could not save image to " + dest, e);
        }
    }

    @Override
    public BufferedImage toBufferedImage(ImageData data) {
        BufferedImage img = new BufferedImage(data.getWidth(), data.getHeight(), BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < data.getWidth(); x++) {
            for (int y = 0; y < data.getHeight(); y++) {
                img.setRGB(x, y, data.getPixel(x, y).getRGB());
            }
        }
        return img;
    }

    private static BufferedImage readFile(File file) {
        try {
            BufferedImage img = ImageIO.read(file);
            if (img == null) throw new IllegalArgumentException("Unsupported image format: " + file.getName());
            return img;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not read image: " + file, e);
        }
    }
}
