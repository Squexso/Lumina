package com.luminamc.game;

import com.luminamc.config.LuminaPaths;
import com.luminamc.instance.Instance;
import com.luminamc.instance.ModLoader;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.*;
import java.util.function.Consumer;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Safely imports files from another Minecraft client folder into a LuminaMC instance.
 *
 * <p>Safe folders (saves, screenshots, resourcepacks, shaderpacks, fonts, backups) are
 * copied as-is.  The mods folder is filtered: only jars whose loader type matches the
 * target instance are kept.  Everything else (logs, crash-reports, coremods, foreign
 * jar libraries) is skipped.</p>
 */
public final class InstanceImporter {

    public enum Decision { COPY, SKIP, FILTER_MODS, WARN }

    public record FolderRule(String folderName, Decision decision, String reason) {}

    public record ModAnalysis(Path jar, boolean keep, String reason) {}

    /** Summary produced by {@link #analyze} before anything is copied. */
    public record ImportAnalysis(
            Path sourceFolder,
            List<FolderRule> folderRules,
            List<ModAnalysis> modResults,
            int totalToCopy,
            int totalSkipped
    ) {}

    private static final List<FolderRule> RULES = List.of(
            new FolderRule("saves",        Decision.COPY,        "World saves — always safe"),
            new FolderRule("screenshots",  Decision.COPY,        "Screenshots — always safe"),
            new FolderRule("resourcepacks",Decision.COPY,        "Resource packs — always safe"),
            new FolderRule("texturepacks", Decision.COPY,        "Old resource packs — always safe"),
            new FolderRule("shaderpacks",  Decision.COPY,        "Shader packs — always safe"),
            new FolderRule("fonts",        Decision.COPY,        "Fonts — always safe"),
            new FolderRule("backups",      Decision.COPY,        "World backups — always safe"),
            new FolderRule("mods",         Decision.FILTER_MODS, "Filtered: only compatible mods are kept"),
            new FolderRule("config",       Decision.WARN,        "Copied with warning: configs may conflict"),
            new FolderRule("logs",         Decision.SKIP,        "Skipped: game logs are not useful to import"),
            new FolderRule("crash-reports",Decision.SKIP,        "Skipped: crash reports from another client"),
            new FolderRule("coremods",     Decision.SKIP,        "Skipped: CoreMods are loader-specific and dangerous"),
            new FolderRule("libraries",    Decision.SKIP,        "Skipped: native libraries are not portable"),
            new FolderRule("natives",      Decision.SKIP,        "Skipped: native binaries are not portable"),
            new FolderRule("versions",     Decision.SKIP,        "Skipped: version metadata managed by LuminaMC"),
            new FolderRule("assets",       Decision.SKIP,        "Skipped: game assets managed by LuminaMC"),
            new FolderRule("runtime",      Decision.SKIP,        "Skipped: Java runtimes managed by LuminaMC"),
            new FolderRule("jre",          Decision.SKIP,        "Skipped: Java runtimes managed by LuminaMC")
    );

    private static final Map<String, Decision> RULE_MAP;
    static {
        Map<String, Decision> m = new HashMap<>();
        for (FolderRule r : RULES) m.put(r.folderName().toLowerCase(), r.decision());
        RULE_MAP = Collections.unmodifiableMap(m);
    }

