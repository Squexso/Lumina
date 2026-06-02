package com.luminamc.instance;

import com.luminamc.config.Json;
import com.luminamc.config.LuminaPaths;

import java.io.IOException;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.stream.Stream;

/** CRUD over instances plus per-instance mod and screenshot operations. */
public final class InstanceManager {

    private final List<Instance> instances = new ArrayList<>();

    public List<Instance> all() {
        return instances;
    }

    public Instance get(String id) {
        return instances.stream().filter(i -> i.id.equals(id)).findFirst().orElse(null);
    }

    /** Reads every instance.json under the instances directory into memory. */
    public void loadAll() {
        instances.clear();
        LuminaPaths.mkdirs(LuminaPaths.instances());
        try (Stream<Path> dirs = Files.list(LuminaPaths.instances())) {
            dirs.filter(Files::isDirectory).forEach(dir -> {
                Path cfg = dir.resolve("instance.json");
                Instance i = Json.read(cfg, Instance.class, null);
                if (i != null && i.id != null) instances.add(i);
            });
        } catch (IOException ignored) {
            // No instances yet.
        }
        instances.sort(Comparator.comparingLong((Instance i) -> i.lastPlayed).reversed());
    }

    public Instance create(String name, String mcVersion, ModLoader loader) {
        Instance i = Instance.create(name, mcVersion, loader);
        LuminaPaths.mkdirs(LuminaPaths.instanceMods(i.id));
        LuminaPaths.mkdirs(LuminaPaths.instanceResourcePacks(i.id));
        LuminaPaths.mkdirs(LuminaPaths.instanceTexturePacks(i.id));
        LuminaPaths.mkdirs(LuminaPaths.instanceShaderPacks(i.id));
        LuminaPaths.mkdirs(LuminaPaths.instanceScreenshots(i.id));
        save(i);
        instances.add(0, i);
        return i;
    }

    public void save(Instance i) {
        Json.write(LuminaPaths.instanceConfig(i.id), i);
    }

    public void delete(Instance i) {
        instances.remove(i);
        deleteRecursively(LuminaPaths.instance(i.id));
        deleteCrashLogs(i.id);
    }

    /** Removes this instance's crash reports ({@code crash-<id>-*.log}) when it's deleted. */
    private void deleteCrashLogs(String id) {
        Path logs = LuminaPaths.logs();
        if (!Files.isDirectory(logs)) return;
        try (Stream<Path> files = Files.list(logs)) {
            files.filter(p -> {
                String n = p.getFileName().toString();
                return n.startsWith("crash-" + id + "-") && n.endsWith(".log");
            }).forEach(p -> { try { Files.deleteIfExists(p); } catch (IOException ignored) {} });
        } catch (IOException ignored) {}
    }

    // ── Mods ────────────────────────────────────────────────────────────────

    public List<Mod> listMods(Instance i) {
        List<Mod> mods = new ArrayList<>();
        Path dir = LuminaPaths.instanceMods(i.id);
        if (!Files.isDirectory(dir)) return mods;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile)
                 .filter(p -> {
                     String n = p.getFileName().toString();
                     return n.endsWith(".jar") || n.endsWith(".jar" + Mod.DISABLED_SUFFIX);
                 })
                 .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                 .forEach(p -> mods.add(new Mod(p)));
        } catch (IOException ignored) {}
        return mods;
    }

    /** Copies a jar from anywhere on disk into the instance's mods folder. */
    public Mod addMod(Instance i, Path source) throws IOException {
        Path dir = LuminaPaths.instanceMods(i.id);
        Files.createDirectories(dir);
        Path target = dir.resolve(source.getFileName());
        Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        return new Mod(target);
    }

    public void removeMod(Mod mod) throws IOException {
        Files.deleteIfExists(mod.path());
    }

    /** Enables/disables a mod by toggling the {@code .disabled} suffix. */
    public Mod setModEnabled(Mod mod, boolean enabled) throws IOException {
        if (mod.isEnabled() == enabled) return mod;
        String name = mod.fileName();
        Path target;
        if (enabled) {
            target = mod.path().resolveSibling(name.substring(0, name.length() - Mod.DISABLED_SUFFIX.length()));
        } else {
            target = mod.path().resolveSibling(name + Mod.DISABLED_SUFFIX);
        }
        Files.move(mod.path(), target, StandardCopyOption.REPLACE_EXISTING);
        return new Mod(target);
    }

    // ── Resource / texture packs ──────────────────────────────────────────

    /** Lists the packs (.zip archives, plus folders when {@code allowFolders}) in {@code dir}. */
    public List<Pack> listPacks(Path dir, boolean allowFolders) {
        List<Pack> packs = new ArrayList<>();
        if (!Files.isDirectory(dir)) return packs;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(p -> {
                     if (Files.isDirectory(p)) return allowFolders;
                     return p.getFileName().toString().toLowerCase().endsWith(".zip");
                 })
                 .sorted(Comparator.comparing(p -> p.getFileName().toString().toLowerCase()))
                 .forEach(p -> packs.add(new Pack(p)));
        } catch (IOException ignored) {}
        return packs;
    }

    /** Copies a pack archive or folder from anywhere on disk into {@code dir}. */
    public Pack addPack(Path dir, Path source) throws IOException {
        Files.createDirectories(dir);
        Path target = dir.resolve(source.getFileName());
        if (Files.isDirectory(source)) {
            copyRecursively(source, target);
        } else {
            Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
        }
        return new Pack(target);
    }

    public void removePack(Pack pack) throws IOException {
        Path p = pack.path();
        if (Files.isDirectory(p)) {
            deleteRecursively(p);
        } else {
            Files.deleteIfExists(p);
        }
    }

    private static void copyRecursively(Path source, Path target) throws IOException {
        try (Stream<Path> walk = Files.walk(source)) {
            for (Path p : (Iterable<Path>) walk::iterator) {
                Path dest = target.resolve(source.relativize(p).toString());
                if (Files.isDirectory(p)) {
                    Files.createDirectories(dest);
                } else {
                    Files.createDirectories(dest.getParent());
                    Files.copy(p, dest, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        }
    }

    // ── Screenshots ───────────────────────────────────────────────────────

    public List<Path> listScreenshots(Instance i) {
        List<Path> shots = new ArrayList<>();
        Path dir = LuminaPaths.instanceScreenshots(i.id);
        if (!Files.isDirectory(dir)) return shots;
        try (Stream<Path> files = Files.list(dir)) {
            files.filter(Files::isRegularFile)
                 .filter(p -> p.getFileName().toString().toLowerCase().endsWith(".png"))
                 .sorted(Comparator.comparing((Path p) -> lastModified(p)).reversed())
                 .forEach(shots::add);
        } catch (IOException ignored) {}
        return shots;
    }

    private static long lastModified(Path p) {
        try {
            return Files.getLastModifiedTime(p).toMillis();
        } catch (IOException e) {
            return 0L;
        }
    }

    private static void deleteRecursively(Path root) {
        if (!Files.exists(root)) return;
        try (Stream<Path> walk = Files.walk(root)) {
            walk.sorted(Comparator.reverseOrder()).forEach(p -> {
                try { Files.deleteIfExists(p); } catch (IOException ignored) {}
            });
        } catch (IOException ignored) {}
    }
}
