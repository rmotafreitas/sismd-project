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
                                .collect(Collectors.toMap(BenchmarkResult::getSizeLabel, BenchmarkResult::getAvgMs));

                var sizeOrder = results.stream()
                                .map(BenchmarkResult::getSizeLabel)
                                .distinct()
                                .toList();

                sb.append("═══════════════════════════════════════════════════════════\n");
                sb.append("  BENCHMARK SUMMARY\n");
                sb.append("═══════════════════════════════════════════════════════════\n");
                sb.append(String.format("  Date           : %s%n",
                                LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss"))));
                sb.append(String.format("  JVM            : %s%n", System.getProperty("java.version")));
                sb.append(String.format("  CPU Cores      : %d%n", Runtime.getRuntime().availableProcessors()));
                sb.append(String.format("  Warmup runs    : %d%n", warmupRuns));
                sb.append(String.format("  Measured runs  : %d%n", measuredRuns));
                sb.append(String.format("  Total configs  : %d%n", results.size()));
                sb.append("\n");

                for (var size : sizeOrder) {
                        var group = results.stream()
                                        .filter(r -> r.getSizeLabel().equals(size))
                                        .toList();
                        double seqMs = seqBySize.getOrDefault(size, 0.0);

                        var best = group.stream()
                                        .filter(r -> !r.getImplName().equals("Sequential"))
                                        .min(Comparator.comparingDouble(BenchmarkResult::getAvgMs));

                        var worst = group.stream()
                                        .filter(r -> !r.getImplName().equals("Sequential"))
                                        .max(Comparator.comparingDouble(BenchmarkResult::getAvgMs));

                        double avgAll = group.stream()
                                        .mapToDouble(BenchmarkResult::getAvgMs).average().orElse(0);

                        sb.append(String.format("  %s%n", size));
                        sb.append(String.format("    Sequential baseline : %.3f ms%n", seqMs));
                        if (best.isPresent()) {
                                sb.append(String.format("    Best parallel       : %-35s → %.3f ms  (×%.2f)%n",
                                                best.get().getImplName(), best.get().getAvgMs(),
                                                best.get().getSpeedup(seqMs)));
                        }
                        if (worst.isPresent()) {
                                sb.append(String.format("    Worst parallel      : %-35s → %.3f ms%n",
                                                worst.get().getImplName(), worst.get().getAvgMs()));
                        }
                        sb.append(String.format("    Average (all)       : %.3f ms%n", avgAll));
                        sb.append("\n");
                }

                // Global best
                var globalBest = results.stream()
                                .filter(r -> !r.getImplName().equals("Sequential"))
                                .min(Comparator.comparingDouble(BenchmarkResult::getAvgMs));
                if (globalBest.isPresent()) {
                        var b = globalBest.get();
                        double seqMs = seqBySize.getOrDefault(b.getSizeLabel(), b.getAvgMs());
                        sb.append(String.format("  OVERALL FASTEST: %s on %s → %.3f ms (×%.2f)%n",
                                        b.getImplName(), b.getSizeLabel(), b.getAvgMs(), b.getSpeedup(seqMs)));
                }

                sb.append("═══════════════════════════════════════════════════════════\n");
                return sb.toString();
        }

        @Override
        public String toString() {
                return render();
        }
}
