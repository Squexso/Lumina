package com.luminamc.config;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/**
 * Frees disk space by deleting safely re-creatable data: mod-loader installer
 * caches, extracted natives, leftover {@code .part} downloads and old crash
 * reports. None of this affects instances or saved worlds — everything removed
 * is regenerated automatically on the next launch.
 */
public final class StorageCleaner {

    private StorageCleaner() {}

    /** Deletes caches and returns the number of bytes freed. */
    public static long cleanCaches() {
        long freed = 0;
        freed += deleteContents(LuminaPaths.modCache());
        freed += deleteContents(LuminaPaths.natives());
        freed += deletePartFiles(LuminaPaths.root());
        freed += trimCrashLogs(20);
        return freed;
    }

    /** Estimates how much could be freed right now (without deleting). */
    public static long reclaimable() {
        return dirSize(LuminaPaths.modCache()) + dirSize(LuminaPaths.natives());
    }

    private static long deleteContents(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) return 0;
        long freed = 0;
        try (Stream<Path> top = Files.list(dir)) {
            for (Path p : (Iterable<Path>) top::iterator) freed += deleteRecursively(p);
        } catch (IOException ignored) {}
        return freed;
    }

    private static long deletePartFiles(Path root) {
        if (root == null || !Files.isDirectory(root)) return 0;
        long freed = 0;
        try (Stream<Path> walk = Files.walk(root)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                if (Files.isRegularFile(p) && p.getFileName().toString().endsWith(".part")) {
                    freed += sizeOf(p);
                    try { Files.deleteIfExists(p); } catch (IOException ignored) {}
                }
            }
        } catch (IOException ignored) {}
        return freed;
    }

    /** Keeps the newest {@code keep} crash logs, deletes the rest. */
    private static long trimCrashLogs(int keep) {
        Path logs = LuminaPaths.logs();
        if (!Files.isDirectory(logs)) return 0;
        long freed = 0;
        try (Stream<Path> s = Files.list(logs)) {
            List<Path> crashes = s.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith("crash-") && n.endsWith(".log");
            }).sorted(Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed()).toList();
            for (int i = keep; i < crashes.size(); i++) {
                freed += sizeOf(crashes.get(i));
                try { Files.deleteIfExists(crashes.get(i)); } catch (IOException ignored) {}
            }
        } catch (IOException ignored) {}
        return freed;
    }

    private static long deleteRecursively(Path p) {
        long freed = 0;
        if (Files.isDirectory(p)) {
            try (Stream<Path> children = Files.list(p)) {
                for (Path c : (Iterable<Path>) children::iterator) freed += deleteRecursively(c);
            } catch (IOException ignored) {}
        } else {
            freed += sizeOf(p);
        }
        try { Files.deleteIfExists(p); } catch (IOException ignored) {}
        return freed;
    }

    private static long dirSize(Path root) {
        if (root == null || !Files.exists(root)) return 0;
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).mapToLong(StorageCleaner::sizeOf).sum();
        } catch (IOException e) { return 0; }
    }

    private static long sizeOf(Path p) {
        try { return Files.size(p); } catch (IOException e) { return 0; }
    }

    public static String human(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1f MB", mb);
        return String.format("%.2f GB", mb / 1024.0);
    }
}
