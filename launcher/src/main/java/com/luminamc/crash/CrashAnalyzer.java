package com.luminamc.crash;

import java.util.List;

/**
 * Parses raw game-log lines and produces a human-readable diagnosis:
 * what went wrong, why, and what the user should do about it.
 */
public final class CrashAnalyzer {

    public enum Severity { ERROR, WARNING, INFO }

    public record Diagnosis(
            Severity severity,
            String title,
            String cause,
            String fix
    ) {
        /** True when the analyzer found a specific known pattern (vs. generic fallback). */
        public boolean isSpecific() { return severity == Severity.ERROR; }
    }

    /**
     * Analyzes the captured game output and returns the most relevant diagnosis.
     * Always returns a non-null result; falls back to a generic message if no
     * pattern matches.
     */
    public static Diagnosis analyze(List<String> lines) {
        // Scan every line for known fingerprints.
        String outOfMemory       = null;
        String duplicateMod      = null;
        String missingMod        = null;
        String wrongJava         = null;
        String foreignMod        = null;
        String modVersionIncompat = null;
        String missingClass      = null;
        String nativeError       = null;
        String openGlError       = null;
        String accessDenied      = null;
        String mixinError        = null;
        String genericException  = null;

        for (String raw : lines) {
            String l = raw.toLowerCase();

            // ── Out of memory ──────────────────────────────────────────
            if (l.contains("outofmemoryerror")) {
                outOfMemory = raw.trim();
            }
            // ── Duplicate mod id ───────────────────────────────────────
            if ((l.contains("duplicate") && l.contains("mod")) ||
                    l.contains("duplicatemodidexception")) {
                if (duplicateMod == null) duplicateMod = raw.trim();
            }
            // ── Missing / unresolved mod dependency ────────────────────
            if (l.contains("modresolutionexception") ||
                    (l.contains("could not find") && l.contains("mod")) ||
                    l.contains("requires mod") ||
                    (l.contains("missing") && l.contains("required mod"))) {
                if (missingMod == null) missingMod = raw.trim();
            }
            // ── Wrong Java version (launchwrapper on Java >8) ──────────
            if (l.contains("appclassloader cannot be cast") ||
                    l.contains("urlclassloader") ||
                    l.contains("unsupported class file major version")) {
                if (wrongJava == null) wrongJava = raw.trim();
            }
            // ── Library / mod from a foreign client (non-SemVer version) ──
            if (l.contains("isn't compatible with loader's extended semantic version") ||
                    (l.contains("could not parse version number component") && l.contains("mod"))) {
                if (foreignMod == null) foreignMod = raw.trim();
            }
            // ── Mod incompatible with this MC version ──────────────────
            if (l.contains("incompatible with minecraft") ||
                    (l.contains("requires") && l.contains("minecraft") && l.contains(">=")) ||
                    l.contains("incompatiblemodexception")) {
                if (modVersionIncompat == null) modVersionIncompat = raw.trim();
            }
            // ── Missing class (often loader mismatch or wrong MC version) ─
            if ((l.contains("classnotfoundexception") || l.contains("noclassdeffounderror")) &&
                    !l.contains("org_eclipse_jgit")) {
                if (missingClass == null) missingClass = raw.trim();
            }
            // ── Native / LWJGL issues ──────────────────────────────────
            if (l.contains("unsatisfiedlinkerror") || l.contains("lwjgl") && l.contains("failed")) {
                if (nativeError == null) nativeError = raw.trim();
            }
            // ── OpenGL / graphics driver issues ───────────────────────
            if (l.contains("opengl") && (l.contains("error") || l.contains("failed")) ||
                    l.contains("gl_invalid") || l.contains("pixelformat")) {
                if (openGlError == null) openGlError = raw.trim();
            }
            // ── Java security / access denied ─────────────────────────
            if (l.contains("accesscontrolexception") || l.contains("access denied")) {
                if (accessDenied == null) accessDenied = raw.trim();
            }
            // ── Mixin injection failure ────────────────────────────────
            if (l.contains("mixin") && (l.contains("failed") || l.contains("injection") || l.contains("error"))) {
                if (mixinError == null) mixinError = raw.trim();
            }
            // ── Generic exception fallback ─────────────────────────────
            if (genericException == null &&
                    (l.contains("caused by") || l.contains("exception in thread"))) {
                genericException = raw.trim();
            }
        }

        // ── Return the most specific match, highest priority first ──
        if (outOfMemory != null) {
            return new Diagnosis(Severity.ERROR,
                    "Not enough RAM",
                    "Minecraft ran out of memory and crashed (OutOfMemoryError).",
                    "Increase the maximum RAM in the instance settings. " +
                    "At least 2 GB is recommended; 4 GB for heavily modded instances.");
        }
        if (wrongJava != null) {
            return new Diagnosis(Severity.ERROR,
                    "Wrong Java version",
                    "This Minecraft version needs Java 8, but a newer Java was used " +
                    "(LegacyLauncher/launchwrapper only works on Java 8).",
                    "LuminaMC auto-downloads Java 8 for older versions. " +
                    "Make sure no manual Java override pointing to Java 11+ is set in Settings.");
        }
        if (duplicateMod != null) {
            return new Diagnosis(Severity.ERROR,
                    "Duplicate mod installed",
                    "Two copies of the same mod are in the mods folder — Fabric/Forge rejects this.",
                    "Open the mods folder for this instance and remove any duplicate .jar files " +
                    "(same mod, different version). Use the Import tool to avoid this automatically.");
        }
        if (missingMod != null) {
            return new Diagnosis(Severity.ERROR,
                    "Missing mod dependency",
                    "A mod requires another mod that isn't installed.",
                    "Check which mod is missing in the log above and install it via the Mods tab, " +
                    "or remove the mod that needs it.");
        }
        if (foreignMod != null) {
            return new Diagnosis(Severity.WARNING,
                    "Mod from another client detected",
                    "A file from a different Minecraft client (e.g. Luna Client, OptiFine suite) " +
                    "ended up in the mods folder. It uses a version format Fabric Loader doesn't understand.",
                    "Remove files that weren't installed through LuminaMC from the mods folder. " +
                    "Use the Import tool — it filters out incompatible files automatically.");
        }
        if (modVersionIncompat != null) {
            return new Diagnosis(Severity.ERROR,
                    "Mod incompatible with this Minecraft version",
                    "One or more mods are built for a different Minecraft version than this instance.",
                    "Check which mods are incompatible (look for 'requires minecraft' in the log) " +
                    "and update them or remove them.");
        }
        if (mixinError != null) {
            return new Diagnosis(Severity.ERROR,
                    "Mixin injection failed",
                    "A mod's Mixin failed to inject into Minecraft — usually caused by a mod built " +
                    "for a different Minecraft or loader version.",
                    "Update the failing mod or remove it. Mixing mods from different MC versions " +
                    "or clients is the most common cause.");
        }
        if (missingClass != null) {
            return new Diagnosis(Severity.ERROR,
                    "Missing class — loader or version mismatch",
                    "A required Java class couldn't be found. This usually means a mod built " +
                    "for Forge ended up in a Fabric instance (or vice versa), or the mod targets " +
                    "a different Minecraft version.",
                    "Make sure all mods in the mods folder match this instance's loader and MC version. " +
                    "Use the Import tool to filter mods automatically.");
        }
        if (nativeError != null) {
            return new Diagnosis(Severity.ERROR,
                    "Native library error (LWJGL)",
                    "A native game library (graphics/audio) failed to load.",
                    "Try deleting the instance's 'natives' folder and relaunching — " +
                    "LuminaMC will re-extract it automatically. Make sure your GPU drivers are up to date.");
        }
        if (openGlError != null) {
            return new Diagnosis(Severity.ERROR,
                    "OpenGL / graphics error",
                    "The game failed to initialize the graphics context.",
                    "Update your GPU drivers. If you're on a laptop, make sure Minecraft uses " +
                    "the dedicated GPU (not the integrated one). Lowering render distance and graphics " +
                    "settings may also help.");
        }
        if (accessDenied != null) {
            return new Diagnosis(Severity.ERROR,
                    "File access denied",
                    "Minecraft couldn't read or write a file — possibly blocked by antivirus or " +
                    "a permission issue on the game folder.",
                    "Temporarily disable your antivirus / Windows Defender and try again. " +
                    "If the problem persists, run LuminaMC as administrator once.");
        }
        if (genericException != null) {
            return new Diagnosis(Severity.ERROR,
                    "Crash — unknown cause",
                    trimLong(genericException, 120),
                    "Check the full log above for more details. Common fixes: remove recently " +
                    "added mods, clear the mods folder and re-add mods one by one, or reinstall the instance.");
        }

        // Absolute fallback.
        String last = lines.isEmpty() ? "The game exited unexpectedly." : lines.get(lines.size() - 1).trim();
        return new Diagnosis(Severity.WARNING,
                "Game exited with an error",
                trimLong(last, 120),
                "Check the full log for details. If this happens repeatedly, try reinstalling the instance.");
    }

    private static String trimLong(String s, int max) {
        return s.length() <= max ? s : s.substring(0, max) + "…";
    }
}
