package com.sismd.benchmark;

import com.sismd.model.BenchmarkResult;
import org.knowm.xchart.*;
import org.knowm.xchart.style.Styler;
import org.knowm.xchart.style.lines.SeriesLines;
import org.knowm.xchart.style.markers.SeriesMarkers;

import java.awt.*;
import java.io.IOException;
import java.nio.file.Path;
import java.util.*;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Generates benchmark visualisation PNGs using XChart (pure Java, no native
 * deps).
 *
 * Produces:
 * walltime.png — avg wall-time per implementation and image size
 * speedup.png — speedup vs sequential baseline
 * gc_analysis.png — GC pause and heap delta for the largest image
 */
public final class BenchmarkCharts {

    private BenchmarkCharts() {
    }

    private static final String[] IMPL_PREFIXES = {
            "ManualThreads", "ThreadPool", "CompletableFuture", "ForkJoin"
    };
    private static final Color[] IMPL_COLORS = {
            new Color(68, 114, 196), // blue
            new Color(237, 125, 49), // orange
            new Color(112, 173, 71), // green
            new Color(165, 42, 42), // dark-red
    };

    // ── public API ───────────────────────────────────────────────────────────

    public static void generateAll(List<BenchmarkResult> results, Path outputDir) {
        // ── Arrange ──────────────────────────────────────────────────────────
        var sizeOrder = results.stream()
                .map(BenchmarkResult::getSizeLabel)
                .distinct()
                .toList();

        var seqBySize = results.stream()
                .filter(r -> r.getImplName().equals("Sequential"))
                .collect(Collectors.toMap(BenchmarkResult::getSizeLabel, BenchmarkResult::getAvgMs));

        // ── Act ──────────────────────────────────────────────────────────────
        for (var size : sizeOrder) {
            var subset = results.stream()
                    .filter(r -> r.getSizeLabel().equals(size))
                    .toList();

            String cleanSize = size.replaceAll("\\(.*", "");
            double seqMs = seqBySize.getOrDefault(size, 1.0);

            saveChart(wallTimeChart(subset, size), outputDir, "walltime_" + cleanSize);
            saveChart(speedupChart(subset, size, seqMs), outputDir, "speedup_" + cleanSize);
        }

        // Combined overview charts
        saveChart(walltimeOverview(results, sizeOrder, seqBySize), outputDir, "walltime_overview");
        saveChart(speedupOverview(results, sizeOrder, seqBySize), outputDir, "speedup_overview");

        // GC analysis for largest image
        var largestSize = sizeOrder.get(sizeOrder.size() - 1);
        var largeResults = results.stream()
                .filter(r -> r.getSizeLabel().equals(largestSize) && r.getThreadCount() >= 4)
                .toList();
        if (!largeResults.isEmpty()) {
            saveChart(gcPauseChart(largeResults, largestSize), outputDir, "gc_pause");
            saveChart(heapDeltaChart(largeResults, largestSize), outputDir, "heap_delta");
            saveChart(cpuEfficiencyChart(largeResults, largestSize), outputDir, "cpu_efficiency");
            saveChart(cpuTimeChart(largeResults, largestSize), outputDir, "cpu_time");
            saveChart(allocatedChart(largeResults, largestSize), outputDir, "allocated");
        }

        // ── Assert ───────────────────────────────────────────────────────────
        System.out.println("  ✓ Charts written to " + outputDir + "/");
    }

    // ── per-size wall time ───────────────────────────────────────────────────

