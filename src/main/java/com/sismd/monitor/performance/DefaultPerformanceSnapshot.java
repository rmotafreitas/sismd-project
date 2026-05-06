package com.sismd.monitor.performance;

import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
public class DefaultPerformanceSnapshot implements PerformanceSnapshot {

    private final long wallTimeMs;
    private final Map<String, String> metrics;

    // ── typed numeric fields ─────────────────────────────────────────────────
    private final long cpuTimeMs;
    private final double cpuEfficiencyPct;
    private final double allocatedMb;
    private final double heapBeforeMb;
    private final double heapAfterMb;
    private final long gcCycles;
    private final long gcPauseMs;
    private final int peakThreads;
    private final String gcCollector;

    private DefaultPerformanceSnapshot(Builder builder) {
        this.wallTimeMs = builder.wallTimeMs;
        this.metrics = Collections.unmodifiableMap(new LinkedHashMap<>(builder.metrics));
        this.cpuTimeMs = builder.cpuTimeMs;
        this.cpuEfficiencyPct = builder.cpuEfficiencyPct;
        this.allocatedMb = builder.allocatedMb;
        this.heapBeforeMb = builder.heapBeforeMb;
        this.heapAfterMb = builder.heapAfterMb;
        this.gcCycles = builder.gcCycles;
        this.gcPauseMs = builder.gcPauseMs;
        this.peakThreads = builder.peakThreads;
        this.gcCollector = builder.gcCollector;
    }

    public static class Builder {

        private long wallTimeMs;
        private final Map<String, String> metrics = new LinkedHashMap<>();
        private long cpuTimeMs;
        private double cpuEfficiencyPct;
        private double allocatedMb;
        private double heapBeforeMb;
        private double heapAfterMb;
        private long gcCycles;
        private long gcPauseMs;
        private int peakThreads;
        private String gcCollector = "";

        public Builder wallTimeMs(long ms) {
            this.wallTimeMs = ms;
            return this;
        }

        public Builder metric(String key, String value) {
            metrics.put(key, value);
            return this;
        }

        public Builder cpuTimeMs(long v) {
            this.cpuTimeMs = v;
            return this;
        }

        public Builder cpuEfficiencyPct(double v) {
            this.cpuEfficiencyPct = v;
            return this;
        }

        public Builder allocatedMb(double v) {
            this.allocatedMb = v;
            return this;
        }

        public Builder heapBeforeMb(double v) {
            this.heapBeforeMb = v;
            return this;
        }

        public Builder heapAfterMb(double v) {
            this.heapAfterMb = v;
            return this;
        }

        public Builder gcCycles(long v) {
            this.gcCycles = v;
            return this;
        }

        public Builder gcPauseMs(long v) {
            this.gcPauseMs = v;
            return this;
        }

        public Builder peakThreads(int v) {
            this.peakThreads = v;
            return this;
        }

        public Builder gcCollector(String v) {
            this.gcCollector = v;
            return this;
        }

        public DefaultPerformanceSnapshot build() {
            if (wallTimeMs < 0)
                throw new IllegalStateException("wallTimeMs must be non-negative");
            return new DefaultPerformanceSnapshot(this);
        }
    }
}
