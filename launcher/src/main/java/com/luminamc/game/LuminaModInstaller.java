package com.luminamc.game;

import com.luminamc.config.LuminaPaths;
import com.luminamc.instance.Instance;
import com.luminamc.instance.ModLoader;

import java.io.IOException;
import java.nio.file.*;
import java.util.Comparator;
import java.util.stream.Stream;

/**
 * Installs the LuminaMC client mod (FPS/armour/effects HUD, custom crosshair,
 * dynamic lights, chat tweaks + the Right-Shift control panel) into compatible
 * instances and cleans up the old skyblock-fishing jar that an earlier build
 * shipped.
 *
 * <p>The features mod currently targets <b>Fabric 1.21.10</b>; Forge/NeoForge and
 * other versions follow as their builds are produced. Where it isn't supported
 * yet, only the legacy cleanup runs.
 */
public final class LuminaModInstaller {

    /** The exact Minecraft version the current features-mod build targets. */
    private static final String SUPPORTED_MC = "1.21.10";

    public String prepare(Instance inst) {
        // Always clear out any old "lumina*.jar" (incl. the legacy skyblock mod).
        removeLegacyLuminaMod(inst);

        if (inst.loader == ModLoader.FABRIC && SUPPORTED_MC.equals(inst.mcVersion)) {
            Path jar = findBuiltJar();
            if (jar == null) {
                return "LuminaMC client jar not found — build the mod (./gradlew build in the repo root).";
            }
            try {
                Path modsDir = LuminaPaths.instanceMods(inst.id);
                Files.createDirectories(modsDir);
                Files.copy(jar, modsDir.resolve("luminamc-client.jar"), StandardCopyOption.REPLACE_EXISTING);
                return "Installed LuminaMC client (" + jar.getFileName() + ") — open it in-game with Right Shift.";
            } catch (IOException e) {
                return "Couldn't install the LuminaMC client: " + e.getMessage();
            }
        }
        return "LuminaMC client not yet available for " + inst.loader.displayName + " " + inst.mcVersion + ".";
    }

    /** Deletes any {@code lumina*.jar} the old installer left (skyblock or older client). */
    private void removeLegacyLuminaMod(Instance inst) {
        Path modsDir = LuminaPaths.instanceMods(inst.id);
        if (!Files.isDirectory(modsDir)) return;
        try (Stream<Path> files = Files.list(modsDir)) {
            for (Path p : (Iterable<Path>) files::iterator) {
                String n = p.getFileName().toString().toLowerCase();
                boolean legacy = (n.equals("lumina.jar") || n.startsWith("lumina-"))
                        && (n.endsWith(".jar") || n.endsWith(".jar.disabled"));
                if (legacy) { try { Files.deleteIfExists(p); } catch (IOException ignored) {} }
            }
        } catch (IOException ignored) {}
    }

    /** Finds the freshly-built mod jar in the repo's build output. */
    private Path findBuiltJar() {
        Path[] dirs = { Paths.get("..", "build", "libs"), Paths.get("build", "libs") };
        for (Path dir : dirs) {
            if (!Files.isDirectory(dir)) continue;
            try (Stream<Path> files = Files.list(dir)) {
                Path found = files
                        .filter(p -> {
                            String n = p.getFileName().toString();
                            return n.startsWith("lumina-") && n.endsWith(".jar")
                                    && !n.contains("-sources") && !n.contains("-dev");
                        })
                        .max(Comparator.comparing(p -> {
                            try { return Files.getLastModifiedTime(p); }
                            catch (IOException e) { return java.nio.file.attribute.FileTime.fromMillis(0); }
                        }))
                        .orElse(null);
                if (found != null) return found;
            } catch (IOException ignored) {}
        }
        return null;
    }
}
