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
    private static final long serialVersionUID = 2L;

    // ── identity / file tracking ─────────────────────────────────────────────
    private final String uuid;
    private final String algorithmName;
    private final String inputFilename;
    private final String outputFilename;
    private final Instant createdAt;
    private final int imageWidth;
    private final int imageHeight;

    // ── timing ───────────────────────────────────────────────────────────────
    private final long wallTimeMs;
    private final long cpuTimeMs;
    private final double cpuEfficiencyPct;

    // ── memory / allocation ──────────────────────────────────────────────────
    private final double allocatedMb;
    private final double heapBeforeMb;
    private final double heapAfterMb;

    // ── GC ───────────────────────────────────────────────────────────────────
    private final long gcCycles;
    private final long gcPauseMs;
    private final String gcCollector;

    // ── threads ──────────────────────────────────────────────────────────────
    private final int peakThreads;

    // ── human-readable display metrics (kept for UI metrics panel) ───────────
    private final Map<String, String> metrics;

    // ── computed helpers ─────────────────────────────────────────────────────

    public long getPixels() {
        return (long) imageWidth * imageHeight;
    }

    public double getHeapDeltaMb() {
        return heapAfterMb - heapBeforeMb;
    }
}
