package com.sismd.benchmark;

import com.sismd.model.BenchmarkResult;
import com.sismd.model.BenchmarkSummary;
import com.sismd.model.ImageData;
import com.sismd.service.ImageProcessingService;
import com.sismd.service.impl.CompletableFutureImageProcessingService;
import com.sismd.service.impl.ForkJoinImageProcessingService;
import com.sismd.service.impl.ManualThreadImageProcessingService;
import com.sismd.service.impl.SequentialImageProcessingService;
import com.sismd.service.impl.ThreadPoolImageProcessingService;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.lang.management.GarbageCollectorMXBean;
import java.lang.management.ManagementFactory;
import java.lang.management.MemoryMXBean;
import java.lang.management.OperatingSystemMXBean;
import com.sun.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.LongStream;

/**
 * Benchmarking harness for all histogram-equalization implementations.
 *
 * Uses the real photograph {@code input2.jpg} downscaled to four sizes
 * (Small 640×360, Medium 1920×1080, Large 4096×2304, Original 8192×4608).
 *
 * Run with: {@code make benchmark}
 *
 * Output (all auto-generated in {@code results/}):
 * {@code benchmark.csv} — single CSV with all metrics
 * {@code walltime.png} — wall-time comparison plot
 * {@code speedup.png} — speedup vs sequential plot
 * {@code gc_analysis.png} — GC pause / heap analysis plot
 */
public class BenchmarkRunner {

    private static final int WARMUP = 3;
    private static final int MEASURED = 10;

    private static final Path OUTPUT_DIR = Path.of("results");

    // ── entry point
    // ───────────────────────────────────────────────────────────────

    public static void main(String[] args) throws Exception {
        // ── Arrange ──────────────────────────────────────────────────────────
        printHeader();
        var images = BenchmarkImageLoader.loadAllSizes();
        var specs = buildSpecs();
        var gcName = ManagementFactory.getGarbageCollectorMXBeans().stream()
                .map(GarbageCollectorMXBean::getName)
                .collect(Collectors.joining(" + "));

        // ── Act ──────────────────────────────────────────────────────────────
        List<BenchmarkResult> results = new ArrayList<>();

        for (var imgEntry : images.entrySet()) {
            var sizeLabel = imgEntry.getKey();
            var image = imgEntry.getValue();

            System.out.printf("▶ %s  (%d×%d, %,d px)%n",
                    sizeLabel, image.getWidth(), image.getHeight(), image.getPixelCount());

            double seqAvgMs = -1;
            for (var spec : specs) {
                System.out.printf("  %-45s ", spec.name());
                System.out.flush();

                var r = runBenchmark(spec.service(), image, spec.name(), sizeLabel,
                        spec.threadCount(), gcName);
                results.add(r);

                if (spec.name().equals("Sequential"))
                    seqAvgMs = r.getAvgMs();

                System.out.println(r.toConsoleLine(seqAvgMs > 0 ? seqAvgMs : r.getAvgMs()));
            }
            System.out.println();
        }

        // ── Assert (write + plot) ───────────────────────────────────────────
        Files.createDirectories(OUTPUT_DIR);
        exportCsv(results, OUTPUT_DIR.resolve("benchmark.csv"));
        exportSummary(results, OUTPUT_DIR.resolve("summary.txt"));
        BenchmarkCharts.generateAll(results, OUTPUT_DIR);
        printFooter();
    }

    // ── benchmark core (AAA)
    // ──────────────────────────────────────────────────────

    public static BenchmarkResult runBenchmark(ImageProcessingService service,
            ImageData image,
            String name,
            String sizeLabel,
            int threadCount,
            String gcCollector) {
        // ── Arrange ──────────────────────────────────────────────────────────
        for (int i = 0; i < WARMUP; i++)
            service.process(image);
        System.gc();

        MemoryMXBean mem = ManagementFactory.getMemoryMXBean();
        OperatingSystemMXBean os = ManagementFactory.getOperatingSystemMXBean();
        com.sun.management.OperatingSystemMXBean sunOs = (com.sun.management.OperatingSystemMXBean) os;
        ThreadMXBean tmx = (ThreadMXBean) ManagementFactory.getThreadMXBean();

        long heapBefore = mem.getHeapMemoryUsage().getUsed();
        GCSnapshot gcBefore = collectGCSnapshot();
        long cpuNsBefore = sunOs.getProcessCpuTime();
        long allocBytesBefore = allocatedBytes(tmx);
        long[] times = new long[MEASURED];
        double loadSum = 0;

        // ── Act ──────────────────────────────────────────────────────────────
        for (int i = 0; i < MEASURED; i++) {
            long t0 = System.nanoTime();
            service.process(image);
            times[i] = System.nanoTime() - t0;
            loadSum += Math.max(0, os.getSystemLoadAverage());
        }

        // ── Assert (compute) ─────────────────────────────────────────────────
        long heapAfter = mem.getHeapMemoryUsage().getUsed();
        GCSnapshot gcAfter = collectGCSnapshot();
        double cpuTimeMs = Math.max(0, sunOs.getProcessCpuTime() - cpuNsBefore) / 1_000_000.0 / MEASURED;
        double allocatedMb = Math.max(0, allocatedBytes(tmx) - allocBytesBefore) / 1_048_576.0;

        var stats = LongStream.of(times).summaryStatistics();
        double avgMs = stats.getAverage() / 1_000_000.0;
        double minMs = stats.getMin() / 1_000_000.0;
        double maxMs = stats.getMax() / 1_000_000.0;
        double stdDev = Math.sqrt(
                LongStream.of(times)
                        .mapToDouble(t -> {
                            double d = t / 1_000_000.0 - avgMs;
                            return d * d;
                        })
                        .average().orElse(0));

        return BenchmarkResult.builder()
                .implName(name).sizeLabel(sizeLabel).threadCount(threadCount)
                .width(image.getWidth()).height(image.getHeight())
                .avgMs(avgMs).minMs(minMs).maxMs(maxMs).stdDevMs(stdDev)
                .cpuTimeMs(cpuTimeMs).allocatedMb(allocatedMb)
                .heapBeforeMb(heapBefore / 1_048_576.0)
                .heapAfterMb(heapAfter / 1_048_576.0)
                .gcCycles(gcAfter.count() - gcBefore.count())
                .gcPauseMs(gcAfter.timeMs() - gcBefore.timeMs())
                .sysLoad(loadSum / MEASURED)
                .gcCollector(gcCollector)
                .build();
    }

