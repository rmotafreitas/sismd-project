package com.sismd.model;

import lombok.Builder;
import lombok.Getter;
import lombok.ToString;

/**
 * Immutable value holding every metric captured for a single benchmark run
 * (one implementation × one image size).
 *
 * Includes computed helpers for CSV serialisation and human-readable display.
 */
@Getter
@Builder
@ToString
public class BenchmarkResult {

    private final String implName;
    private final String sizeLabel;
    private final int threadCount;
    private final int width;
    private final int height;

    // timing
    private final double avgMs;
    private final double minMs;
    private final double maxMs;
    private final double stdDevMs;

    // memory / GC
    private final double heapBeforeMb;
    private final double heapAfterMb;
    private final long gcCycles;
    private final long gcPauseMs;
    private final double sysLoad;
    private final String gcCollector;

    // ── computed fields ──────────────────────────────────────────────────────

    public long getPixels() {
        return (long) width * height;
    }

    public double getHeapDeltaMb() {
        return heapAfterMb - heapBeforeMb;
    }

    public double getSpeedup(double sequentialAvgMs) {
        return (avgMs > 0) ? sequentialAvgMs / avgMs : 1.0;
    }

    // ── CSV support ──────────────────────────────────────────────────────────

    private static final String[] CSV_HEADERS = {
            "Implementation", "Threads", "ImageSize",
            "Width_px", "Height_px", "Pixels",
            "Avg_ms", "Min_ms", "Max_ms", "StdDev_ms", "Speedup",
            "HeapBefore_MB", "HeapAfter_MB", "HeapDelta_MB",
            "GC_Cycles", "GC_Pause_ms", "Sys_Load", "GC_Collector"
    };

    public static String csvHeader() {
        return String.join(",", CSV_HEADERS);
    }

    public String toCsvRow(double sequentialAvgMs) {
        return String.format(
                "%s,%d,%s,%d,%d,%d,%.3f,%.3f,%.3f,%.3f,%.2f,%.1f,%.1f,%.1f,%d,%d,%.2f,%s",
                implName, threadCount, sizeLabel,
                width, height, getPixels(),
                avgMs, minMs, maxMs, stdDevMs, getSpeedup(sequentialAvgMs),
                heapBeforeMb, heapAfterMb, getHeapDeltaMb(),
                gcCycles, gcPauseMs, sysLoad, gcCollector);
    }

    // ── human-readable one-liner (console) ───────────────────────────────────

    public String toConsoleLine(double sequentialAvgMs) {
        return String.format("avg=%8.2f ms   min=%8.2f   max=%8.2f   σ=%6.2f   ×%.2f",
                avgMs, minMs, maxMs, stdDevMs, getSpeedup(sequentialAvgMs));
    }
}
