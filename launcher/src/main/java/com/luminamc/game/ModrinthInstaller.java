package com.luminamc.game;

import com.luminamc.download.Http;
import com.luminamc.download.ModrinthApi;
import com.luminamc.instance.Instance;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Installs a chosen Modrinth {@link ModrinthApi.Version} into a target folder
 * ({@code mods/}, {@code resourcepacks/}, {@code shaderpacks/}, …), automatically
 * pulling in every <em>required</em> dependency (Fabric API, libraries, …) —
 * resolved recursively and filtered to the given loader and the instance's
 * Minecraft version. Already-present files are skipped, so re-installing is
 * harmless.
 */
public final class ModrinthInstaller {

    private final ModrinthApi api = new ModrinthApi();

    /** Outcome of an install: how many main files and dependency files were downloaded. */
    public record Result(int installed, int dependencies, List<String> files) {}

    /**
     * @param targetDir folder the files are written to (created if missing)
     * @param loader    loader used to resolve dependency builds, or {@code null}
     *                  for non-loader content like resource packs and shaders
     */
    public Result install(Instance inst, Path targetDir, ModrinthApi.Version version,
                          String loader, Consumer<String> log) throws Exception {
        Files.createDirectories(targetDir);

        List<String> downloaded = new ArrayList<>();
        Set<String> seenProjects = new HashSet<>();
        Deque<ModrinthApi.Version> queue = new ArrayDeque<>();

        queue.add(version);
        if (version.projectId() != null && !version.projectId().isBlank()) seenProjects.add(version.projectId());

        int main = 0, deps = 0;
        while (!queue.isEmpty()) {
            ModrinthApi.Version v = queue.poll();
            boolean root = (v == version);
            if (v.file() == null) continue;

            String filename = v.file().filename();
            if (alreadyPresent(targetDir, filename)) {
                log.accept(filename + " already present — skipped.");
            } else {
                log.accept("Downloading " + filename + "…");
                Http.download(v.file().url(), targetDir.resolve(filename), v.file().sha1(), null);
                downloaded.add(filename);
                if (root) main++; else deps++;
            }

            for (ModrinthApi.Dependency d : v.dependencies()) {
                if (!"required".equals(d.type())) continue;
                queueDependency(d, loader, inst.mcVersion, seenProjects, queue, log);
            }
        }
        return new Result(main, deps, downloaded);
    }

    private void queueDependency(ModrinthApi.Dependency d, String loader, String mcVersion,
                                 Set<String> seenProjects, Deque<ModrinthApi.Version> queue,
                                 Consumer<String> log) {
        try {
            ModrinthApi.Version dv = null;
            if (d.versionId() != null) {
                dv = api.versionById(d.versionId());
            } else if (d.projectId() != null && !seenProjects.contains(d.projectId())) {
                dv = api.bestVersion(d.projectId(), loader, mcVersion);
            }
            if (dv == null) return;

            String pid = dv.projectId() != null && !dv.projectId().isBlank() ? dv.projectId() : d.projectId();
            if (pid != null && !seenProjects.add(pid)) return; // already queued/installed
            if (dv.file() != null) log.accept("• Required dependency: " + dv.file().filename());
            queue.add(dv);
        } catch (Exception e) {
            log.accept("• Couldn't resolve a dependency — skipped (" + e.getMessage() + ").");
        }
    }

    private boolean alreadyPresent(Path dir, String filename) {
        String target = filename.toLowerCase();
        try (Stream<Path> files = Files.list(dir)) {
            return files.anyMatch(p -> {
                String n = p.getFileName().toString().toLowerCase();
                return n.equals(target) || n.equals(target + ".disabled");
            });
        } catch (Exception e) {
            return false;
        }
    }
}
