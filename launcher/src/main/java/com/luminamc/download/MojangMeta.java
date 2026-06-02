package com.luminamc.download;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.luminamc.config.LuminaPaths;
import com.luminamc.game.ResolvedVersion;

import java.io.IOException;
import java.nio.file.Path;
import java.util.Locale;

/**
 * Parses a Mojang version JSON into a {@link ResolvedVersion}: libraries (with
 * OS rule evaluation and native classifiers), the client jar, the asset index
 * and all asset objects, the JVM/game argument templates and the main class.
 * Handles both the modern {@code arguments} block and the legacy
 * {@code minecraftArguments} string.
 */
public final class MojangMeta {

    private static final String RESOURCES = "https://resources.download.minecraft.net/";

    public enum OS { WINDOWS, OSX, LINUX }

    public static final OS CURRENT_OS = detectOs();

    public ResolvedVersion resolve(String versionJsonUrl) throws IOException, InterruptedException {
        JsonObject json = Http.getJson(versionJsonUrl);
        return resolve(json);
    }

    public ResolvedVersion resolve(JsonObject json) throws IOException, InterruptedException {
        ResolvedVersion rv = new ResolvedVersion();
        rv.id = str(json, "id");
        rv.mainClass = str(json, "mainClass");

        if (json.has("javaVersion")) {
            rv.javaMajor = json.getAsJsonObject("javaVersion").get("majorVersion").getAsInt();
        }

        parseClient(json, rv);
        parseLibraries(json, rv);
        parseAssets(json, rv);
        parseArguments(json, rv);
        return rv;
    }

    // ── client jar ──────────────────────────────────────────────────────

    private void parseClient(JsonObject json, ResolvedVersion rv) {
        JsonObject client = json.getAsJsonObject("downloads").getAsJsonObject("client");
        Path jar = LuminaPaths.versions().resolve(rv.id).resolve(rv.id + ".jar");
        rv.clientJar = jar;
        rv.classpath.add(jar);
        rv.downloads.add(new DownloadTask(
                str(client, "url"), jar, str(client, "sha1"), longOr(client, "size", 0), rv.id + ".jar"));
    }

    // ── libraries ───────────────────────────────────────────────────────

    private void parseLibraries(JsonObject json, ResolvedVersion rv) {
        JsonArray libs = json.getAsJsonArray("libraries");
        if (libs == null) return;
        for (JsonElement el : libs) {
            JsonObject lib = el.getAsJsonObject();
            if (lib.has("rules") && !rulesAllow(lib.getAsJsonArray("rules"))) continue;

            JsonObject downloads = lib.has("downloads") ? lib.getAsJsonObject("downloads") : null;
            if (downloads == null) continue;

            // Main artifact (current versions ship per-OS natives this way too).
            if (downloads.has("artifact")) {
                JsonObject art = downloads.getAsJsonObject("artifact");
                Path dest = LuminaPaths.libraries().resolve(str(art, "path"));
                rv.classpath.add(dest);
                rv.downloads.add(new DownloadTask(str(art, "url"), dest, str(art, "sha1"),
                        longOr(art, "size", 0), str(art, "path")));
                String name = str(lib, "name");
                if (name != null && name.toLowerCase(Locale.ROOT).contains("natives")) {
                    rv.nativeJars.add(dest);
                }
            }

            // Legacy native classifiers (e.g. lwjgl natives-windows).
            if (lib.has("natives") && downloads.has("classifiers")) {
                JsonObject natives = lib.getAsJsonObject("natives");
                String key = osKey(natives);
                if (key != null) {
                    String classifier = natives.get(key).getAsString().replace("${arch}", "64");
                    JsonObject classifiers = downloads.getAsJsonObject("classifiers");
                    if (classifiers.has(classifier)) {
                        JsonObject nat = classifiers.getAsJsonObject(classifier);
                        Path dest = LuminaPaths.libraries().resolve(str(nat, "path"));
                        rv.downloads.add(new DownloadTask(str(nat, "url"), dest, str(nat, "sha1"),
                                longOr(nat, "size", 0), str(nat, "path")));
                        rv.nativeJars.add(dest);
                    }
                }
            }
        }
    }

    // ── assets ──────────────────────────────────────────────────────────

