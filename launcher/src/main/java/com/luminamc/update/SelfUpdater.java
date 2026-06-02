package com.luminamc.update;

import com.google.gson.JsonObject;
import com.luminamc.download.Http;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Optional;
import java.util.function.LongConsumer;

/**
 * Self-updater for the packaged LuminaMC app-image.
 *
 * <p>The update feed is a tiny JSON document served by the user's update server:
 * <pre>{ "version":"0.2.0", "url":"http://…/luminamc-launcher.jar", "sha1":"…", "notes":"…" }</pre>
 *
 * <p>When a newer version is advertised, {@link #downloadAndStage} fetches the new
 * application jar, verifies it, and writes a tiny helper script that waits for the
 * running app to exit, swaps the jar inside the installed {@code app/} folder, and
 * relaunches {@code LuminaMC.exe}. The launcher then exits so the swap can happen.
 */
public final class SelfUpdater {

    /** Version of the running build — read from the jar manifest, with a dev fallback. */
    public static final String CURRENT_VERSION = resolveVersion();

    public record UpdateInfo(String version, String downloadUrl, String notes, String sha1) {}

    private final String feedUrl;

    public SelfUpdater(String feedUrl) {
        this.feedUrl = feedUrl;
    }

    /** Returns an update if the feed advertises a newer semantic version. */
    public Optional<UpdateInfo> check() {
        try {
            JsonObject feed = Http.getJson(feedUrl);
            String latest = feed.get("version").getAsString();
            if (isNewer(latest, CURRENT_VERSION)) {
                return Optional.of(new UpdateInfo(
                        latest,
                        feed.has("url") ? feed.get("url").getAsString() : "",
                        feed.has("notes") ? feed.get("notes").getAsString() : "",
                        feed.has("sha1") && !feed.get("sha1").isJsonNull() ? feed.get("sha1").getAsString() : null));
            }
        } catch (Exception ignored) {
            // Offline, no feed, or server down — silently skip.
        }
        return Optional.empty();
    }

    /**
     * Downloads the new jar and stages an in-place swap that runs after this process
     * exits. Returns {@code true} if the update was staged (the caller should then
     * exit), or {@code false} if auto-apply isn't possible — e.g. running from a dev
     * classpath rather than a packaged install (the caller should then just point the
     * user at the download URL).
     */
    public boolean downloadAndStage(UpdateInfo info, LongConsumer onBytes) throws IOException, InterruptedException {
        Path installedJar = installedJar();
        if (installedJar == null) return false;               // not a packaged app-image
        Path appDir = installedJar.getParent();
        Path installRoot = appDir.getParent();
        Path exe = installRoot.resolve("LuminaMC.exe");
        if (!Files.exists(exe)) return false;

        if (info.downloadUrl() == null || info.downloadUrl().isBlank())
            throw new IOException("Update feed has no download URL.");

        Path staged = Files.createTempFile("luminamc-update-", ".jar");
        Http.download(info.downloadUrl(), staged, info.sha1(), onBytes::accept);

        writeAndRunSwapScript(staged, installedJar, exe);
        return true;
    }

    // ── swap-on-exit helper ────────────────────────────────────────────────

    private static void writeAndRunSwapScript(Path staged, Path target, Path exe) throws IOException {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        if (windows) {
            Path bat = Files.createTempFile("luminamc-update-", ".cmd");
            String script = String.join("\r\n",
                    "@echo off",
                    ":wait",
                    "tasklist /FI \"IMAGENAME eq LuminaMC.exe\" | find /I \"LuminaMC.exe\" >nul && (ping -n 2 127.0.0.1 >nul & goto wait)",
                    "copy /Y \"" + staged + "\" \"" + target + "\" >nul",
                    "del /Q \"" + staged + "\" >nul 2>&1",
                    "start \"\" \"" + exe + "\"",
                    "del \"%~f0\"",
                    "");
            Files.writeString(bat, script, StandardCharsets.US_ASCII);
            new ProcessBuilder("cmd", "/c", "start", "", "/min", bat.toString())
                    .directory(target.getParent().toFile())
                    .start();
        } else {
            Path sh = Files.createTempFile("luminamc-update-", ".sh");
            String script = String.join("\n",
                    "#!/bin/sh",
                    "sleep 1",
                    "cp -f \"" + staged + "\" \"" + target + "\"",
                    "rm -f \"" + staged + "\"",
                    "\"" + exe + "\" &",
                    "rm -- \"$0\"",
                    "");
            Files.writeString(sh, script, StandardCharsets.US_ASCII);
            sh.toFile().setExecutable(true);
            new ProcessBuilder("sh", sh.toString()).start();
        }
    }

    /** The running {@code luminamc-launcher.jar} inside an app-image, or null if unpackaged. */
    private static Path installedJar() {
        try {
            Path p = Paths.get(SelfUpdater.class.getProtectionDomain().getCodeSource().getLocation().toURI());
            if (Files.isRegularFile(p) && p.getFileName().toString().endsWith(".jar")) return p;
        } catch (Exception ignored) {}
        return null;
    }

    private static String resolveVersion() {
        String v = SelfUpdater.class.getPackage().getImplementationVersion();
        return (v == null || v.isBlank()) ? "0.1.0" : v;
    }

    // ── version comparison ──────────────────────────────────────────────────

    static boolean isNewer(String candidate, String current) {
        String[] a = candidate.split("[.\\-+]");
        String[] b = current.split("[.\\-+]");
        int n = Math.max(a.length, b.length);
        for (int i = 0; i < n; i++) {
            int x = i < a.length ? parse(a[i]) : 0;
            int y = i < b.length ? parse(b[i]) : 0;
            if (x != y) return x > y;
        }
        return false;
    }

    private static int parse(String s) {
        try { return Integer.parseInt(s.replaceAll("\\D", "")); } catch (Exception e) { return 0; }
    }
}
