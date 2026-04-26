package com.sismd.repository;

import com.sismd.model.GenerationRecord;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.SequencedSet;

public class CsvExporter {

    public void export(List<GenerationRecord> records, File dest) throws IOException {
        // collect all metric keys that appear across all records (preserving insertion order)
        SequencedSet<String> metricKeys = new LinkedHashSet<>();
        for (GenerationRecord r : records) {
            if (r.getMetrics() != null) metricKeys.addAll(r.getMetrics().keySet());
        }

        List<String> metricKeyList = new ArrayList<>(metricKeys);

        try (BufferedWriter w = new BufferedWriter(new FileWriter(dest))) {
            // header
            w.write("uuid,algorithmName,inputFilename,outputFilename," +
                    "wallTimeMs,imageWidth,imageHeight,createdAt");
            for (String k : metricKeyList) {
                w.write(",");
                w.write(escape(k));
            }
            w.newLine();

            for (GenerationRecord r : records) {
                w.write(String.join(",",
                        escape(r.getUuid()),
                        escape(r.getAlgorithmName()),
                        escape(r.getInputFilename()),
                        escape(r.getOutputFilename()),
                        String.valueOf(r.getWallTimeMs()),
                        String.valueOf(r.getImageWidth()),
                        String.valueOf(r.getImageHeight()),
                        escape(r.getCreatedAt().toString())
                ));
                for (String k : metricKeyList) {
                    w.write(",");
                    String val = r.getMetrics() != null ? r.getMetrics().getOrDefault(k, "") : "";
                    w.write(escape(val));
                }
                w.newLine();
            }
        }
    }

    private static String escape(String v) {
        if (v == null) return "";
        if (v.contains(",") || v.contains("\"") || v.contains("\n") || v.contains("\r")) {
            return "\"" + v.replace("\"", "\"\"") + "\"";
        }
        return v;
    }
}