    private void parseAssets(JsonObject json, ResolvedVersion rv) throws IOException, InterruptedException {
        if (!json.has("assetIndex")) return;
        JsonObject idx = json.getAsJsonObject("assetIndex");
        rv.assetIndexId = str(idx, "id");
        rv.assetsRoot = LuminaPaths.assets();

        Path indexFile = LuminaPaths.assets().resolve("indexes").resolve(rv.assetIndexId + ".json");
        rv.downloads.add(new DownloadTask(str(idx, "url"), indexFile, str(idx, "sha1"),
                longOr(idx, "size", 0), rv.assetIndexId + ".json"));

        // Fetch the index now so we can enumerate every object to download.
        JsonObject index = Http.getJson(str(idx, "url"));
        rv.legacyAssets = index.has("virtual") && index.get("virtual").getAsBoolean();
        JsonObject objects = index.getAsJsonObject("objects");
        for (var entry : objects.entrySet()) {
            JsonObject obj = entry.getValue().getAsJsonObject();
            String hash = str(obj, "hash");
            long size = longOr(obj, "size", 0);
            String sub = hash.substring(0, 2);
            Path dest = LuminaPaths.assets().resolve("objects").resolve(sub).resolve(hash);
            rv.downloads.add(new DownloadTask(RESOURCES + sub + "/" + hash, dest, hash, size, entry.getKey()));
        }
    }

    // ── arguments ───────────────────────────────────────────────────────

    private void parseArguments(JsonObject json, ResolvedVersion rv) {
        if (json.has("arguments")) {
            JsonObject args = json.getAsJsonObject("arguments");
            collectArgs(args.getAsJsonArray("jvm"), rv.jvmArgs);
            collectArgs(args.getAsJsonArray("game"), rv.gameArgs);
        } else if (json.has("minecraftArguments")) {
            // Legacy: a single space-separated template, default JVM args supplied by us.
            rv.jvmArgs.add("-Djava.library.path=${natives_directory}");
            rv.jvmArgs.add("-cp");
            rv.jvmArgs.add("${classpath}");
            for (String tok : str(json, "minecraftArguments").split(" ")) {
                if (!tok.isBlank()) rv.gameArgs.add(tok);
            }
        }
    }

    private void collectArgs(JsonArray arr, java.util.List<String> out) {
        if (arr == null) return;
        for (JsonElement el : arr) {
            if (el.isJsonPrimitive()) {
                out.add(el.getAsString());
            } else if (el.isJsonObject()) {
                JsonObject o = el.getAsJsonObject();
                if (o.has("rules") && !rulesAllow(o.getAsJsonArray("rules"))) continue;
                JsonElement val = o.get("value");
                if (val.isJsonPrimitive()) {
                    out.add(val.getAsString());
                } else if (val.isJsonArray()) {
                    for (JsonElement v : val.getAsJsonArray()) out.add(v.getAsString());
                }
            }
        }
    }

    // ── rule evaluation ─────────────────────────────────────────────────

    /** Evaluates Mojang allow/disallow rules for the current OS (features ignored). */
    public static boolean rulesAllow(JsonArray rules) {
        boolean allowed = false;
        for (JsonElement el : rules) {
            JsonObject rule = el.getAsJsonObject();
            boolean matches = true;
            if (rule.has("os")) {
                JsonObject os = rule.getAsJsonObject("os");
                if (os.has("name")) {
                    matches = osName().equals(os.get("name").getAsString());
                }
            }
            if (rule.has("features")) {
                matches = false; // we don't enable demo/custom-resolution feature args
            }
            if (matches) {
                allowed = "allow".equals(rule.get("action").getAsString());
            }
        }
        return allowed;
    }

    private static String osKey(JsonObject natives) {
        String name = osName();
        if (natives.has(name)) return name;
        return null;
    }

    private static OS detectOs() {
        String os = System.getProperty("os.name").toLowerCase(Locale.ROOT);
        if (os.contains("win")) return OS.WINDOWS;
        if (os.contains("mac") || os.contains("darwin")) return OS.OSX;
        return OS.LINUX;
    }

    public static String osName() {
        return switch (CURRENT_OS) {
            case WINDOWS -> "windows";
            case OSX     -> "osx";
            case LINUX   -> "linux";
        };
    }

    // ── small json helpers ──────────────────────────────────────────────

    private static String str(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }

    private static long longOr(JsonObject o, String key, long def) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsLong() : def;
    }
}
