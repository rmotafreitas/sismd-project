package com.sismd.monitor.performance;

import lombok.Getter;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

@Getter
public class DefaultPerformanceSnapshot implements PerformanceSnapshot {

    private final long wallTimeMs;
    private final Map<String, String> metrics;

    private DefaultPerformanceSnapshot(Builder builder) {
        this.wallTimeMs = builder.wallTimeMs;
        this.metrics    = Collections.unmodifiableMap(new LinkedHashMap<>(builder.metrics));
    }

    public static class Builder {

        private long wallTimeMs;
        private final Map<String, String> metrics = new LinkedHashMap<>();

        public Builder wallTimeMs(long ms)              { this.wallTimeMs = ms;         return this; }
        public Builder metric(String key, String value) { metrics.put(key, value);      return this; }

        public DefaultPerformanceSnapshot build() {
            if (wallTimeMs < 0) throw new IllegalStateException("wallTimeMs must be non-negative");
            return new DefaultPerformanceSnapshot(this);
        }
    }
}
