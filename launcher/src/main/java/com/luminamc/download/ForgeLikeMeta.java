package com.luminamc.download;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.luminamc.config.Json;
import com.luminamc.config.LuminaPaths;
import com.luminamc.game.ResolvedVersion;
import com.luminamc.instance.ModLoader;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

/**
 * Forge and NeoForge support. Both ship an official installer jar; the cleanest
 * cross-version path is to download it and run it headlessly with
 * {@code --installClient}, then merge the version profile it produces into the
 * resolved vanilla version.
 *
 * <p>This is best-effort: the installer's headless flags vary by release, and
 * very old Forge versions predate {@code --installClient}. For those the user
 * is told to run the GUI installer once.
 */
public final class ForgeLikeMeta {

    private static final String FORGE_META =
            "https://maven.minecraftforge.net/net/minecraftforge/forge/maven-metadata.xml";
    private static final String FORGE_BASE =
            "https://maven.minecraftforge.net/net/minecraftforge/forge/";
    private static final String NEO_META =
            "https://maven.neoforged.net/releases/net/neoforged/neoforge/maven-metadata.xml";
    private static final String NEO_BASE =
            "https://maven.neoforged.net/releases/net/neoforged/neoforge/";

    private static final List<String> FALLBACK_REPOS = List.of(
            "https://maven.minecraftforge.net/",
            "https://maven.neoforged.net/releases/",
            "https://libraries.minecraft.net/",
            "https://repo1.maven.org/maven2/");

    private static final Pattern VERSION_TAG = Pattern.compile("<version>(.*?)</version>");

    /** Lists loader builds. For Forge, only builds matching {@code mcVersion}. */
    public List<String> listVersions(ModLoader loader, String mcVersion) throws IOException, InterruptedException {
        String xml = Http.getString(loader == ModLoader.FORGE ? FORGE_META : NEO_META);
        List<String> all = new ArrayList<>();
        Matcher m = VERSION_TAG.matcher(xml);
        while (m.find()) all.add(m.group(1));
        List<String> out = new ArrayList<>();
        if (loader == ModLoader.FORGE) {
            String prefix = mcVersion + "-";
            for (String v : all) if (v.startsWith(prefix)) out.add(v.substring(prefix.length()));
        } else {
            // NeoForge version A.B.C maps to Minecraft 1.A.B — only show matching builds
            // so the user can't accidentally pick a build for a different MC version.
            String prefix = neoforgePrefix(mcVersion);
            for (String v : all) if (prefix == null || v.startsWith(prefix)) out.add(v);
        }
        java.util.Collections.reverse(out); // newest first
        return out;
    }

    /**
     * Maps a Minecraft version to the NeoForge build prefix. Classic scheme
     * {@code 1.21.10} → {@code 21.10.}; next-gen {@code 26.1.2} → {@code 26.1.}.
     * Returns a non-null prefix for any parseable version so the UI filter and the
     * mismatch guard stay consistent (a null prefix would disable both).
     */
    private static String neoforgePrefix(String mcVersion) {
        if (mcVersion == null) return null;
        String[] p = mcVersion.split("\\.");
        if (p.length < 2) return null;
        if (p[0].equals("1")) {
            String patch = p.length >= 3 ? p[2] : "0";
            return p[1] + "." + patch + ".";
        }
        // Next-gen line (e.g. 26.x): NeoForge would mirror the major.minor.
        return p[0] + "." + p[1] + ".";
    }

    private String installerUrl(ModLoader loader, String mcVersion, String loaderVersion) {
        if (loader == ModLoader.FORGE) {
            String full = mcVersion + "-" + loaderVersion;
            return FORGE_BASE + full + "/forge-" + full + "-installer.jar";
        }
        return NEO_BASE + loaderVersion + "/neoforge-" + loaderVersion + "-installer.jar";
    }

