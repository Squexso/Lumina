package com.luminamc.javart;

import com.luminamc.config.LuminaPaths;
import com.luminamc.download.Http;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.util.Locale;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.stream.Stream;

/**
 * Downloads a Java runtime on demand so the user never has to install a JDK
 * manually. Uses the Eclipse Temurin (Adoptium) public API, which serves a JRE
 * for any major version and platform.
 *
 * <p>Runtimes are cached under {@code ~/.luminamc/jres/<major>/}; a second launch
 * with the same major reuses the extracted runtime instead of downloading again.
 */
public final class JreProvisioner {

    /** Progress messages for the UI. */
    @FunctionalInterface
    public interface Progress { void message(String text); }

    /**
     * Ensures a Java runtime of at least {@code major} exists locally and returns
     * the path to its {@code java} executable. Downloads + extracts on first use.
     */
    public Path ensureJava(int major, Progress progress) throws IOException, InterruptedException {
        Path home = LuminaPaths.root().resolve("jres").resolve(String.valueOf(major));

        // Already provisioned?
        Path existing = findJavaExe(home);
        if (existing != null) {
            progress.message("Using bundled Java " + major + ".");
            return existing;
        }

        Files.createDirectories(home);
        String os = os(), arch = arch();
        boolean zip = os.equals("windows");
        String url = "https://api.adoptium.net/v3/binary/latest/" + major
                + "/ga/" + os + "/" + arch + "/jre/hotspot/normal/eclipse";

        Path archive = home.resolve("runtime" + (zip ? ".zip" : ".tar.gz"));
        progress.message("Downloading Java " + major + " for " + os + "/" + arch + " (one-time, ~45 MB)…");

        long[] seen = {0};
        try {
            Http.download(url, archive, null, bytes -> {
                seen[0] += bytes;
                if (seen[0] % (4L << 20) < bytes) { // roughly every 4 MB
                    progress.message("Downloading Java " + major + "…  "
                            + (seen[0] >> 20) + " MB");
                }
            });
        } catch (IOException e) {
            throw new IOException("Couldn't download Java " + major + " automatically ("
                    + e.getMessage() + ").\nYou can install JDK " + major
                    + " manually and select it in Settings → Java.", e);
        }

        progress.message("Extracting Java " + major + "…");
        if (zip) unzip(archive, home); else untarGz(archive, home);
        try { Files.deleteIfExists(archive); } catch (IOException ignored) {}

        Path java = findJavaExe(home);
        if (java == null) {
            throw new IOException("Java " + major + " was downloaded but no executable was found after extraction.");
        }
        makeExecutable(java);
        progress.message("Java " + major + " is ready.");
        return java;
    }

    // ── platform detection ───────────────────────────────────────────────

    private static String os() {
        String n = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        if (n.contains("win")) return "windows";
        if (n.contains("mac") || n.contains("darwin")) return "mac";
        return "linux";
    }

    private static String arch() {
        String a = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (a.contains("aarch64") || a.contains("arm64")) return "aarch64";
        if (a.contains("arm")) return "arm";
        if (a.contains("64")) return "x64";
        return "x86";
    }

    private static String javaExeName() {
        return os().equals("windows") ? "java.exe" : "java";
    }

    // ── locate the java binary anywhere under the extraction root ─────────

    private Path findJavaExe(Path root) {
        if (!Files.isDirectory(root)) return null;
        String exe = javaExeName();
        try (Stream<Path> w = Files.walk(root)) {
            return w.filter(p -> p.getFileName().toString().equals(exe)
                            && p.getParent() != null
                            && p.getParent().getFileName().toString().equals("bin"))
                    .findFirst().orElse(null);
        } catch (IOException e) {
            return null;
        }
    }

    // ── extraction ────────────────────────────────────────────────────────

