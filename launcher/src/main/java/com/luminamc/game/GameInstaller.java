package com.luminamc.game;

import com.luminamc.config.LuminaPaths;
import com.luminamc.download.*;
import com.luminamc.instance.Instance;
import com.luminamc.instance.ModLoader;

import java.nio.file.Path;
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
    }
}