    private static XYChart wallTimeChart(List<BenchmarkResult> subset, String sizeLabel) {
        var chart = new XYChartBuilder()
                .width(900).height(550)
                .title("Wall Time — " + sizeLabel)
                .xAxisTitle("Threads")
                .yAxisTitle("Avg Wall Time (ms)")
                .build();
        styleXY(chart);

        for (int i = 0; i < IMPL_PREFIXES.length; i++) {
            var prefix = IMPL_PREFIXES[i];
            var filtered = subset.stream()
                    .filter(r -> r.getImplName().startsWith(prefix))
                    .sorted(Comparator.comparingInt(BenchmarkResult::getThreadCount))
                    .toList();
            if (filtered.isEmpty())
                continue;

            var xs = filtered.stream().map(r -> (double) r.getThreadCount()).toList();
            var ys = filtered.stream().map(BenchmarkResult::getAvgMs).toList();
            var s = chart.addSeries(prefix, xs, ys);
            s.setLineColor(IMPL_COLORS[i]);
            s.setMarkerColor(IMPL_COLORS[i]);
        }

        // Sequential baseline
        var seq = subset.stream().filter(r -> r.getImplName().equals("Sequential")).findFirst();
        if (seq.isPresent()) {
            var maxT = subset.stream().mapToInt(BenchmarkResult::getThreadCount).max().orElse(16);
            var xs = List.of(1.0, (double) maxT);
            var ys = List.of(seq.get().getAvgMs(), seq.get().getAvgMs());
            var s = chart.addSeries("Sequential", xs, ys);
            s.setLineColor(Color.GRAY);
            s.setLineStyle(SeriesLines.DASH_DASH);
            s.setMarker(SeriesMarkers.NONE);
        }

        addAnnotation(chart, subset);
        return chart;
    }

    // ── per-size speedup ─────────────────────────────────────────────────────

    private static XYChart speedupChart(List<BenchmarkResult> subset, String sizeLabel, double seqMs) {
        var chart = new XYChartBuilder()
                .width(900).height(550)
                .title("Speedup — " + sizeLabel)
                .xAxisTitle("Threads")
                .yAxisTitle("Speedup (×)")
                .build();
        styleXY(chart);

        int maxT = subset.stream().mapToInt(BenchmarkResult::getThreadCount).max().orElse(16);

        // Ideal line
        var idealXs = List.of(1.0, (double) maxT);
        var idealYs = List.of(1.0, (double) maxT);
        var ideal = chart.addSeries("Ideal (linear)", idealXs, idealYs);
        ideal.setLineColor(new Color(200, 200, 200));
        ideal.setLineStyle(SeriesLines.DASH_DASH);
        ideal.setMarker(SeriesMarkers.NONE);

        for (int i = 0; i < IMPL_PREFIXES.length; i++) {
            var prefix = IMPL_PREFIXES[i];
            var filtered = subset.stream()
                    .filter(r -> r.getImplName().startsWith(prefix))
                    .sorted(Comparator.comparingInt(BenchmarkResult::getThreadCount))
                    .toList();
            if (filtered.isEmpty())
                continue;

            var xs = filtered.stream().map(r -> (double) r.getThreadCount()).toList();
            var ys = filtered.stream().map(r -> seqMs / r.getAvgMs()).toList();
            var s = chart.addSeries(prefix, xs, ys);
            s.setLineColor(IMPL_COLORS[i]);
            s.setMarkerColor(IMPL_COLORS[i]);
        }

        return chart;
    }

    // ── overview: all sizes on one chart ─────────────────────────────────────

    private static XYChart walltimeOverview(List<BenchmarkResult> results,
            List<String> sizeOrder,
            Map<String, Double> seqBySize) {
        var chart = new XYChartBuilder()
                .width(1200).height(700)
                .title("Wall Time Overview — Best per Implementation")
                .xAxisTitle("Image Size (megapixels)")
                .yAxisTitle("Avg Wall Time (ms)")
                .build();
        styleXY(chart);
        chart.getStyler().setYAxisLogarithmic(true);

        for (int i = 0; i < IMPL_PREFIXES.length; i++) {
            var prefix = IMPL_PREFIXES[i];
            List<Double> xs = new ArrayList<>();
            List<Double> ys = new ArrayList<>();
            for (var size : sizeOrder) {
                var best = results.stream()
                        .filter(r -> r.getSizeLabel().equals(size) && r.getImplName().startsWith(prefix))
                        .min(Comparator.comparingDouble(BenchmarkResult::getAvgMs));
                if (best.isPresent()) {
                    xs.add((double) best.get().getWidth() * best.get().getHeight() / 1_000_000.0);
                    ys.add(best.get().getAvgMs());
                }
            }
            if (!xs.isEmpty()) {
                var s = chart.addSeries(prefix, xs, ys);
                s.setLineColor(IMPL_COLORS[i]);
                s.setMarkerColor(IMPL_COLORS[i]);
            }
        }

        // Sequential
        List<Double> seqXs = new ArrayList<>(), seqYs = new ArrayList<>();
        for (var size : sizeOrder) {
            var seq = results.stream()
                    .filter(r -> r.getSizeLabel().equals(size) && r.getImplName().equals("Sequential"))
                    .findFirst();
            if (seq.isPresent()) {
                seqXs.add((double) seq.get().getWidth() * seq.get().getHeight() / 1_000_000.0);
                seqYs.add(seq.get().getAvgMs());
            }
        }
        if (!seqXs.isEmpty()) {
            var s = chart.addSeries("Sequential", seqXs, seqYs);
            s.setLineColor(Color.GRAY);
            s.setLineStyle(SeriesLines.DASH_DASH);
        }

        return chart;
    }

