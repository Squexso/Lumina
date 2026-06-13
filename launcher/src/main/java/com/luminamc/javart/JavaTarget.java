package com.luminamc.javart;

/**
 * The Java runtime an instance must launch with — major version plus whether that
 * major is a hard requirement or just a floor.
 *
 * <p>Old Minecraft (1.16 and earlier, and anything booting through LegacyLauncher /
 * {@code net.minecraft.launchwrapper.Launch}) runs <b>only</b> on Java 8: launchwrapper
 * casts the system class loader to {@link java.net.URLClassLoader}, which stopped being
 * the system loader in Java 9, so a newer JDK crashes immediately with
 * {@code ClassCastException ... AppClassLoader cannot be cast to ... URLClassLoader}.
 * For those versions {@link #exact} is {@code true} and {@link #major} is 8 — a newer JDK
 * is <b>not</b> acceptable. Modern versions only set a floor (a newer JDK is fine).
 */
public final class JavaTarget {

    public final int major;
    public final boolean exact;   // true: runtime major must equal `major`; false: `major` or newer

    private JavaTarget(int major, boolean exact) {
        this.major = major;
        this.exact = exact;
    }

    /** Whether a detected runtime of {@code runtimeMajor} can launch this instance. */
    public boolean accepts(int runtimeMajor) {
        return exact ? runtimeMajor == major : runtimeMajor >= major;
    }

    /**
     * @param mcVersion     the instance's Minecraft version (e.g. "1.12.2", "1.21.11")
     * @param manifestMajor the major declared in the version JSON (defaults high when absent)
     * @param mainClass     the launch main class — LegacyLauncher signals a Java-8-only boot
     */
    public static JavaTarget forVersion(String mcVersion, int manifestMajor, String mainClass) {
        boolean legacy = JavaDetector.requiredMajor(mcVersion) <= 8     // ≤ 1.16 heuristic
                || (mainClass != null && mainClass.contains("launchwrapper"));
        if (legacy) return new JavaTarget(8, true);                     // capped: newer Java breaks it
        // Modern versions: the version JSON's javaVersion is authoritative (e.g. 1.20.4→17,
        // 1.20.5+→21). Trust it as the floor; a newer JDK is fine.
        return new JavaTarget(manifestMajor, false);
    }
}