    /**
     * Scans the source folder and decides what to copy, filter, or skip —
     * without touching any files yet.
     */
    public ImportAnalysis analyze(Path sourceFolder, Instance target) throws IOException {
        List<FolderRule> applicable = new ArrayList<>();
        List<ModAnalysis> modResults = new ArrayList<>();
        int copy = 0, skip = 0;

        if (!Files.isDirectory(sourceFolder)) {
            throw new IOException("Not a directory: " + sourceFolder);
        }

        try (DirectoryStream<Path> ds = Files.newDirectoryStream(sourceFolder)) {
            for (Path entry : ds) {
                if (!Files.isDirectory(entry)) continue;
                String name = entry.getFileName().toString().toLowerCase();
                FolderRule rule = RULES.stream()
                        .filter(r -> r.folderName().equalsIgnoreCase(name))
                        .findFirst()
                        .orElse(new FolderRule(name, Decision.SKIP, "Unknown folder — skipped for safety"));

                applicable.add(rule);

                switch (rule.decision()) {
                    case COPY, WARN -> {
                        copy += countFiles(entry);
                    }
                    case FILTER_MODS -> {
                        // Inspect every jar in the mods folder.
                        try (DirectoryStream<Path> mods = Files.newDirectoryStream(entry, "*.jar")) {
                            for (Path jar : mods) {
                                ModAnalysis ma = analyzeJar(jar, target.loader);
                                modResults.add(ma);
                                if (ma.keep()) copy++; else skip++;
                            }
                        }
                    }
                    case SKIP -> {
                        skip += countFiles(entry);
                    }
                }
            }
        }

        return new ImportAnalysis(sourceFolder, applicable, modResults, copy, skip);
    }

    /**
     * Executes the import: copies safe folders, filters mods, skips the rest.
     * All output is sent to {@code log}.
     */
    public void execute(ImportAnalysis analysis, Instance target, Consumer<String> log) throws IOException {
        Path gameDir = LuminaPaths.instanceGameDir(target.id);
        Files.createDirectories(gameDir);
        Path sourceFolder = analysis.sourceFolder();

        log.accept("[Import] Source: " + sourceFolder);
        log.accept("[Import] Target: " + gameDir);

        for (FolderRule rule : analysis.folderRules()) {
            Path src = sourceFolder.resolve(rule.folderName());
            if (!Files.isDirectory(src)) continue;

            switch (rule.decision()) {
                case COPY -> {
                    Path dst = gameDir.resolve(rule.folderName());
                    int copied = copyTree(src, dst);
                    log.accept("[Import] " + rule.folderName() + " — copied " + copied + " files");
                }
                case WARN -> {
                    Path dst = gameDir.resolve(rule.folderName());
                    int copied = copyTree(src, dst);
                    log.accept("[Import] " + rule.folderName() + " — copied " + copied + " files"
                            + " (warning: configs from another client may conflict)");
                }
                case FILTER_MODS -> {
                    Path dst = gameDir.resolve("mods");
                    Files.createDirectories(dst);
                    int kept = 0, dropped = 0;
                    for (ModAnalysis ma : analysis.modResults()) {
                        if (ma.keep()) {
                            Path out = dst.resolve(ma.jar().getFileName());
                            Files.copy(ma.jar(), out, StandardCopyOption.REPLACE_EXISTING);
                            log.accept("[Import] mods/" + ma.jar().getFileName() + " — kept (" + ma.reason() + ")");
                            kept++;
                        } else {
                            log.accept("[Import] mods/" + ma.jar().getFileName() + " — skipped (" + ma.reason() + ")");
                            dropped++;
                        }
                    }
                    log.accept("[Import] mods — kept " + kept + ", dropped " + dropped + " incompatible files");
                }
                case SKIP -> {
                    log.accept("[Import] " + rule.folderName() + " — skipped (" + rule.reason() + ")");
                }
            }
        }
        log.accept("[Import] Done.");
    }

    // ── mod jar inspection ──────────────────────────────────────────────────

