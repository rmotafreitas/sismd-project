package com.sismd.model;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Aggregates a list of {@link BenchmarkResult} rows into a human-readable
 * summary report. Written to {@code results/summary.txt}.
 */
@Getter
@Builder
public class BenchmarkSummary {

        private final List<BenchmarkResult> results;
        private final int warmupRuns;
        private final int measuredRuns;

        // ── factory ──────────────────────────────────────────────────────────────

        public static BenchmarkSummary of(List<BenchmarkResult> results,
                        int warmup, int measured) {
                return BenchmarkSummary.builder()
                                .results(results)
                                .warmupRuns(warmup)
                                .measuredRuns(measured)
                                .build();
        }

        // ── render ────────────────────────────────────────────────────────────────

        public String render() {
                var sb = new StringBuilder();

                var seqBySize = results.stream()
                                .filter(r -> r.getImplName().equals("Sequential"))
                                .collect(Collectors.toMap(BenchmarkResult::getSizeLabel, r -> r));

                var sizeOrder = results.stream()
                                .map(BenchmarkResult::getSizeLabel)
                                .distinct()
                                .toList();

                String gcCollector = results.stream()
                                .map(BenchmarkResult::getGcCollector)
                                .filter(s -> s != null && !s.isBlank())
                                .findFirst().orElse("unknown");

                // ── header ────────────────────────────────────────────────────────────
                sb.append("═══════════════════════════════════════════════════════════\n");
                sb.append("  BENCHMARK SUMMARY\n");
                sb.append("═══════════════════════════════════════════════════════════\n");
                sb.append(String.format("  Date           : %s%n",
                                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
                sb.append(String.format("  JVM            : %s%n", System.getProperty("java.version")));
                sb.append(String.format("  CPU Cores      : %d%n", Runtime.getRuntime().availableProcessors()));
                sb.append(String.format("  GC Collector   : %s%n", gcCollector));
                sb.append(String.format("  Warmup runs    : %d%n", warmupRuns));
                sb.append(String.format("  Measured runs  : %d%n", measuredRuns));
                sb.append(String.format("  Total configs  : %d%n", results.size()));
                sb.append("\n");

                // ── per-size blocks ───────────────────────────────────────────────────
                for (var size : sizeOrder) {
                        var group = results.stream()
                                        .filter(r -> r.getSizeLabel().equals(size))
                                        .toList();
                        var seqResult = seqBySize.get(size);
                        double seqMs = seqResult != null ? seqResult.getAvgMs() : 0.0;
                        double seqSigma = seqResult != null ? seqResult.getStdDevMs() : 0.0;

                        var parallel = group.stream()
                                        .filter(r -> !r.getImplName().equals("Sequential"))
                                        .toList();

                        var bestWall = parallel.stream()
                                        .min(Comparator.comparingDouble(BenchmarkResult::getAvgMs));
                        var bestCpuEff = parallel.stream()
                                        .max(Comparator.comparingDouble(BenchmarkResult::getCpuEfficiencyPct));
                        var leastAlloc = parallel.stream()
                                        .min(Comparator.comparingDouble(BenchmarkResult::getAllocatedMb));
                        var worst = parallel.stream()
                                        .max(Comparator.comparingDouble(BenchmarkResult::getAvgMs));

                        double avgAll = group.stream().mapToDouble(BenchmarkResult::getAvgMs).average().orElse(0);
                        double avgCycles = parallel.stream().mapToLong(BenchmarkResult::getGcCycles).average()
                                        .orElse(0);
                        double avgPause = parallel.stream().mapToLong(BenchmarkResult::getGcPauseMs).average()
                                        .orElse(0);
                        double avgAlloc = parallel.stream().mapToDouble(BenchmarkResult::getAllocatedMb).average()
                                        .orElse(0);

                        sb.append(String.format("  ── %s ──%n", size));
                        sb.append(String.format(
                                        "    Sequential baseline : %.3f ms  σ=%.3f ms  cpu=%.1f ms  eff=%.0f%%%n",
                                        seqMs, seqSigma,
                                        seqResult != null ? seqResult.getCpuTimeMs() : 0.0,
                                        seqResult != null ? seqResult.getCpuEfficiencyPct() : 0.0));

                        bestWall.ifPresent(b -> sb.append(String.format(
                                        "    Best wall time      : %-35s → %.3f ms  ×%.2f  eff=%.0f%%  alloc=%.2f MB%n",
                                        b.getImplName(), b.getAvgMs(), b.getSpeedup(seqMs),
                                        b.getCpuEfficiencyPct(), b.getAllocatedMb())));

                        bestCpuEff.ifPresent(b -> {
                                boolean sameAsBest = bestWall.isPresent() &&
                                                bestWall.get().getImplName().equals(b.getImplName());
                                if (!sameAsBest)
                                        sb.append(String.format(
                                                        "    Best CPU efficiency : %-35s → eff=%.0f%%  %.3f ms  ×%.2f%n",
                                                        b.getImplName(), b.getCpuEfficiencyPct(),
                                                        b.getAvgMs(), b.getSpeedup(seqMs)));
                        });

                        leastAlloc.ifPresent(b -> {
                                boolean sameAsBest = bestWall.isPresent() &&
                                                bestWall.get().getImplName().equals(b.getImplName());
                                if (!sameAsBest)
                                        sb.append(String.format(
                                                        "    Least allocation    : %-35s → %.2f MB%n",
                                                        b.getImplName(), b.getAllocatedMb()));
                        });

                        worst.ifPresent(w -> sb.append(String.format(
                                        "    Worst wall time     : %-35s → %.3f ms%n",
                                        w.getImplName(), w.getAvgMs())));

                        sb.append(String.format("    Average (all)       : %.3f ms%n", avgAll));
                        sb.append(String.format(
                                        "    GC pressure (avg)   : %.0f cycles  %.0f ms pause  %.2f MB alloc/run%n",
                                        avgCycles, avgPause, avgAlloc));
                        sb.append("\n");
                }

                // ── global highlights ─────────────────────────────────────────────────
                sb.append("  ── GLOBAL HIGHLIGHTS ──────────────────────────────────\n");

                var globalBestWall = results.stream()
                                .filter(r -> !r.getImplName().equals("Sequential"))
                                .min(Comparator.comparingDouble(BenchmarkResult::getAvgMs));
                globalBestWall.ifPresent(b -> {
                        double seqMs = seqBySize.containsKey(b.getSizeLabel())
                                        ? seqBySize.get(b.getSizeLabel()).getAvgMs()
                                        : b.getAvgMs();
                        sb.append(String.format("  Fastest overall     : %-35s on %-20s → %.3f ms  ×%.2f%n",
                                        b.getImplName(), b.getSizeLabel(), b.getAvgMs(), b.getSpeedup(seqMs)));
                });

                var globalBestCpuEff = results.stream()
                                .filter(r -> !r.getImplName().equals("Sequential"))
                                .max(Comparator.comparingDouble(BenchmarkResult::getCpuEfficiencyPct));
                globalBestCpuEff.ifPresent(b -> sb.append(String.format(
                                "  Best CPU efficiency : %-35s on %-20s → %.0f%%%n",
                                b.getImplName(), b.getSizeLabel(), b.getCpuEfficiencyPct())));

                var globalLeastGc = results.stream()
                                .filter(r -> !r.getImplName().equals("Sequential"))
                                .min(Comparator.comparingLong(BenchmarkResult::getGcPauseMs));
                globalLeastGc.ifPresent(b -> sb.append(String.format(
                                "  Least GC pause      : %-35s on %-20s → %d ms%n",
                                b.getImplName(), b.getSizeLabel(), b.getGcPauseMs())));

                var globalLeastAlloc = results.stream()
                                .filter(r -> !r.getImplName().equals("Sequential"))
                                .min(Comparator.comparingDouble(BenchmarkResult::getAllocatedMb));
                globalLeastAlloc.ifPresent(b -> sb.append(String.format(
                                "  Least allocation    : %-35s on %-20s → %.2f MB%n",
                                b.getImplName(), b.getSizeLabel(), b.getAllocatedMb())));

                sb.append("═══════════════════════════════════════════════════════════\n");
                return sb.toString();
        }

        @Override
        public String toString() {
                return render();
        }
}
