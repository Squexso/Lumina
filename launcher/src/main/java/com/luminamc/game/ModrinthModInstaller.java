package com.luminamc.game;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.luminamc.config.LuminaPaths;
import com.luminamc.download.Http;
import com.luminamc.instance.Instance;

import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Generic single-mod installer from Modrinth — fetches the newest build of a
 * project matching the instance's loader + Minecraft version and drops it into
 * the mods folder. Idempotent and non-fatal (skips gracefully if there's no
 * matching build or the network fails).
 */
public final class ModrinthModInstaller {

    private static final String VERSIONS = "https://api.modrinth.com/v2/project/";

    /** Installs {@code slug} for {@code inst}. {@code marker} detects an existing copy. */
    public void ensure(Instance inst, String slug, String marker, String displayName, Consumer<String> log) {
        try {
            Path modsDir = LuminaPaths.instanceMods(inst.id);
            Files.createDirectories(modsDir);
            if (alreadyInstalled(modsDir, marker)) {
                log.accept(displayName + " already present.");
                return;
            }

            String loaderName = switch (inst.loader) {
                case FABRIC -> "fabric"; case NEOFORGE -> "neoforge"; case FORGE -> "forge"; default -> null;
            };
            if (loaderName == null) return;

            JsonObject version = findVersion(slug, loaderName, inst.mcVersion);
            if (version == null) {
                log.accept(displayName + ": no build for " + inst.loader.displayName + " " + inst.mcVersion + " — skipped.");
                return;
            }
            JsonObject file = primaryFile(version);
            if (file == null) return;

            String url = file.get("url").getAsString();
            String filename = file.get("filename").getAsString();
            String sha1 = file.has("hashes") && file.getAsJsonObject("hashes").has("sha1")
                    ? file.getAsJsonObject("hashes").get("sha1").getAsString() : null;
            Http.download(url, modsDir.resolve(filename), sha1, null);
            log.accept("Installed " + displayName + " (" + filename + ").");
        } catch (Exception e) {
            log.accept(displayName + ": couldn't auto-install (" + e.getMessage() + ").");
        }
    }

    private boolean alreadyInstalled(Path modsDir, String marker) {
        try (Stream<Path> files = Files.list(modsDir)) {
            String m = marker.toLowerCase();
            return files.anyMatch(p -> {
                String n = p.getFileName().toString().toLowerCase();
                return n.contains(m) && (n.endsWith(".jar") || n.endsWith(".jar.disabled"));
            });
        } catch (Exception e) { return false; }
    }

    private JsonObject findVersion(String slug, String loader, String mc) throws Exception {
        String url = VERSIONS + slug + "/version?loaders=" + enc("[\"" + loader + "\"]")
                + "&game_versions=" + enc("[\"" + mc + "\"]");
        JsonArray arr;
        try { arr = Http.getJsonArray(url); } catch (Exception e) { return null; }
        JsonObject first = null;
        for (JsonElement el : arr) {
            JsonObject v = el.getAsJsonObject();
            if (first == null) first = v;
            if ("release".equals(v.has("version_type") ? v.get("version_type").getAsString() : "")) return v;
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

    private static String enc(String s) { return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8); }
}
