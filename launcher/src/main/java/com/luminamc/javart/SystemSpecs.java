package com.luminamc.javart;

import com.sun.management.OperatingSystemMXBean;

import java.lang.management.ManagementFactory;

/** Detects basic hardware specs so the launcher can pick sensible defaults (e.g. RAM). */
public final class SystemSpecs {

    private SystemSpecs() {}

    /** Total physical RAM in MB (best-effort; falls back to 8192). */
    public static int totalRamMb() {
        try {
            OperatingSystemMXBean os = (OperatingSystemMXBean) ManagementFactory.getOperatingSystemMXBean();
            return (int) Math.max(2048, os.getTotalMemorySize() / (1024 * 1024));
        } catch (Throwable t) {
            return 8192;
        }
    }

    /** Recommended max heap: ~half of system RAM, clamped to 2–8 GB, rounded to 512 MB. */
    public static int recommendedMaxRamMb() {
        long half = totalRamMb() / 2L;
        long clamped = Math.max(2048, Math.min(half, 8192));
        return (int) (clamped / 512 * 512);
    }
}
