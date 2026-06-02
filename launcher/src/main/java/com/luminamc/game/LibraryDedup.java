package com.luminamc.game;

import java.nio.file.Path;
import java.util.*;

/**
 * De-duplicates a Minecraft classpath so each Maven artifact appears only once.
 *
 * <p>Mod loaders (Fabric, Forge, NeoForge) ship newer copies of libraries that
 * also exist in the vanilla manifest — most notably ASM. Two versions of the
 * same artifact on the classpath makes Fabric abort at startup with
 * {@code "duplicate ASM classes found on classpath"}, and can break Forge too.
 * Real launchers keep only the highest version of each
 * {@code artifact[:classifier]}.
 *
 * <p>Keys are derived from the jar <em>file name</em> (not the directory), so the
 * de-dup works even when vanilla libraries live under {@code ~/.luminamc/libraries}
 * while a loader's libraries live under the instance's own {@code libraries}
 * folder. Paths that don't look like {@code name-version.jar} (the client jar,
 * the version jar, anything custom) are kept untouched, preserving order.
 */
public final class LibraryDedup {

    private LibraryDedup() {}

    public static List<Path> dedupe(List<Path> classpath) {
        Map<String, String> bestVersion = new HashMap<>();
        Map<String, Path>   bestPath    = new HashMap<>();

        // Pass 1 — winning (highest) version per artifact key.
        for (Path p : classpath) {
            Parsed parsed = parse(p);
            if (parsed == null) continue;
            String prev = bestVersion.get(parsed.key);
            if (prev == null || compareVersions(parsed.version, prev) > 0) {
                bestVersion.put(parsed.key, parsed.version);
                bestPath.put(parsed.key, p);
            }
        }

        // Pass 2 — rebuild in order, emitting each artifact's winner once.
        List<Path> out = new ArrayList<>(classpath.size());
        Set<String> emitted = new HashSet<>();
        for (Path p : classpath) {
            Parsed parsed = parse(p);
            if (parsed == null) { out.add(p); continue; }   // non-library: untouched
            if (!emitted.add(parsed.key)) continue;          // already wrote winner
            out.add(bestPath.get(parsed.key));
        }
        return out;
    }

    // ── filename parsing ─────────────────────────────────────────────────

    private record Parsed(String key, String version) {}

    /**
     * Splits a jar file name into {@code artifact}, {@code version} and optional
     * {@code classifier}. Returns null when the name isn't a normal
     * {@code name-version[-classifier].jar} library (e.g. the {@code 1.21.10.jar}
     * client jar, whose first token is already the version).
     */
    private static Parsed parse(Path p) {
        String file = p.getFileName().toString();
        if (!file.endsWith(".jar")) return null;
        String base = file.substring(0, file.length() - 4);

        String[] t = base.split("-");
        int verIdx = -1;
        for (int i = 0; i < t.length; i++) {
            if (!t[i].isEmpty() && Character.isDigit(t[i].charAt(0))) { verIdx = i; break; }
        }
        if (verIdx <= 0) return null;   // no artifact name before a version → not a library

        String artifact   = String.join("-", Arrays.copyOfRange(t, 0, verIdx));
        String version    = t[verIdx];
        String classifier = verIdx + 1 < t.length
                ? String.join("-", Arrays.copyOfRange(t, verIdx + 1, t.length)) : "";
        return new Parsed(artifact + "|" + classifier, version);
    }

    // ── version comparison ───────────────────────────────────────────────

    /** Compares dotted/dashed version strings numerically where possible. */
    static int compareVersions(String a, String b) {
        if (a.equals(b)) return 0;
        String[] pa = a.split("[._+]");
        String[] pb = b.split("[._+]");
        int len = Math.max(pa.length, pb.length);
        for (int i = 0; i < len; i++) {
            String sa = i < pa.length ? pa[i] : "";
            String sb = i < pb.length ? pb[i] : "";
            Integer na = asInt(sa), nb = asInt(sb);
            int cmp;
            if (na != null && nb != null) cmp = Integer.compare(na, nb);
            else if (na != null)          cmp = 1;    // numeric outranks textual suffix
            else if (nb != null)          cmp = -1;
            else                          cmp = sa.compareTo(sb);
            if (cmp != 0) return cmp;
        }
        return 0;
    }

    private static Integer asInt(String s) {
        if (s.isEmpty()) return null;
        try { return Integer.parseInt(s); } catch (NumberFormatException e) { return null; }
    }
}