    /**
     * Opens a mod jar and determines whether it's compatible with the given loader.
     * Checks for standard loader metadata files inside the zip.
     */
    private static ModAnalysis analyzeJar(Path jar, ModLoader target) {
        try (ZipFile zf = new ZipFile(jar.toFile())) {
            boolean hasFabricMeta  = zf.getEntry("fabric.mod.json") != null;
            boolean hasQuiltMeta   = zf.getEntry("quilt.mod.json") != null;
            boolean hasForge1      = zf.getEntry("META-INF/mods.toml") != null;   // Forge/NeoForge modern
            boolean hasForge2      = zf.getEntry("mcmod.info") != null;            // Forge legacy (≤1.12)
            boolean hasNeoForge    = hasForge1 && containsNeoForgeMarker(zf);
            boolean hasForgeOnly   = hasForge1 && !hasNeoForge;

            // JGit and similar foreign libraries — skip regardless of target.
            if (!hasFabricMeta && !hasQuiltMeta && !hasForge1 && !hasForge2) {
                // No known mod metadata at all — likely a library accidentally copied.
                return new ModAnalysis(jar, false, "not a mod (no loader metadata found)");
            }

            return switch (target) {
                case FABRIC -> {
                    if (hasFabricMeta || hasQuiltMeta)
                        yield new ModAnalysis(jar, true, "Fabric/Quilt mod");
                    yield new ModAnalysis(jar, false,
                            hasFabricMeta ? "Fabric mod" : "Forge/NeoForge mod — not compatible with Fabric");
                }
                case FORGE -> {
                    if (hasForgeOnly || hasForge2)
                        yield new ModAnalysis(jar, true, "Forge mod");
                    if (hasNeoForge)
                        yield new ModAnalysis(jar, false, "NeoForge mod — not compatible with Forge");
                    yield new ModAnalysis(jar, false, "not a Forge mod");
                }
                case NEOFORGE -> {
                    if (hasNeoForge || hasForge1)  // NeoForge accepts most modern Forge mods
                        yield new ModAnalysis(jar, true, "NeoForge/Forge mod");
                    yield new ModAnalysis(jar, false, "Fabric/Quilt mod — not compatible with NeoForge");
                }
                case VANILLA -> new ModAnalysis(jar, false, "Vanilla instance — no mods allowed");
            };
        } catch (Exception e) {
            return new ModAnalysis(jar, false, "could not read jar: " + e.getMessage());
        }
    }

    /** Checks for a NeoForge marker in the mods.toml manifest. */
    private static boolean containsNeoForgeMarker(ZipFile zf) {
        ZipEntry e = zf.getEntry("META-INF/neoforge.mods.toml");
        if (e != null) return true;
        // Also check if mods.toml mentions "neoforge" as modLoader.
        ZipEntry modsToml = zf.getEntry("META-INF/mods.toml");
        if (modsToml == null) return false;
        try (InputStream is = zf.getInputStream(modsToml)) {
            String content = new String(is.readAllBytes());
            return content.toLowerCase().contains("neoforge");
        } catch (Exception ex) { return false; }
    }

    // ── file utilities ──────────────────────────────────────────────────────

    private static int copyTree(Path src, Path dst) throws IOException {
        final int[] count = {0};
        Files.createDirectories(dst);
        Files.walkFileTree(src, new SimpleFileVisitor<>() {
            @Override
            public FileVisitResult preVisitDirectory(Path dir, BasicFileAttributes attrs) throws IOException {
                Files.createDirectories(dst.resolve(src.relativize(dir)));
                return FileVisitResult.CONTINUE;
            }
            @Override
            public FileVisitResult visitFile(Path file, BasicFileAttributes attrs) throws IOException {
                Path target = dst.resolve(src.relativize(file));
                Files.copy(file, target, StandardCopyOption.REPLACE_EXISTING);
                count[0]++;
                return FileVisitResult.CONTINUE;
            }
        });
        return count[0];
    }

    private static int countFiles(Path dir) {
        final int[] n = {0};
        try {
            Files.walkFileTree(dir, new SimpleFileVisitor<>() {
                @Override public FileVisitResult visitFile(Path f, BasicFileAttributes a) {
                    n[0]++; return FileVisitResult.CONTINUE;
                }
                @Override public FileVisitResult visitFileFailed(Path f, IOException e) {
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException ignored) {}
        return n[0];
    }
}
