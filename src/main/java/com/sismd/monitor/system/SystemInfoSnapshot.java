package com.sismd.monitor.system;

import lombok.Builder;
import lombok.Getter;

import java.util.LinkedHashMap;
import java.util.Map;

@Getter
@Builder
public class SystemInfoSnapshot {

    @Builder.Default private final String cpuModel      = "Unknown";
    @Builder.Default private final int    cpuCores      = 1;
    @Builder.Default private final long   totalRamBytes = 0L;
    @Builder.Default private final String osName        = "";
    @Builder.Default private final String osVersion     = "";
    @Builder.Default private final String osArch        = "";
    @Builder.Default private final String jvmVersion    = "";

    public Map<String, String> asMap() {
        Map<String, String> m = new LinkedHashMap<>();
        m.put("CPU",   cpuModel);
        m.put("Cores", String.valueOf(cpuCores));
        m.put("RAM",   fmtRam(totalRamBytes));
        m.put("OS",    osName + " " + osVersion);
        m.put("Arch",  osArch);
        m.put("JVM",   jvmVersion);
        return m;
    }

    private static String fmtRam(long bytes) {
        if (bytes >= 1_073_741_824L) return String.format("%.1f GB", bytes / 1_073_741_824.0);
        return String.format("%.0f MB", bytes / 1_048_576.0);
    }
}