    // ── GC snapshot
    // ───────────────────────────────────────────────────────────────

    private static long allocatedBytes(ThreadMXBean tmx) {
        long[] ids = tmx.getAllThreadIds();
        long[] bytes = tmx.getThreadAllocatedBytes(ids);
        long sum = 0;
        for (long b : bytes)
            if (b > 0)
                sum += b;
        return sum;
    }

    public static GCSnapshot collectGCSnapshot() {
        long count = 0, timeMs = 0;
        for (var gc : ManagementFactory.getGarbageCollectorMXBeans()) {
            long c = gc.getCollectionCount();
            long t = gc.getCollectionTime();
            if (c >= 0)
                count += c;
            if (t >= 0)
                timeMs += t;
        }
        return new GCSnapshot(count, timeMs);
    }

    // ── spec builder
    // ──────────────────────────────────────────────────────────────

    private static List<ImplSpec> buildSpecs() {
        int cores = Runtime.getRuntime().availableProcessors();
        int[] threadCounts = deduplicate(new int[] { 1, 2, 4, 8, cores, 16 });

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

        for (int threshold : new int[] { 10, 50, 200 }) {
            specs.add(new ImplSpec("ForkJoin(threshold=" + threshold + ")",
                    new ForkJoinImageProcessingService(threshold), cores));
        }
        return specs;
    }

    private static int[] deduplicate(int[] src) {
        return Arrays.stream(src).distinct().sorted().toArray();
    }

    // ── CSV export (pure data — no comments, Excel/viewer-safe)
    // ─────────────────

    public static void exportCsv(List<BenchmarkResult> results, Path dest) throws IOException {
        // ── Arrange ──────────────────────────────────────────────────────────
        var seqBySize = results.stream()
                .filter(r -> r.getImplName().equals("Sequential"))
                .collect(Collectors.toMap(BenchmarkResult::getSizeLabel, BenchmarkResult::getAvgMs));

        var sizeOrder = results.stream()
                .map(BenchmarkResult::getSizeLabel)
                .distinct()
                .toList();

        var sorted = new ArrayList<>(results);
        sorted.sort(Comparator
                .<BenchmarkResult, Integer>comparing(r -> sizeOrder.indexOf(r.getSizeLabel()))
                .thenComparing(r -> r.getImplName().equals("Sequential") ? 0 : 1)
                .thenComparing(BenchmarkResult::getThreadCount)
                .thenComparing(BenchmarkResult::getImplName));

        // ── Act ──────────────────────────────────────────────────────────────
        try (var w = new PrintWriter(new FileWriter(dest.toFile()))) {
            w.println(BenchmarkResult.csvHeader());
            for (var r : sorted) {
                double seqMs = seqBySize.getOrDefault(r.getSizeLabel(), r.getAvgMs());
                w.println(r.toCsvRow(seqMs));
            }
        }

        // ── Assert ───────────────────────────────────────────────────────────
        System.out.printf("  ✓ Written %d rows → %s%n", sorted.size(), dest);
    }

    // ── Summary export (human-readable text report)
    // ──────────────────────────

    public static void exportSummary(List<BenchmarkResult> results, Path dest) throws IOException {
        var summary = BenchmarkSummary.of(results, WARMUP, MEASURED);
        Files.writeString(dest, summary.render());
        System.out.printf("  ✓ Summary     → %s%n", dest);
    }

    // ── console helpers
    // ───────────────────────────────────────────────────────────

    private static void printHeader() {
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║         SISMD Benchmark — Histogram Equalization        ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
        System.out.printf("  Date    : %s%n",
                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")));
        System.out.printf("  JVM     : %s%n", System.getProperty("java.version"));
        System.out.printf("  Cores   : %d%n", Runtime.getRuntime().availableProcessors());
        System.out.printf("  Source  : input2.jpg (downscaled to 4 sizes)%n%n");
    }

    private static void printFooter() {
        System.out.println();
        System.out.println("╔══════════════════════════════════════════════════════════╗");
        System.out.println("║  Done — results/benchmark.csv                           ║");
        System.out.println("║         results/summary.txt                             ║");
        System.out.println("║         results/*.png  (XChart)                         ║");
        System.out.println("╚══════════════════════════════════════════════════════════╝");
    }

    // ── data types
    // ────────────────────────────────────────────────────────────────

    public record GCSnapshot(long count, long timeMs) {
    }

    private record ImplSpec(String name, ImageProcessingService service, int threadCount) {
    }
}