    private static XYChart speedupOverview(List<BenchmarkResult> results,
            List<String> sizeOrder,
            Map<String, Double> seqBySize) {
        var chart = new XYChartBuilder()
                .width(1200).height(700)
                .title("Best Speedup Overview — per Image Size")
                .xAxisTitle("Image Size (megapixels)")
                .yAxisTitle("Best Speedup (×)")
                .build();
        styleXY(chart);

        for (int i = 0; i < IMPL_PREFIXES.length; i++) {
            var prefix = IMPL_PREFIXES[i];
            List<Double> xs = new ArrayList<>();
            List<Double> ys = new ArrayList<>();
            for (var size : sizeOrder) {
                double seqMs = seqBySize.getOrDefault(size, 1.0);
                var best = results.stream()
                        .filter(r -> r.getSizeLabel().equals(size) && r.getImplName().startsWith(prefix))
                        .min(Comparator.comparingDouble(BenchmarkResult::getAvgMs));
                if (best.isPresent()) {
                    xs.add((double) best.get().getWidth() * best.get().getHeight() / 1_000_000.0);
                    ys.add(seqMs / best.get().getAvgMs());
                }
            }
            if (!xs.isEmpty()) {
                var s = chart.addSeries(prefix, xs, ys);
                s.setLineColor(IMPL_COLORS[i]);
                s.setMarkerColor(IMPL_COLORS[i]);
            }
        }

        return chart;
    }

    // ── GC / memory bar charts ───────────────────────────────────────────────

    private static CategoryChart gcPauseChart(List<BenchmarkResult> subset, String sizeLabel) {
        var chart = new CategoryChartBuilder()
                .width(900).height(450)
                .title("GC Pause Time — " + sizeLabel)
                .xAxisTitle("Implementation")
                .yAxisTitle("GC Pause (ms)")
                .build();
        styleCategory(chart);

        var names = subset.stream().map(BenchmarkResult::getImplName).toList();
        var values = subset.stream().map(r -> (Number) r.getGcPauseMs()).toList();
        var s = chart.addSeries("GC Pause", names, values);
        s.setFillColor(new Color(68, 114, 196));
        return chart;
    }

    private static CategoryChart heapDeltaChart(List<BenchmarkResult> subset, String sizeLabel) {
        var chart = new CategoryChartBuilder()
                .width(900).height(450)
                .title("Heap Delta — " + sizeLabel)
                .xAxisTitle("Implementation")
                .yAxisTitle("Heap Delta (MB)")
                .build();
        styleCategory(chart);

        var names = subset.stream().map(BenchmarkResult::getImplName).toList();
        var values = subset.stream()
                .map(r -> (Number) (r.getHeapAfterMb() - r.getHeapBeforeMb()))
                .toList();
        var s = chart.addSeries("Heap Delta", names, values);
        s.setFillColor(new Color(237, 125, 49));
        return chart;
    }

    private static CategoryChart cpuEfficiencyChart(List<BenchmarkResult> subset, String sizeLabel) {
        var chart = new CategoryChartBuilder()
                .width(900).height(450)
                .title("CPU Efficiency — " + sizeLabel)
                .xAxisTitle("Implementation")
                .yAxisTitle("CPU Efficiency (%)")
                .build();
        styleCategory(chart);
        chart.getStyler().setLegendVisible(false);
        var names = subset.stream().map(BenchmarkResult::getImplName).toList();
        var values = subset.stream().map(r -> (Number) r.getCpuEfficiencyPct()).toList();
        var s = chart.addSeries("CPU Efficiency %", names, values);
        s.setFillColor(new Color(112, 173, 71));
        return chart;
    }

