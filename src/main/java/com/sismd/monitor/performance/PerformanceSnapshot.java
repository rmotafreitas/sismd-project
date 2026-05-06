package com.sismd.monitor.performance;

import java.util.Map;

public interface PerformanceSnapshot {
    /** Wall-clock time the operation took, in milliseconds. */
    long getWallTimeMs();

    /**
     * Ordered breakdown metrics for display (human-readable strings with units).
     * Wall time is NOT repeated here — callers use getWallTimeMs() for the
     * headline.
     */
    Map<String, String> getMetrics();

    // ── typed numeric accessors (for machine-readable CSV export) ────────────

    /** Total process CPU time consumed during the window, in milliseconds. */
    long getCpuTimeMs();

    /** CPU time / wall time × 100. >100% = multiple cores active. */
    double getCpuEfficiencyPct();

    /** Bytes allocated by all live JVM threads during the window, in MB. */
    double getAllocatedMb();

    double getHeapBeforeMb();

    double getHeapAfterMb();

    long getGcCycles();

    long getGcPauseMs();

    /** Peak JVM thread count observed during the window. */
    int getPeakThreads();

    /**
     * Names of active GC collectors (e.g. "G1 Young Generation + G1 Old
     * Generation").
     */
    String getGcCollector();
}
