package com.sismd.service;

import com.sismd.model.ImageData;

/**
 * Applies an image transformation and returns the result.
 * Swap implementations to change execution strategy (sequential → parallel, GPU, etc.)
 * without touching callers.
 */
public interface ImageProcessingService {
    ImageData process(ImageData input);
}
