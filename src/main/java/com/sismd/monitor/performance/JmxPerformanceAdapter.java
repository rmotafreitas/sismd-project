package com.sismd.monitor.performance;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.List;

/**
 * Measures the cost of an operation including all threads spawned during the
 * window.
 *
 * CPU time uses OperatingSystemMXBean.getProcessCpuTime() (process-wide) rather
 * than
 * getCurrentThreadCpuTime(), so worker threads that are created and terminated
 * during
 * the window are fully counted. The tradeoff is that JVM background threads
 * (JIT, GC)
 * are also included, but their contribution is small relative to parallel image
 * work.
 *
 * What each metric means:
 * Wall time — elapsed clock time (includes waiting, scheduling, I/O)
 * CPU time — total CPU consumed by all JVM threads during the window
 * CPU efficiency— cpu / wall; >100% = multi-threaded, ~100% = sequential
 * compute-bound
 * Allocated — bytes allocated by all live JVM threads during this window
 * (threads that die inside the window cannot be counted by the JVM API)
 * GC cycles — collections that fired during the window (JVM-wide)
 * GC pause — total stop-the-world time during the window (JVM-wide)
 * Heap before/after — heap snapshot for context, not an allocation measure
 */
public class JmxPerformanceAdapter implements PerformanceMonitor {

    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    private final MemoryMXBean memBean = ManagementFactory.getMemoryMXBean();
    private final List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();
    private final com.sun.management.OperatingSystemMXBean osBean;

    public JmxPerformanceAdapter() {
        java.lang.management.OperatingSystemMXBean raw = ManagementFactory.getOperatingSystemMXBean();
        osBean = raw instanceof com.sun.management.OperatingSystemMXBean sun ? sun : null;
    }

    // captured at start()
    private long startWallNs;
    private long startCpuNs;
    private long startAllocatedBytes;
    private long startGcCount;
    private long startGcTimeMs;
    private long startHeapUsed;
    private int startThreadCount;

    @Override
    public void start() {
        enableMeasurements();
        // Reset the JVM peak so we only observe threads spawned during *this* window
        threadBean.resetPeakThreadCount();
        // Snapshot the baseline — ordered so wall-clock is as close as possible
        // to the actual work starting. No System.gc() — that would bias all metrics.
        startHeapUsed = memBean.getHeapMemoryUsage().getUsed();
        startGcCount = sumGcCount();
        startGcTimeMs = sumGcTime();
        startAllocatedBytes = sumAllThreadsAllocatedBytes();
        startThreadCount = threadBean.getThreadCount();
        startCpuNs = osBean != null ? osBean.getProcessCpuTime() : -1;
        startWallNs = System.nanoTime(); // last — leaves the least gap before work
    }

    @Override
    public PerformanceSnapshot stop() {
        // Mirror the order: wall first to minimise gap after work finishes
        long wallNs = System.nanoTime() - startWallNs;
        long rawCpuNs = (osBean != null && startCpuNs >= 0) ? osBean.getProcessCpuTime() - startCpuNs : -1;
        long cpuNs = nonNegative(rawCpuNs);
        long allocated = nonNegative(sumAllThreadsAllocatedBytes() - startAllocatedBytes);
        long heapAfter = memBean.getHeapMemoryUsage().getUsed();
        long gcCycles = sumGcCount() - startGcCount;
        long gcPauseMs = sumGcTime() - startGcTimeMs;

        long wallMs = wallNs / 1_000_000;
        long cpuMs = cpuNs / 1_000_000;

        // Thread counts — proves we're observing all spawned worker threads
        int peakThreads = threadBean.getPeakThreadCount();
        int endThreads = threadBean.getThreadCount();

        // >100% means multiple cores were active (expected for parallel algorithms)
        String cpuEff = (wallNs > 0 && cpuNs >= 0)
                ? String.format("%.1f%%", 100.0 * cpuNs / wallNs)
                : "n/a";

        return new DefaultPerformanceSnapshot.Builder()
                .wallTimeMs(wallMs)
                .metric("CPU time", cpuMs >= 0 ? cpuMs + " ms" : "n/a")
                .metric("CPU efficiency", cpuEff)
                .metric("Threads", startThreadCount + " → peak " + peakThreads + " → " + endThreads)
                .metric("Allocated", allocated >= 0 ? fmtBytes(allocated) : "n/a")
                .metric("GC cycles", String.valueOf(gcCycles))
                .metric("GC pause", gcPauseMs + " ms")
                .metric("Heap before", fmtMb(startHeapUsed))
                .metric("Heap after", fmtMb(heapAfter))
                .build();
    }

    // --- internals ---------------------------------------------------------------

    private void enableMeasurements() {
        if (threadBean instanceof com.sun.management.ThreadMXBean sun
                && sun.isThreadAllocatedMemorySupported()
                && !sun.isThreadAllocatedMemoryEnabled()) {
            sun.setThreadAllocatedMemoryEnabled(true);
        }
    }

    /**
     * Sums allocated bytes across all currently-live JVM threads.
     * Threads that were created and terminated inside the measurement window
     * are not visible here (JVM API limitation), so this undercounts for
     * ManualThread-style implementations where workers die before stop().
     * Pool-based implementations (ThreadPool, ForkJoin, CompletableFuture)
     * keep threads alive across the window so their allocations are captured.
     */
    private long sumAllThreadsAllocatedBytes() {
        if (!(threadBean instanceof com.sun.management.ThreadMXBean sun))
            return -1;
        long[] ids = threadBean.getAllThreadIds();
        long[] perThread = sun.getThreadAllocatedBytes(ids);
        long sum = 0;
        for (long v : perThread) {
            if (v >= 0)
                sum += v;
        }
        return sum;
    }

    private static long nonNegative(long v) {
        return v >= 0 ? v : -1;
    }

    private static String fmtMb(long bytes) {
        return String.format("%.1f MB", bytes / 1_048_576.0);
    }

    private static String fmtBytes(long bytes) {
        if (bytes < 1_024)
            return bytes + " B";
        if (bytes < 1_048_576)
            return String.format("%.1f KB", bytes / 1_024.0);
        return String.format("%.2f MB", bytes / 1_048_576.0);
    }

    private long sumGcCount() {
        return gcBeans.stream().mapToLong(GarbageCollectorMXBean::getCollectionCount).sum();
    }

    private long sumGcTime() {
        return gcBeans.stream().mapToLong(GarbageCollectorMXBean::getCollectionTime).sum();
    }
}
