package com.sismd.monitor.system;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Reads system info via JMX MXBeans + platform-specific OS commands.
 * CPU model is not exposed by JMX, so it falls back to platform CLIs:
 *   macOS   → sysctl -n machdep.cpu.brand_string
 *   Linux   → /proc/cpuinfo  (model name field)
 *   Windows → wmic cpu get name
 */
public class DefaultSystemInfoAdapter implements SystemInfoService {

    @Override
    public SystemInfoSnapshot read() {
        return SystemInfoSnapshot.builder()
                .cpuModel(readCpuModel())
                .cpuCores(Runtime.getRuntime().availableProcessors())
                .totalRamBytes(readTotalRam())
                .osName(System.getProperty("os.name", "?"))
                .osVersion(System.getProperty("os.version", "?"))
                .osArch(System.getProperty("os.arch", "?"))
                .jvmVersion(System.getProperty("java.version", "?"))
                .build();
    }

    private static long readTotalRam() {
        try {
            var os = ManagementFactory.getOperatingSystemMXBean();
            if (os instanceof com.sun.management.OperatingSystemMXBean sun) {
                return sun.getTotalMemorySize();
            }
        } catch (Exception ignored) {}
        return 0L;
    }

    private static String readCpuModel() {
        String os = System.getProperty("os.name", "").toLowerCase();
        try {
            if (os.contains("mac")) {
                return exec("sysctl", "-n", "machdep.cpu.brand_string");
            }
            if (os.contains("linux")) {
                return Files.lines(Path.of("/proc/cpuinfo"))
                        .filter(l -> l.startsWith("model name"))
                        .map(l -> l.substring(l.indexOf(':') + 1).trim())
                        .findFirst()
                        .orElse("Unknown");
            }
            if (os.contains("windows")) {
                String out = exec("wmic", "cpu", "get", "name");
                for (String line : out.split("\\r?\\n")) {
                    String t = line.trim();
                    if (!t.isEmpty() && !t.equalsIgnoreCase("Name")) return t;
                }
            }
        } catch (Exception ignored) {}
        return System.getProperty("os.arch", "Unknown");
    }

    private static String exec(String... cmd) throws Exception {
        Process process = new ProcessBuilder(cmd).redirectErrorStream(true).start();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            StringBuilder sb = new StringBuilder();
            String line;
            while ((line = r.readLine()) != null) sb.append(line).append("\n");
            return sb.toString().trim();
        }
    }
}
