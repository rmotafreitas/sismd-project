package com.sismd.model;

import lombok.Builder;
import lombok.Getter;

import java.io.Serial;
import java.io.Serializable;
import java.time.Instant;
import java.util.Map;

@Getter
@Builder
public class GenerationRecord implements Serializable {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String              uuid;
    private final String              algorithmName;
    private final String              inputFilename;
    private final String              outputFilename;
    private final long                wallTimeMs;
    private final Map<String, String> metrics;
    private final Instant             createdAt;
    private final int                 imageWidth;
    private final int                 imageHeight;
}