    /**
     * Downloads and runs the installer against {@code gameDir}, then merges the
     * produced version profile into {@code rv}. Returns the produced version id.
     */
    public String installAndMerge(ModLoader loader, String mcVersion, String loaderVersion,
                                  Path gameDir, String javaExe, ResolvedVersion rv)
            throws IOException, InterruptedException {

        // Guard against a version mismatch (e.g. NeoForge 21.1.x — which is for
        // Minecraft 1.21.1 — selected on a 1.21.10 instance). That mixes
        // incompatible libraries and crashes deep inside Minecraft with a cryptic
        // NoSuchMethodError. Catch it early with a clear, actionable message.
        if (loader == ModLoader.NEOFORGE) {
            String prefix = neoforgePrefix(mcVersion);
            if (prefix != null && !loaderVersion.startsWith(prefix)) {
                throw new IOException("This instance is set to Minecraft " + mcVersion
                        + ", but its NeoForge version " + loaderVersion + " is for a different "
                        + "Minecraft version.\n\nDelete this instance and create a new one — the "
                        + "version list now only offers matching NeoForge builds (" + prefix + "x).");
            }
        }

        Files.createDirectories(gameDir);
        // The installer expects a vanilla launcher layout; provide the minimum.
        Path profiles = gameDir.resolve("launcher_profiles.json");
        if (!Files.exists(profiles)) {
            Files.writeString(profiles, "{\"profiles\":{},\"settings\":{},\"version\":3}");
        }

        String url = installerUrl(loader, mcVersion, loaderVersion);
        Path installer = LuminaPaths.modCache().resolve(
                loader.name().toLowerCase() + "-" + loaderVersion + "-installer.jar");
        // Reuse an already-downloaded installer instead of overwriting it — a leftover
        // lock from a previous run otherwise causes "the file is used by another process".
        boolean haveInstaller = false;
        try { haveInstaller = Files.exists(installer) && Files.size(installer) > 0; }
        catch (IOException ignored) {}
        if (!haveInstaller) {
            Http.download(url, installer, null, null);
        }

        Process p = new ProcessBuilder(javaExe, "-jar", installer.toString(),
                "--installClient", gameDir.toString())
                .redirectErrorStream(true)
                .start();
        StringBuilder log = new StringBuilder();
        try (BufferedReader r = new BufferedReader(new InputStreamReader(p.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = r.readLine()) != null) log.append(line).append('\n');
        }
        int code = p.waitFor();
        if (code != 0) {
            throw new IOException("Installer for " + loader.displayName + " " + loaderVersion
                    + " exited with code " + code + ".\n" + log);
        }

        String versionId = findInstalledVersion(gameDir, mcVersion);
        if (versionId == null) {
            throw new IOException("Installer ran but no version profile inheriting from "
                    + mcVersion + " was found under " + gameDir.resolve("versions"));
        }
        mergeProfile(gameDir, versionId, rv);
        return versionId;
    }

    /**
     * Finds the version folder the installer produced. Prefers an exact
     * {@code inheritsFrom == mcVersion} match, but falls back leniently to any
     * Forge/NeoForge-named profile or any profile that inherits from a vanilla
     * version, since the installer's naming and {@code inheritsFrom} format vary
     * across releases.
     */
    private String findInstalledVersion(Path gameDir, String mcVersion) {
        Path versions = gameDir.resolve("versions");
        if (!Files.isDirectory(versions)) return null;

        String loaderNamed = null;
        String anyInherits = null;
        try (Stream<Path> dirs = Files.list(versions)) {
            for (Path dir : (Iterable<Path>) dirs::iterator) {
                if (!Files.isDirectory(dir)) continue;
                String name = dir.getFileName().toString();
                Path json = dir.resolve(name + ".json");
                if (!Files.exists(json)) continue;
                JsonObject o = Json.read(json, JsonObject.class, null);
                if (o == null) continue;

                String inh = optStr(o, "inheritsFrom");
                if (mcVersion.equals(inh)) return name;                 // best match
                String lower = name.toLowerCase();
                if (lower.contains("neoforge") || lower.contains("forge")) loaderNamed = name;
                if (inh != null && !inh.equals(name)) anyInherits = name;
            }
        } catch (IOException ignored) {}

        return loaderNamed != null ? loaderNamed : anyInherits;
    }

