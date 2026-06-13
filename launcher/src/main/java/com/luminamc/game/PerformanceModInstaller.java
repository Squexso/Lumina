package com.luminamc.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.luminamc.config.LuminaPaths;
import com.luminamc.download.Http;
import com.luminamc.instance.Instance;
import com.luminamc.instance.ModLoader;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.List;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Auto-installs a well-maintained third-party performance mod (instead of a
 * custom one) when an instance has FPS Boost enabled. This is loader- and
 * version-aware and uses Modrinth:
 *
 * <ul>
 *   <li><b>Fabric</b> → VulkanMod (Vulkan renderer), else Lithium for versions it
 *       doesn't support. VulkanMod replaces the whole renderer, so it is used
 *       <em>instead of</em> Sodium (the two are mutually exclusive).</li>
 *   <li><b>NeoForge</b> → Sodium (NeoForge build), else Embeddium</li>
 *   <li><b>Forge</b> → Embeddium, else Rubidium, else (old versions like 1.12.2)
 *       FoamFix, else VanillaFix</li>
 * </ul>
 *
 * <p>The ordered fallbacks make this work across the whole version range: modern
 * loaders get Sodium/Embeddium, and very old Forge (where Sodium-family mods don't
 * exist) still gets the best maintained option on Modrinth. OptiFine is not on
 * Modrinth (no clean download), so it can't be auto-installed — drop it in by hand.
 * Skips gracefully if nothing compatible exists for the instance's version.
 */
public final class PerformanceModInstaller {

    private static final String MODRINTH = "https://api.modrinth.com/v2/project/";

    /** Known performance-mod jar name fragments — to detect an existing install. */
    private static final String[] KNOWN =
            {"vulkanmod", "sodium", "embeddium", "rubidium", "foamfix", "vanillafix", "optifine"};

    public void ensure(Instance inst, Consumer<String> log) {
        if (inst.loader == ModLoader.VANILLA) {
            log.accept("FPS Boost: vanilla can't load mods — using launcher-side options tweaks only.");
            return;
        }
        try {
            Path modsDir = LuminaPaths.instanceMods(inst.id);
            Files.createDirectories(modsDir);
            if (alreadyInstalled(modsDir)) {
                log.accept("Performance mod already present.");
                return;
            }

            String loaderName = switch (inst.loader) {
                case FABRIC   -> "fabric";
                case NEOFORGE -> "neoforge";
                case FORGE    -> "forge";
                default       -> null;
            };
            if (loaderName == null) return;

            List<String> candidates = switch (inst.loader) {
                // VulkanMod (Vulkan renderer) instead of Sodium; Lithium is a logic-only,
                // VulkanMod-compatible fallback for MC versions VulkanMod doesn't cover.
                case FABRIC   -> List.of("vulkanmod", "lithium");
                case NEOFORGE -> List.of("sodium", "embeddium");
                // Modern Forge → Embeddium/Rubidium; very old Forge (1.12.2 etc.)
                // has no Sodium-family mod, so fall back to the best maintained
                // optimisation mods that DO exist on Modrinth for those versions.
                case FORGE    -> List.of("embeddium", "rubidium", "foamfix", "vanillafix");
                default       -> List.of();
            };

            for (String slug : candidates) {
                JsonObject version = findVersion(slug, loaderName, inst.mcVersion);
                if (version == null) continue;
                JsonObject file = primaryFile(version);
                if (file == null) continue;

                String url = file.get("url").getAsString();
                String filename = file.get("filename").getAsString();
                String sha1 = file.has("hashes") && file.getAsJsonObject("hashes").has("sha1")
                        ? file.getAsJsonObject("hashes").get("sha1").getAsString() : null;
                Path dest = modsDir.resolve(filename);
                log.accept("FPS Boost: installing " + slug + " (" + filename + ")…");
                Http.download(url, dest, sha1, null);
                log.accept("Performance mod ready: " + filename);
                return;
            }
            log.accept("FPS Boost: no compatible performance mod found for " + inst.loader.displayName
                    + " " + inst.mcVersion + " — using launcher-side tweaks only.");
        } catch (Exception e) {
            log.accept("FPS Boost: couldn't auto-install a performance mod (" + e.getMessage() + ").");
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private boolean alreadyInstalled(Path modsDir) {
        try (Stream<Path> files = Files.list(modsDir)) {
            return files.anyMatch(p -> {
                String n = p.getFileName().toString().toLowerCase();
                if (!n.endsWith(".jar") && !n.endsWith(".jar.disabled")) return false;
                for (String k : KNOWN) if (n.contains(k)) return true;
                return false;
            });
        } catch (Exception e) {
            return false;
        }
    }

    private JsonObject findVersion(String slug, String loader, String mcVersion) throws Exception {
        String url = MODRINTH + slug + "/version"
                + "?loaders=" + enc("[\"" + loader + "\"]")
                + "&game_versions=" + enc("[\"" + mcVersion + "\"]");
        JsonArray arr;
        try {
            arr = Http.getJsonArray(url);
        } catch (Exception e) {
            return null; // project not found / network — try next candidate
        }
        JsonObject first = null;
        for (JsonElement el : arr) {
            JsonObject v = el.getAsJsonObject();
            if (first == null) first = v;
            String type = v.has("version_type") ? v.get("version_type").getAsString() : "";
            if ("release".equals(type)) return v;
        }
        return first;
    }

    private JsonObject primaryFile(JsonObject version) {
        JsonArray files = version.getAsJsonArray("files");
        if (files == null || files.isEmpty()) return null;
        for (JsonElement el : files) {
            JsonObject f = el.getAsJsonObject();
            if (f.has("primary") && f.get("primary").getAsBoolean()) return f;
        }
        return files.get(0).getAsJsonObject();
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
