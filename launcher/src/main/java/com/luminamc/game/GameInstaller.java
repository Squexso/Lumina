package com.luminamc.game;

import com.google.gson.JsonObject;
import com.luminamc.config.Json;
import com.luminamc.config.LuminaPaths;
import com.luminamc.download.*;
import com.luminamc.instance.Instance;
import com.luminamc.instance.ModLoader;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;

/**
 * Orchestrates everything needed before launch: resolve the vanilla version,
 * merge the chosen mod loader, download all files in parallel, and extract
 * native libraries.
 */
public final class GameInstaller {

    /** Progress phases surfaced to the UI. */
    public interface Listener extends DownloadManager.Progress {
        void phase(String description);
    }

    /**
     * Resolves the version and merges the mod loader — cheap metadata work only,
     * no large downloads yet. The caller validates Java against {@link ResolvedVersion#javaMajor}
     * before committing to {@link #fetchAll}.
     */
    public ResolvedVersion resolve(Instance inst, VersionManifest.Entry entry, String javaExe,
                                   Listener listener) throws Exception {
        listener.phase("Resolving " + inst.mcVersion + "…");
        ResolvedVersion rv = new MojangMeta().resolve(entry.url);

        Path gameDir = LuminaPaths.instanceGameDir(inst.id);
        switch (inst.loader) {
            case FABRIC -> {
                listener.phase("Resolving Fabric " + inst.loaderVersion + "…");
                new FabricMeta().applyTo(rv, inst.mcVersion, inst.loaderVersion);
            }
            case FORGE, NEOFORGE -> {
                listener.phase("Running " + inst.loader.displayName + " installer…");
                new ForgeLikeMeta().installAndMerge(
                        inst.loader, inst.mcVersion, inst.loaderVersion, gameDir, javaExe, rv);
            }
            case VANILLA -> { /* nothing extra */ }
        }

        // Mod loaders ship newer copies of libraries that vanilla also provides
        // (e.g. ASM). Two versions of the same artifact on the classpath makes
        // Fabric abort with "duplicate ASM classes" — keep only the highest.
        if (inst.loader != ModLoader.VANILLA) {
            List<Path> deduped = LibraryDedup.dedupe(rv.classpath);
            int removed = rv.classpath.size() - deduped.size();
            rv.classpath.clear();
            rv.classpath.addAll(deduped);
            if (removed > 0) listener.phase("Resolved classpath (removed " + removed + " duplicate librar"
                    + (removed == 1 ? "y" : "ies") + ")");
        }
        return rv;
    }

    /** Downloads every resolved file in parallel and extracts native libraries. */
    public void fetchAll(Instance inst, ResolvedVersion rv, int threads, Listener listener) throws Exception {
        listener.phase("Downloading game files (" + rv.downloads.size() + " files)…");
        new DownloadManager(threads).runBatch(rv.downloads, listener);

        listener.phase("Extracting native libraries…");
        rv.nativesDir = LuminaPaths.natives().resolve(inst.mcVersion + "-" + inst.id);
        NativeExtractor.extract(rv.nativeJars, rv.nativesDir);

        materializeLegacyAssets(inst, rv, listener);
    }

    /**
     * Old Minecraft (≤ 1.7.x) doesn't read the hashed asset object store — it needs the
     * assets laid out as real files. The asset index says how:
     * <ul>
     *   <li>{@code "virtual": true} (1.7.x) → copy objects to
     *       {@code assets/virtual/<indexId>/<path>} (referenced via {@code ${game_assets}}).</li>
     *   <li>{@code "map_to_resources": true} (≤ 1.5) → copy into {@code <gameDir>/resources/<path>}.</li>
     * </ul>
     * Modern versions (non-virtual) skip this entirely. Copies are size-guarded so reruns
     * are cheap and won't touch files a running game holds open.
     */
    private void materializeLegacyAssets(Instance inst, ResolvedVersion rv, Listener listener) throws IOException {
        if (rv.assetIndexId == null || rv.assetsRoot == null) return;
        Path indexFile = rv.assetsRoot.resolve("indexes").resolve(rv.assetIndexId + ".json");
        if (!Files.exists(indexFile)) return;
        JsonObject index = Json.read(indexFile, JsonObject.class, null);
        if (index == null || !index.has("objects")) return;

        boolean virtual = index.has("virtual") && index.get("virtual").getAsBoolean();
        boolean mapToResources = index.has("map_to_resources") && index.get("map_to_resources").getAsBoolean();
        if (!virtual && !mapToResources) return;

        Path targetRoot = mapToResources
                ? LuminaPaths.instanceGameDir(inst.id).resolve("resources")
                : rv.assetsRoot.resolve("virtual").resolve(rv.assetIndexId);
        listener.phase("Preparing legacy assets…");

        JsonObject objects = index.getAsJsonObject("objects");
        for (var entry : objects.entrySet()) {
            JsonObject obj = entry.getValue().getAsJsonObject();
            String hash = obj.get("hash").getAsString();
            Path src = rv.assetsRoot.resolve("objects").resolve(hash.substring(0, 2)).resolve(hash);
            if (!Files.exists(src)) continue;
            Path dst = targetRoot.resolve(entry.getKey());
            try {
                if (Files.exists(dst) && Files.size(dst) == Files.size(src)) continue;
                Files.createDirectories(dst.getParent());
                Files.copy(src, dst, StandardCopyOption.REPLACE_EXISTING);
            } catch (IOException ignored) {
                // A single asset failing (e.g. locked by a running game) must not block launch.
            }
        }
    }
}
