package com.sismd.benchmark;

import com.sismd.model.ImageData;
import com.sismd.service.ImageProcessingService;
import com.sismd.service.impl.CompletableFutureImageProcessingService;
import com.sismd.service.impl.ForkJoinImageProcessingService;
import com.sismd.service.impl.ManualThreadImageProcessingService;
import com.sismd.service.impl.SequentialImageProcessingService;
import com.sismd.service.impl.ThreadPoolImageProcessingService;

import java.awt.Color;
import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

/**
 * Issue #8 — Benchmarking harness for all histogram-equalization implementations.
 *
 * Run with:  mvn exec:java -Dexec.mainClass="com.sismd.benchmark.BenchmarkRunner"
 * or:        make benchmark
 *
 * Outputs three CSVs to results/:
 *   results_time.csv    — wall-clock timings (avg, min, max, stddev)
 *   results_memory.csv  — heap usage before / after each set of runs
 *   results_gc.csv      — GC collections + pause time deltas, system load
 */
public class BenchmarkRunner {

    private static final int WARMUP    = 3;
    private static final int MEASURED  = 10;

    // ── entry point ───────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║          SISMD Benchmark Runner — Issue #8           ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
        System.out.printf("  Started : %s%n", LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        System.out.printf("  JVM     : %s%n", System.getProperty("java.version"));
        System.out.printf("  Cores   : %d%n%n", Runtime.getRuntime().availableProcessors());

        Map<String, int[]> imageSizes = new LinkedHashMap<>();
        imageSizes.put("Small(640x480)",    new int[]{640,  480});
        imageSizes.put("Medium(1920x1080)", new int[]{1920, 1080});
        imageSizes.put("Large(4096x2160)",  new int[]{4096, 2160});

        List<BenchmarkResult> allResults = new ArrayList<>();

        for (Map.Entry<String, int[]> sizeEntry : imageSizes.entrySet()) {
            String label = sizeEntry.getKey();
            int    w     = sizeEntry.getValue()[0];
            int    h     = sizeEntry.getValue()[1];

            System.out.printf("▶ Generating %s image (%dx%d, %,d pixels)…%n", label, w, h, (long) w * h);
            ImageData image = generateImage(w, h, 42L);

            for (ImplSpec spec : buildSpecs()) {
                System.out.printf("  %-50s ", spec.name() + "…");
                System.out.flush();
                BenchmarkResult result = runBenchmark(spec.service(), image, spec.name(), label, spec.threadCount());
                allResults.add(result);
                System.out.printf("avg=%7.2f ms  min=%7.2f  max=%7.2f  σ=%.2f%n",
                        result.avgWallMs(), result.minWallMs(), result.maxWallMs(), result.stdDevMs());
            }
            System.out.println();
        }

        Path outDir = Path.of("results");
        Files.createDirectories(outDir);
        exportTimeCsv(allResults,   outDir.resolve("results_time.csv").toString());
        exportMemoryCsv(allResults, outDir.resolve("results_memory.csv").toString());
        exportGcCsv(allResults,     outDir.resolve("results_gc.csv").toString());

        System.out.println("╔══════════════════════════════════════════════════════╗");
        System.out.println("║  Done — results written to results/                  ║");
        System.out.println("║    results_time.csv    — execution times             ║");
        System.out.println("║    results_memory.csv  — heap usage                  ║");
        System.out.println("║    results_gc.csv      — GC & CPU metrics            ║");
        System.out.println("╚══════════════════════════════════════════════════════╝");
    }

    // ── public API (also used by tests) ───────────────────────────────────────────

    public static BenchmarkResult runBenchmark(ImageProcessingService service,
                                               ImageData image,
                                               String name,
                                               String sizeLabel,
                                               int threadCount) {
        // warm-up — JIT compilation, cache warm
        for (int i = 0; i < WARMUP; i++) service.process(image);
        System.gc();

        MemoryMXBean       mem = ManagementFactory.getMemoryMXBean();
        OperatingSystemMXBean os  = ManagementFactory.getOperatingSystemMXBean();

        long heapBefore = mem.getHeapMemoryUsage().getUsed();
        GCSnapshot gcBefore = collectGCSnapshot();
        double loadSum = 0;

        long[] times = new long[MEASURED];
        for (int i = 0; i < MEASURED; i++) {
            long t0 = System.nanoTime();
            service.process(image);
            times[i] = System.nanoTime() - t0;
            loadSum += Math.max(0, os.getSystemLoadAverage());
        }

        long heapAfter = mem.getHeapMemoryUsage().getUsed();
        GCSnapshot gcAfter = collectGCSnapshot();

        Arrays.sort(times);
        long sum = 0;
        for (long t : times) sum += t;
        double avgMs = sum / 1_000_000.0 / MEASURED;
        double minMs = times[0] / 1_000_000.0;
        double maxMs = times[MEASURED - 1] / 1_000_000.0;

        double sumSq = 0;
        for (long t : times) { double d = t / 1_000_000.0 - avgMs; sumSq += d * d; }
        double stdDev = Math.sqrt(sumSq / MEASURED);

        return new BenchmarkResult(
                name, sizeLabel, threadCount,
                avgMs, minMs, maxMs, stdDev,
                heapBefore, heapAfter,
                gcAfter.count() - gcBefore.count(),
                gcAfter.timeMs() - gcBefore.timeMs(),
                loadSum / MEASURED
        );
    }