    private void mergeProfile(Path gameDir, String versionId, ResolvedVersion rv) {
        Path json = gameDir.resolve("versions").resolve(versionId).resolve(versionId + ".json");
        JsonObject o = Json.read(json, JsonObject.class, null);
        if (o == null) return;

        if (o.has("mainClass")) rv.mainClass = o.get("mainClass").getAsString();

        // Modern Forge/NeoForge profiles declare their own minimum Java (e.g. 1.17+ → 17,
        // 1.20.5+ → 21). Honor it so we launch (and provision) the right JDK.
        if (o.has("javaVersion") && o.getAsJsonObject("javaVersion").has("majorVersion")) {
            int loaderMajor = o.getAsJsonObject("javaVersion").get("majorVersion").getAsInt();
            if (loaderMajor > rv.javaMajor) rv.javaMajor = loaderMajor;
        }

        // Forge/NeoForge libraries (and the installer's locally-patched jars) live
        // under the instance's own libraries dir, where the installer placed them.
        Path base = gameDir.resolve("libraries");

        JsonArray libs = o.has("libraries") ? o.getAsJsonArray("libraries") : null;
        if (libs != null) {
            for (JsonElement el : libs) {
                JsonObject lib = el.getAsJsonObject();
                JsonObject downloads = lib.has("downloads") ? lib.getAsJsonObject("downloads") : null;
                if (downloads != null && downloads.has("artifact")) {
                    JsonObject art = downloads.getAsJsonObject("artifact");
                    Path dest = base.resolve(optStr(art, "path"));
                    rv.classpath.add(dest);
                    String dlUrl = optStr(art, "url");
                    // Skip locally-generated jars (no url) — already on disk from the installer.
                    if (dlUrl != null && !dlUrl.isBlank() && !Files.exists(dest)) {
                        rv.downloads.add(new DownloadTask(dlUrl, dest, optStr(art, "sha1"), 0, optStr(art, "path")));
                    }
                } else if (lib.has("name")) {
                    // name + (optional) url maven coordinate.
                    String relPath = FabricMeta.mavenPath(lib.get("name").getAsString());
                    Path dest = base.resolve(relPath);
                    rv.classpath.add(dest);
                    if (!Files.exists(dest)) {
                        String repo = lib.has("url") ? lib.get("url").getAsString() : FALLBACK_REPOS.get(0);
                        if (!repo.endsWith("/")) repo += "/";
                        rv.downloads.add(new DownloadTask(repo + relPath, dest, null, 0, lib.get("name").getAsString()));
                    }
                }
            }
        }

        if (o.has("minecraftArguments")) {
            // Legacy Forge (≤ 1.12.2): the child's minecraftArguments REPLACES the parent's
            // — it already contains the full vanilla args PLUS Forge's --tweakClass, which is
            // what actually bootstraps Forge under launchwrapper. Without this, old Forge
            // launches as plain vanilla (or crashes); appending would duplicate vanilla args.
            rv.gameArgs.clear();
            for (String tok : o.get("minecraftArguments").getAsString().split(" ")) {
                if (!tok.isBlank()) rv.gameArgs.add(tok);
            }
        } else if (o.has("arguments")) {
            // Modern Forge/NeoForge (1.13+): arguments ADD to vanilla's (module path,
            // --launchTarget, fml.* markers). Rule-gated entries are honored.
            JsonObject args = o.getAsJsonObject("arguments");
            MojangMeta.collectArgs(args.has("jvm") ? args.getAsJsonArray("jvm") : null, rv.jvmArgs);
            MojangMeta.collectArgs(args.has("game") ? args.getAsJsonArray("game") : null, rv.gameArgs);
        }
    }

    private static String optStr(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }
}
