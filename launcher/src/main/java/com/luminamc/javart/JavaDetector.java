package com.luminamc.javart;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Cross-platform JDK discovery. Scans {@code JAVA_HOME}, the running JVM, the
 * {@code PATH}, and well-known install roots on Windows, macOS and Linux. Each
 * candidate is verified by running {@code java -version} and parsing the output.
 */
public final class JavaDetector {

    private static final boolean WINDOWS = System.getProperty("os.name").toLowerCase().contains("win");
    private static final boolean MAC     = System.getProperty("os.name").toLowerCase().contains("mac");
    private static final String  BIN      = WINDOWS ? "java.exe" : "java";

    private static final Pattern VERSION = Pattern.compile("version \"([^\"]+)\"");

    /** Returns all distinct, verified runtimes, highest major version first. */
    public List<JavaRuntime> detectAll() {
        LinkedHashSet<Path> candidates = new LinkedHashSet<>();

        // 1. The JVM currently running the launcher.
        addJavaBin(candidates, Paths.get(System.getProperty("java.home")));

        // 2. JAVA_HOME.
        String javaHome = System.getenv("JAVA_HOME");
        if (javaHome != null && !javaHome.isBlank()) addJavaBin(candidates, Paths.get(javaHome));

        // 3. Anything on PATH.
        String path = System.getenv("PATH");
        if (path != null) {
            for (String dir : path.split(Pattern.quote(java.io.File.pathSeparator))) {
                if (dir.isBlank()) continue;
                Path p = Paths.get(dir, BIN);
                if (Files.isExecutable(p)) candidates.add(p);
            }
        }

        // 4. Well-known vendor install roots.
        for (Path root : commonRoots()) scanRoot(candidates, root);

        List<JavaRuntime> result = new ArrayList<>();
        Set<String> seenVersions = new HashSet<>();
        for (Path bin : candidates) {
            JavaRuntime rt = probe(bin);
            if (rt != null && seenVersions.add(rt.executable.toString())) {
                result.add(rt);
            }
        }
        result.sort(Comparator.comparingInt((JavaRuntime r) -> r.major).reversed());
        return result;
    }

    /** Picks the best runtime for a given Minecraft version (newer MC needs 17/21). */
    public JavaRuntime bestFor(List<JavaRuntime> runtimes, String mcVersion) {
        if (runtimes.isEmpty()) return null;
        int wanted = requiredMajor(mcVersion);
        return runtimes.stream()
                .filter(r -> r.major >= wanted)
                .min(Comparator.comparingInt(r -> r.major))   // smallest that still satisfies
                .orElse(runtimes.get(0));
    }

    /** Lowest installed runtime whose major is at least {@code requiredMajor}, or null. */
    public JavaRuntime bestForMajor(List<JavaRuntime> runtimes, int requiredMajor) {
        return runtimes.stream()
                .filter(r -> r.major >= requiredMajor)
                .min(Comparator.comparingInt(r -> r.major))
                .orElse(null);
    }

    /** An installed runtime whose major is exactly {@code major}, or null — used for
     *  legacy Minecraft (≤ 1.16) which only runs on Java 8, never a newer JDK. */
    public JavaRuntime exactMajor(List<JavaRuntime> runtimes, int major) {
        return runtimes.stream()
                .filter(r -> r.major == major)
                .findFirst()
                .orElse(null);
    }

    /** Heuristic minimum Java major for a Minecraft version. */
    public static int requiredMajor(String mcVersion) {
        if (mcVersion == null) return 21;
        String major = mcVersion.split("\\.")[0];
        if (!major.equals("1")) return 21;                 // 26.x.x and friends
        String[] parts = mcVersion.split("\\.");
        int minor = parts.length > 1 ? parseInt(parts[1]) : 0;
        if (minor >= 20) return 21;
        if (minor >= 18) return 17;
        if (minor >= 17) return 16;
        return 8;
    }

