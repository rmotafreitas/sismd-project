package com.sismd.repository;

import com.sismd.model.GenerationRecord;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.List;

/**
 * Exports a list of {@link GenerationRecord} rows to a clean, machine-readable
 * CSV.
 *
 * Column schema is intentionally aligned with
 * {@code BenchmarkResult.csvHeader()} so
 * that both the UI-generated and the CLI benchmark CSV can be loaded / compared
 * in the
 * same spreadsheet or analysis tool. Shared column names are identical; UI-only
 * columns (uuid, filenames, etc.) are simply absent from the benchmark CSV.
 */
public class CsvExporter {

    private static final String HEADER = "uuid,AlgorithmName,InputFile,OutputFile,CreatedAt," +
            "ImageWidth,ImageHeight,Pixels," +
            "WallTime_ms,CpuTime_ms,CpuEfficiency_pct,Allocated_MB," +
            "HeapBefore_MB,HeapAfter_MB,HeapDelta_MB," +
            "GC_Cycles,GC_Pause_ms,PeakThreads,GC_Collector";

    public void export(List<GenerationRecord> records, File dest) throws IOException {
        try (BufferedWriter w = new BufferedWriter(new FileWriter(dest))) {
            w.write(HEADER);
            w.newLine();
            for (GenerationRecord r : records) {
                w.write(toRow(r));
                w.newLine();
            }
        }
    }

    private static String toRow(GenerationRecord r) {
        return String.format(
                "%s,%s,%s,%s,%s,%d,%d,%d," +
                        "%d,%d,%.1f,%.2f," +
                        "%.1f,%.1f,%.1f," +
                        "%d,%d,%d,%s",
                escape(r.getUuid()),
                escape(r.getAlgorithmName()),
                escape(r.getInputFilename()),
                escape(r.getOutputFilename()),
                escape(r.getCreatedAt().toString()),
                r.getImageWidth(), r.getImageHeight(), r.getPixels(),
                r.getWallTimeMs(), r.getCpuTimeMs(), r.getCpuEfficiencyPct(), r.getAllocatedMb(),
                r.getHeapBeforeMb(), r.getHeapAfterMb(), r.getHeapDeltaMb(),
                r.getGcCycles(), r.getGcPauseMs(), r.getPeakThreads(),
                escape(r.getGcCollector()));
    }

    private static String escape(String v) {
        if (v == null)
            return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
