package com.sismd.service;

import com.sismd.model.ImageMetadata;

import java.io.File;

/**
 * Extracts display metadata from an image file without retaining pixel data.
 */
public interface ImageMetadataService {
    ImageMetadata read(File file);
}