    /** Validates a user-supplied path (file or home dir) into a runtime, or null. */
    public JavaRuntime probePath(String userPath) {
        if (userPath == null || userPath.isBlank()) return null;
        Path p = Paths.get(userPath.trim());
        if (Files.isDirectory(p)) {
            Path bin = p.resolve("bin").resolve(BIN);
            if (Files.exists(bin)) return probe(bin);
            if (p.getFileName().toString().equals(BIN)) return probe(p);
            return probe(p.resolve(BIN));
        }
        return probe(p);
    }

    // ── internals ────────────────────────────────────────────────────────

    private void addJavaBin(Set<Path> out, Path home) {
        Path bin = home.resolve("bin").resolve(BIN);
        if (Files.exists(bin)) out.add(bin);
    }

    private List<Path> commonRoots() {
        List<Path> roots = new ArrayList<>();
        if (WINDOWS) {
            for (String env : new String[]{"ProgramFiles", "ProgramFiles(x86)", "LOCALAPPDATA"}) {
                String base = System.getenv(env);
                if (base == null) continue;
                roots.add(Paths.get(base, "Java"));
                roots.add(Paths.get(base, "Eclipse Adoptium"));
                roots.add(Paths.get(base, "Microsoft"));
                roots.add(Paths.get(base, "Zulu"));
                roots.add(Paths.get(base, "BellSoft"));
                roots.add(Paths.get(base, "Amazon Corretto"));
            }
        } else if (MAC) {
            roots.add(Paths.get("/Library/Java/JavaVirtualMachines"));
            roots.add(Paths.get(System.getProperty("user.home"), "Library/Java/JavaVirtualMachines"));
        } else {
            roots.add(Paths.get("/usr/lib/jvm"));
            roots.add(Paths.get("/usr/java"));
            roots.add(Paths.get(System.getProperty("user.home"), ".sdkman/candidates/java"));
        }
        return roots;
    }

    private void scanRoot(Set<Path> out, Path root) {
        if (!Files.isDirectory(root)) return;
        try (Stream<Path> children = Files.list(root)) {
            children.filter(Files::isDirectory).forEach(home -> {
                addJavaBin(out, home);
                // macOS bundle layout: <home>/Contents/Home/bin/java
                addJavaBin(out, home.resolve("Contents").resolve("Home"));
            });
        } catch (Exception ignored) {}
    }

    private JavaRuntime probe(Path bin) {
        if (bin == null || !Files.exists(bin)) return null;
        try {
            Process p = new ProcessBuilder(bin.toString(), "-version")
                    .redirectErrorStream(true)
                    .start();
            StringBuilder out = new StringBuilder();
            try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
                String line;
                while ((line = r.readLine()) != null) out.append(line).append('\n');
            }
            p.waitFor();
            String text = out.toString();
            Matcher m = VERSION.matcher(text);
            if (!m.find()) return null;
            String version = m.group(1);
            int major = parseMajor(version);
            String vendor = guessVendor(text, bin);
            boolean is64 = text.contains("64-Bit") || text.contains("64-bit") || !text.contains("Client");
            return new JavaRuntime(bin.toAbsolutePath().normalize(), version, major, vendor, is64);
        } catch (Exception e) {
            return null;
        }
    }

    private static int parseMajor(String version) {
        // "1.8.0_xxx" -> 8 ; "21.0.11" -> 21
        String[] parts = version.split("[._]");
        if (parts[0].equals("1") && parts.length > 1) return parseInt(parts[1]);
        return parseInt(parts[0]);
    }

    private static String guessVendor(String versionOutput, Path bin) {
        String low = versionOutput.toLowerCase();
        if (low.contains("temurin") || low.contains("adoptium")) return "Eclipse Temurin";
        if (low.contains("corretto")) return "Amazon Corretto";
        if (low.contains("zulu")) return "Azul Zulu";
        if (low.contains("graalvm")) return "GraalVM";
        if (low.contains("openjdk")) return "OpenJDK";
        if (low.contains("java(tm)") || low.contains("hotspot")) return "Oracle";
        String path = bin.toString().toLowerCase();
        if (path.contains("adoptium") || path.contains("temurin")) return "Eclipse Temurin";
        return "Unknown";
    }

    private static int parseInt(String s) {
        try { return Integer.parseInt(s.replaceAll("\\D", "")); } catch (Exception e) { return 0; }
    }
}