    private static CategoryChart cpuTimeChart(List<BenchmarkResult> subset, String sizeLabel) {
        var chart = new CategoryChartBuilder()
                .width(900).height(450)
                .title("Avg CPU Time per Run — " + sizeLabel)
                .xAxisTitle("Implementation")
                .yAxisTitle("CPU Time (ms)")
                .build();
        styleCategory(chart);
        var names = subset.stream().map(BenchmarkResult::getImplName).toList();
        var values = subset.stream().map(r -> (Number) r.getCpuTimeMs()).toList();
        var s = chart.addSeries("CPU Time ms", names, values);
        s.setFillColor(new Color(68, 114, 196));
        return chart;
    }

    private static CategoryChart allocatedChart(List<BenchmarkResult> subset, String sizeLabel) {
        var chart = new CategoryChartBuilder()
                .width(900).height(450)
                .title("Allocated Memory — " + sizeLabel)
                .xAxisTitle("Implementation")
                .yAxisTitle("Allocated (MB)")
                .build();
        styleCategory(chart);
        var names = subset.stream().map(BenchmarkResult::getImplName).toList();
        var values = subset.stream().map(r -> (Number) r.getAllocatedMb()).toList();
        var s = chart.addSeries("Allocated MB", names, values);
        s.setFillColor(new Color(165, 42, 42));
        return chart;
    }

    // ── styling ──────────────────────────────────────────────────────────────

    private static void styleXY(XYChart chart) {
        var st = chart.getStyler();
        st.setLegendPosition(Styler.LegendPosition.OutsideE);
        st.setChartBackgroundColor(Color.WHITE);
        st.setPlotBackgroundColor(new Color(250, 250, 250));
        st.setPlotGridLinesColor(new Color(220, 220, 220));
        st.setMarkerSize(8);
        st.setChartTitleFont(new Font("SansSerif", Font.BOLD, 16));
        st.setAxisTitleFont(new Font("SansSerif", Font.PLAIN, 13));
    }

    private static void styleCategory(CategoryChart chart) {
        var st = chart.getStyler();
        st.setLegendVisible(false);
        st.setChartBackgroundColor(Color.WHITE);
        st.setPlotBackgroundColor(new Color(250, 250, 250));
        st.setPlotGridLinesColor(new Color(220, 220, 220));
        st.setXAxisLabelRotation(45);
        st.setChartTitleFont(new Font("SansSerif", Font.BOLD, 16));
        st.setAxisTitleFont(new Font("SansSerif", Font.PLAIN, 13));
    }

    // ── annotation helper ────────────────────────────────────────────────────

    private static void addAnnotation(XYChart chart, List<BenchmarkResult> subset) {
        var best = subset.stream()
                .min(Comparator.comparingDouble(BenchmarkResult::getAvgMs));
        var seq = subset.stream()
                .filter(r -> r.getImplName().equals("Sequential"))
                .findFirst();
        if (best.isPresent() && seq.isPresent()) {
            double speedup = seq.get().getAvgMs() / best.get().getAvgMs();
            chart.setTitle(chart.getTitle()
                    + String.format("  [best: %s %.1fms ×%.1f]",
                            best.get().getImplName(), best.get().getAvgMs(), speedup));
        }
    }

    // ── save helper ──────────────────────────────────────────────────────────

    private static void saveChart(Object chart, Path dir, String name) {
        try {
            if (chart instanceof XYChart xy) {
                BitmapEncoder.saveBitmap(xy, dir.resolve(name).toString(), BitmapEncoder.BitmapFormat.PNG);
            } else if (chart instanceof CategoryChart cat) {
                BitmapEncoder.saveBitmap(cat, dir.resolve(name).toString(), BitmapEncoder.BitmapFormat.PNG);
            }
            System.out.println("    ✓ " + name + ".png");
        } catch (IOException e) {
            System.err.println("    WARN: failed to write " + name + ".png: " + e.getMessage());
        }
    }
}
