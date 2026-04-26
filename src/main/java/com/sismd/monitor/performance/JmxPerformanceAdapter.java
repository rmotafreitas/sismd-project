package com.sismd.monitor.performance;

import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.ThreadMXBean;
import java.util.List;

/**
 * Measures the cost of an operation from the perspective of the thread that runs it.
 *
 * start() / stop() must be called from the same thread that performs the work —
 * that is the only way to attribute CPU time and allocations to this operation
 * rather than to the whole JVM.
 *
 * What each metric means:
 *   Wall time     — elapsed clock time (includes waiting, scheduling, I/O)
 *   CPU time      — time this thread spent on a CPU core (user + kernel)
 *   User time     — CPU time in user-mode code (the algorithm itself)
 *   Kernel time   — CPU time in kernel-mode (memory ops, syscalls)
 *   CPU efficiency— cpu / wall; near 100 % = compute-bound, low = memory/IO-bound
 *   Allocated     — bytes the thread allocated on the heap during this window;
 *                   this is cumulative, never affected by GC running mid-operation
 *   GC cycles     — collections that fired during the window (JVM-wide)
 *   GC pause      — total stop-the-world time during the window (JVM-wide)
 *   Heap before/after — heap snapshot for context, not an allocation measure
 */
public class JmxPerformanceAdapter implements PerformanceMonitor {

    private final ThreadMXBean threadBean = ManagementFactory.getThreadMXBean();
    private final MemoryMXBean memBean    = ManagementFactory.getMemoryMXBean();
    private final List<GarbageCollectorMXBean> gcBeans = ManagementFactory.getGarbageCollectorMXBeans();

    // captured at start()
    private long startWallNs;
    private long startCpuNs;
    private long startUserNs;
    private long startAllocatedBytes;
    private long startGcCount;
    private long startGcTimeMs;
    private long startHeapUsed;

    @Override
    public void start() {
        enableMeasurements();
        // Snapshot the baseline — ordered so wall-clock is as close as possible
        // to the actual work starting. No System.gc() — that would bias all metrics.
        startHeapUsed       = memBean.getHeapMemoryUsage().getUsed();
        startGcCount        = sumGcCount();
        startGcTimeMs       = sumGcTime();
        startAllocatedBytes = currentThreadAllocatedBytes();
        startCpuNs          = threadBean.getCurrentThreadCpuTime();
        startUserNs         = threadBean.getCurrentThreadUserTime();
        startWallNs         = System.nanoTime(); // last — leaves the least gap before work
    }

    @Override
    public PerformanceSnapshot stop() {
        // Mirror the order: wall first to minimise gap after work finishes
        long wallNs    = System.nanoTime() - startWallNs;
        long cpuNs     = nonNegative(threadBean.getCurrentThreadCpuTime()  - startCpuNs);
        long userNs    = nonNegative(threadBean.getCurrentThreadUserTime() - startUserNs);
        long allocated = nonNegative(currentThreadAllocatedBytes()         - startAllocatedBytes);
        long heapAfter = memBean.getHeapMemoryUsage().getUsed();
        long gcCycles  = sumGcCount() - startGcCount;
        long gcPauseMs = sumGcTime()  - startGcTimeMs;

        long wallMs   = wallNs / 1_000_000;
        long cpuMs    = cpuNs  / 1_000_000;
        long userMs   = userNs / 1_000_000;
        long kernelMs = Math.max(0, cpuMs - userMs);

        String cpuEff = (wallNs > 0 && cpuNs >= 0)
                ? String.format("%.1f%%", 100.0 * cpuNs / wallNs)
                : "n/a";

        return new DefaultPerformanceSnapshot.Builder()
                .wallTimeMs(wallMs)
                .metric("CPU time",       cpuMs    >= 0 ? cpuMs    + " ms"      : "n/a")
                .metric("  user",         userMs   >= 0 ? userMs   + " ms"      : "n/a")
                .metric("  kernel",       kernelMs >= 0 ? kernelMs + " ms"      : "n/a")
                .metric("CPU efficiency", cpuEff)
                .metric("Allocated",      allocated >= 0 ? fmtBytes(allocated)  : "n/a")
                .metric("GC cycles",      String.valueOf(gcCycles))
                .metric("GC pause",       gcPauseMs + " ms")
                .metric("Heap before",    fmtMb(startHeapUsed))
                .metric("Heap after",     fmtMb(heapAfter))
                .build();
    }

    // --- internals ---------------------------------------------------------------

    private void enableMeasurements() {
        if (threadBean.isThreadCpuTimeSupported() && !threadBean.isThreadCpuTimeEnabled()) {
            threadBean.setThreadCpuTimeEnabled(true);
        }
        if (threadBean instanceof com.sun.management.ThreadMXBean sun
                && sun.isThreadAllocatedMemorySupported()
                && !sun.isThreadAllocatedMemoryEnabled()) {
            sun.setThreadAllocatedMemoryEnabled(true);
        }
    }

    /** Returns bytes allocated by the calling thread, or -1 if not available. */
    private long currentThreadAllocatedBytes() {
        if (threadBean instanceof com.sun.management.ThreadMXBean sun) {
            long v = sun.getThreadAllocatedBytes(Thread.currentThread().getId());
            return v >= 0 ? v : -1;
        }
        return -1;
    }

    private static long nonNegative(long v) {
        return v >= 0 ? v : -1;
    }

    private static String fmtMb(long bytes) {
        return String.format("%.1f MB", bytes / 1_048_576.0);
    }

    private static String fmtBytes(long bytes) {
        if (bytes < 1_024)     return bytes + " B";
        if (bytes < 1_048_576) return String.format("%.1f KB", bytes / 1_024.0);
        return String.format("%.2f MB", bytes / 1_048_576.0);
    }

    private long sumGcCount() {
        return gcBeans.stream().mapToLong(GarbageCollectorMXBean::getCollectionCount).sum();
    }

    private long sumGcTime() {
        return gcBeans.stream().mapToLong(GarbageCollectorMXBean::getCollectionTime).sum();
    }
}
