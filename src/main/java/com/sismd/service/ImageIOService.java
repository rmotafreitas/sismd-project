package com.sismd.service;

import com.sismd.model.ImageData;

import java.awt.image.BufferedImage;
import java.io.File;

/**
 * Handles loading and saving images to/from the filesystem.
 * Also converts between ImageData and BufferedImage for display.
 */
public interface ImageIOService {
    ImageData load(File file);
    void save(ImageData data, File dest);
    BufferedImage toBufferedImage(ImageData data);
}
