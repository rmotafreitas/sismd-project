package com.sismd.service.impl;

import com.sismd.model.ImageData;
import com.sismd.model.ImageMetadata;
import com.sismd.service.ImageIOService;
import com.sismd.service.ImageMetadataService;
import lombok.RequiredArgsConstructor;

import java.io.File;

@RequiredArgsConstructor
public class DefaultImageMetadataService implements ImageMetadataService {

    private final ImageIOService imageIOService;

    @Override
    public ImageMetadata read(File file) {
        ImageData data = imageIOService.load(file);
        return ImageMetadata.fromFile(file, data.getWidth(), data.getHeight());
    }
}