    private static void unzip(Path zip, Path dir) throws IOException {
        try (ZipInputStream zis = new ZipInputStream(Files.newInputStream(zip))) {
            ZipEntry e;
            while ((e = zis.getNextEntry()) != null) {
                Path out = dir.resolve(e.getName()).normalize();
                if (!out.startsWith(dir)) continue;           // zip-slip guard
                if (e.isDirectory()) {
                    Files.createDirectories(out);
                } else {
                    Files.createDirectories(out.getParent());
                    Files.copy(zis, out, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    /** Minimal USTAR tar reader over a gzip stream — enough for Adoptium archives. */
    private static void untarGz(Path targz, Path dir) throws IOException {
        try (InputStream in = new GZIPInputStream(Files.newInputStream(targz))) {
            byte[] header = new byte[512];
            while (true) {
                int read = readFully(in, header, 0, 512);
                if (read < 512) break;
                if (isZeroBlock(header)) break;               // end-of-archive

                String name = cString(header, 0, 100);
                if (name.isEmpty()) break;
                long size = parseOctal(header, 124, 12);
                char type = (char) header[156];

                Path out = dir.resolve(name).normalize();
                boolean inside = out.startsWith(dir);

                if (type == '5') {                            // directory
                    if (inside) Files.createDirectories(out);
                    skip(in, padded(0));
                } else if (type == '0' || type == 0) {        // regular file
                    if (inside) {
                        Files.createDirectories(out.getParent());
                        try (var os = Files.newOutputStream(out)) {
                            copyN(in, os, size);
                        }
                    } else {
                        skip(in, size);
                    }
                    skip(in, padded(size) - size);            // padding to 512
                } else {                                      // links/other → skip body
                    skip(in, padded(size));
                }
            }
        }
    }

    private static long padded(long size) {
        long rem = size % 512;
        return rem == 0 ? size : size + (512 - rem);
    }

    private static boolean isZeroBlock(byte[] b) {
        for (byte x : b) if (x != 0) return false;
        return true;
    }

    private static String cString(byte[] b, int off, int len) {
        int end = off;
        while (end < off + len && b[end] != 0) end++;
        return new String(b, off, end - off, java.nio.charset.StandardCharsets.UTF_8).trim();
    }

    private static long parseOctal(byte[] b, int off, int len) {
        String s = cString(b, off, len).trim();
        if (s.isEmpty()) return 0;
        try { return Long.parseLong(s, 8); } catch (NumberFormatException e) { return 0; }
    }

    private static int readFully(InputStream in, byte[] buf, int off, int len) throws IOException {
        int total = 0;
        while (total < len) {
            int r = in.read(buf, off + total, len - total);
            if (r < 0) break;
            total += r;
        }
        return total;
    }

    private static void copyN(InputStream in, java.io.OutputStream out, long n) throws IOException {
        byte[] buf = new byte[1 << 16];
        long left = n;
        while (left > 0) {
            int r = in.read(buf, 0, (int) Math.min(buf.length, left));
            if (r < 0) break;
            out.write(buf, 0, r);
            left -= r;
        }
    }

    private static void skip(InputStream in, long n) throws IOException {
        long left = n;
        byte[] buf = new byte[1 << 16];
        while (left > 0) {
            int r = in.read(buf, 0, (int) Math.min(buf.length, left));
            if (r < 0) break;
            left -= r;
        }
    }

    // ── permissions (unix) ────────────────────────────────────────────────

    private void makeExecutable(Path javaExe) {
        if (os().equals("windows")) return;
        Path bin = javaExe.getParent();
        try (Stream<Path> files = Files.list(bin)) {
            files.forEach(p -> p.toFile().setExecutable(true, false));
        } catch (IOException ignored) {}
        // jspawnhelper sometimes lives under lib/.
        try (Stream<Path> w = Files.walk(bin.getParent())) {
            w.filter(p -> p.getFileName().toString().equals("jspawnhelper"))
             .forEach(p -> p.toFile().setExecutable(true, false));
        } catch (IOException ignored) {}
    }
}
