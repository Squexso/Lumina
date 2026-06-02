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
 * Auto-installs the <b>Fabric API</b> into a Fabric instance's mods folder.
 *
 * <p>Almost every Fabric mod — including LuminaMC's own in-game mod — declares a
 * hard dependency on Fabric API, so Fabric refuses to start without it
 * ({@code "requires any version of fabric-api, which is missing"}). The correct
 * build for the instance's Minecraft version is fetched from the Modrinth API
 * and dropped next to the other mods, exactly as a user would do by hand.
 */
public final class FabricApiInstaller {

    private static final String MODRINTH_VERSIONS =
            "https://api.modrinth.com/v2/project/fabric-api/version";

    /**
     * Ensures Fabric API is present for {@code inst}. Idempotent — does nothing
     * if a fabric-api jar is already installed. Never throws; failures are logged
     * so a launch can still proceed (and fail loudly later if truly needed).
     */
    public void ensure(Instance inst, Consumer<String> log) {
        try {
            Path modsDir = LuminaPaths.instanceMods(inst.id);
            Files.createDirectories(modsDir);

            if (alreadyInstalled(modsDir)) {
                log.accept("Fabric API already present.");
                return;
            }

            log.accept("Fetching Fabric API for Minecraft " + inst.mcVersion + "…");
            JsonObject version = findVersion(inst.mcVersion);
            if (version == null) {
                log.accept("⚠ No Fabric API build found for " + inst.mcVersion
                        + " — mods needing it may not load. You can add it manually from Modrinth.");
                return;
            }

            JsonObject file = primaryFile(version);
            if (file == null) {
                log.accept("⚠ Fabric API version had no downloadable file — skipping.");
                return;
            }

            String url = file.get("url").getAsString();
            String filename = file.get("filename").getAsString();
            String sha1 = file.has("hashes") && file.getAsJsonObject("hashes").has("sha1")
                    ? file.getAsJsonObject("hashes").get("sha1").getAsString() : null;

            Path dest = modsDir.resolve(filename);
            Http.download(url, dest, sha1, null);
            log.accept("Installed Fabric API (" + filename + ").");
        } catch (Exception e) {
            log.accept("⚠ Could not auto-install Fabric API (" + e.getMessage()
                    + "). You can add it manually from Modrinth.");
        }
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private boolean alreadyInstalled(Path modsDir) {
        try (Stream<Path> files = Files.list(modsDir)) {
            return files.anyMatch(p -> {
                String n = p.getFileName().toString().toLowerCase();
                return (n.contains("fabric-api") || n.contains("fabric_api"))
                        && (n.endsWith(".jar") || n.endsWith(".jar.disabled"));
            });
        } catch (Exception e) {
            return false;
        }
    }

    /** Queries Modrinth for the newest Fabric API build matching the MC version. */
    private JsonObject findVersion(String mcVersion) throws Exception {
        String url = MODRINTH_VERSIONS
                + "?loaders=" + enc("[\"fabric\"]")
                + "&game_versions=" + enc("[\"" + mcVersion + "\"]");
        JsonArray arr = Http.getJsonArray(url);
        // Modrinth returns newest first; pick the first featured/release build,
        // else just the first entry.
        JsonObject firstRelease = null;
        for (JsonElement el : arr) {
            JsonObject v = el.getAsJsonObject();
            if (firstRelease == null) firstRelease = v;
            String type = v.has("version_type") ? v.get("version_type").getAsString() : "";
            if ("release".equals(type)) return v;
        }
        return firstRelease;
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
