#!/usr/bin/env bash
# ──────────────────────────────────────────────────────────────────────────────
# GC Tuning Benchmark — runs the full benchmark under each garbage collector.
#
# Each run produces:
#   results/gc_tuning/<GC>/benchmark.csv   — single CSV with all metrics + summary
#   results/gc_tuning/<GC>/*.png           — XChart plots (wall-time, speedup, GC)
#   results/gc_tuning/<GC>/gc.log          — verbose JVM GC log
#   results/gc_tuning/<GC>/console.log     — benchmark console output
#
# Usage:   ./benchmark-gc.sh          or   make benchmark-gc
# Requires: Maven, Java 21+
# ──────────────────────────────────────────────────────────────────────────────

set -euo pipefail

RESULTS_BASE="results/gc_tuning"
MAIN_CLASS="com.sismd.benchmark.BenchmarkRunner"

# ── GC configurations to compare ─────────────────────────────────────────────
#   G1GC       — default since Java 9; region-based, balanced throughput/latency
#   ZGC        — ultra-low-pause (<1 ms); concurrent, good for large heaps
#   SerialGC   — single-threaded; baseline for overhead comparison
#   ParallelGC — throughput-oriented; maximizes batch processing speed
# ──────────────────────────────────────────────────────────────────────────────
declare -A GC_CONFIGS
GC_CONFIGS=(
    ["G1GC"]="-XX:+UseG1GC -XX:MaxGCPauseMillis=200"
    ["ZGC"]="-XX:+UseZGC"
    ["SerialGC"]="-XX:+UseSerialGC"
    ["ParallelGC"]="-XX:+UseParallelGC"
)

echo "╔══════════════════════════════════════════════════════════════╗"
echo "║      GC Tuning Benchmark — Comparing Garbage Collectors     ║"
echo "╚══════════════════════════════════════════════════════════════╝"
echo ""

mvn compile -q

for gc_name in "${!GC_CONFIGS[@]}"; do
    gc_flags="${GC_CONFIGS[$gc_name]}"
    out_dir="$RESULTS_BASE/$gc_name"
    mkdir -p "$out_dir"

    echo "▶ Running benchmark with $gc_name ($gc_flags)…"
    echo "  Output → $out_dir/"

    # Arrange — override results dir so Java writes CSV + plots into out_dir
    MAVEN_OPTS="$gc_flags -Xmx4g -Xlog:gc*:file=$out_dir/gc.log:time,uptime,level,tags" \
        mvn exec:java \
            -Dexec.mainClass="$MAIN_CLASS" \
            -q 2>&1 | tee "$out_dir/console.log"

    # Act — copy generated CSV + PNG charts into the GC-specific folder
    [ -f results/benchmark.csv ] && cp results/benchmark.csv "$out_dir/"
    for png in results/*.png; do
        [ -f "$png" ] && cp "$png" "$out_dir/"
    done

    echo "  ✓ $gc_name complete"
    echo ""
done

# Assert — summary
echo "╔══════════════════════════════════════════════════════════════╗"
echo "║  Done — compare results across:                             ║"
for gc_name in "${!GC_CONFIGS[@]}"; do
    printf "║    %-56s ║\n" "$RESULTS_BASE/$gc_name/"
done
echo "╚══════════════════════════════════════════════════════════════╝"
