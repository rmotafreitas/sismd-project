package com.sismd.service.impl;

import com.sismd.model.ImageData;
import com.sismd.service.ImageIOService;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.File;
import java.io.IOException;
import java.io.UncheckedIOException;

public class DefaultImageIOService implements ImageIOService {

    @Override
    public ImageData load(File file) {
        BufferedImage img = readFile(file);
        int w = img.getWidth(), h = img.getHeight();
        int[] pixels = new int[w * h];
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                pixels[x * h + y] = img.getRGB(x, y) & 0x00FFFFFF;
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
        int w = data.getWidth(), h = data.getHeight();
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        for (int x = 0; x < w; x++)
            for (int y = 0; y < h; y++)
                img.setRGB(x, y, data.getPixel(x, y));
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