    public static GCSnapshot collectGCSnapshot() {
        long count = 0, timeMs = 0;
        for (GarbageCollectorMXBean gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long c = gc.getCollectionCount();
            long t = gc.getCollectionTime();
            if (c >= 0) count  += c;
            if (t >= 0) timeMs += t;
        }
        return new GCSnapshot(count, timeMs);
    }

    // ── spec builder ──────────────────────────────────────────────────────────────

    private static List<ImplSpec> buildSpecs() {
        int cores = Runtime.getRuntime().availableProcessors();
        int[] threadCounts = deduplicate(new int[]{1, 2, 4, 8, 16, cores});

        List<ImplSpec> specs = new ArrayList<>();
        specs.add(new ImplSpec("Sequential", new SequentialImageProcessingService(), 1));

        for (int t : threadCounts) {
            specs.add(new ImplSpec("ManualThreads(t=" + t + ")",
                    new ManualThreadImageProcessingService(t), t));
            specs.add(new ImplSpec("ThreadPool(t=" + t + ")",
                    new ThreadPoolImageProcessingService(t), t));
            specs.add(new ImplSpec("CompletableFuture(t=" + t + ")",
                    new CompletableFutureImageProcessingService(t), t));
        }

        // ForkJoin uses the common pool (parallelism = cores-1); vary threshold for granularity
        for (int threshold : new int[]{10, 50, 200}) {
            specs.add(new ImplSpec("ForkJoin(threshold=" + threshold + ")",
                    new ForkJoinImageProcessingService(threshold), cores));
        }

        return specs;
    }

    private static int[] deduplicate(int[] src) {
        return Arrays.stream(src).distinct().sorted().toArray();
    }

    // ── image generator ───────────────────────────────────────────────────────────

    public static ImageData generateImage(int width, int height, long seed) {
        Random rng = new Random(seed);
        Color[][] px = new Color[width][height];
        for (int x = 0; x < width; x++)
            for (int y = 0; y < height; y++)
                px[x][y] = new Color(rng.nextInt(256), rng.nextInt(256), rng.nextInt(256));
        return ImageData.of(px, width, height);
    }

    // ── CSV exporters ─────────────────────────────────────────────────────────────

    public static void exportTimeCsv(List<BenchmarkResult> results, String file) throws IOException {
        try (PrintWriter w = new PrintWriter(new FileWriter(file))) {
            w.println("implementation,imageSize,threadCount,avgWallMs,minWallMs,maxWallMs,stdDevMs");
            for (BenchmarkResult r : results)
                w.printf("\"%s\",\"%s\",%d,%.3f,%.3f,%.3f,%.3f%n",
                        r.implName(), r.sizeLabel(), r.threadCount(),
                        r.avgWallMs(), r.minWallMs(), r.maxWallMs(), r.stdDevMs());
        }
    }

    public static void exportMemoryCsv(List<BenchmarkResult> results, String file) throws IOException {
        try (PrintWriter w = new PrintWriter(new FileWriter(file))) {
            w.println("implementation,imageSize,threadCount,heapBeforeBytes,heapAfterBytes,heapDeltaBytes");
            for (BenchmarkResult r : results)
                w.printf("\"%s\",\"%s\",%d,%d,%d,%d%n",
                        r.implName(), r.sizeLabel(), r.threadCount(),
                        r.heapBeforeBytes(), r.heapAfterBytes(),
                        r.heapAfterBytes() - r.heapBeforeBytes());
        }
    }

    public static void exportGcCsv(List<BenchmarkResult> results, String file) throws IOException {
        try (PrintWriter w = new PrintWriter(new FileWriter(file))) {
            w.println("implementation,imageSize,threadCount,gcCountDelta,gcTimeMsDelta,avgSystemLoad");
            for (BenchmarkResult r : results)
                w.printf("\"%s\",\"%s\",%d,%d,%d,%.4f%n",
                        r.implName(), r.sizeLabel(), r.threadCount(),
                        r.gcCountDelta(), r.gcTimeMsDelta(), r.avgSystemLoad());
        }
    }

    // ── data types ────────────────────────────────────────────────────────────────

    public record BenchmarkResult(
            String implName,
            String sizeLabel,
            int    threadCount,
            double avgWallMs,
            double minWallMs,
            double maxWallMs,
            double stdDevMs,
            long   heapBeforeBytes,
            long   heapAfterBytes,
            long   gcCountDelta,
            long   gcTimeMsDelta,
            double avgSystemLoad
    ) {}

    public record GCSnapshot(long count, long timeMs) {}

    private record ImplSpec(String name, ImageProcessingService service, int threadCount) {}
}
