package com.sismd.monitor.performance;

import java.util.Map;

public interface PerformanceSnapshot {
    /** Wall-clock time the operation took, in milliseconds. */
    long getWallTimeMs();

    /**
     * Ordered breakdown metrics for display.
     * Wall time is NOT repeated here — callers use getWallTimeMs() for the headline.
     */
    Map<String, String> getMetrics();
}
