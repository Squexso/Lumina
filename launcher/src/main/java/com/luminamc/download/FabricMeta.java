package com.luminamc.download;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.luminamc.config.LuminaPaths;
import com.luminamc.game.ResolvedVersion;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Talks to {@code meta.fabricmc.net}. Lists loader builds for a game version
 * and merges the Fabric launch profile (extra libraries + main class) into an
 * already-resolved vanilla {@link ResolvedVersion}.
 */
public final class FabricMeta {

    private static final String BASE = "https://meta.fabricmc.net/v2/versions/loader/";

    public static final class Loader {
        public String version;
        public boolean stable;
        @Override public String toString() { return version + (stable ? "" : " (beta)"); }
    }

    /** Returns the available Fabric loader builds for a given game version. */
    public List<Loader> listLoaders(String gameVersion) throws IOException, InterruptedException {
        JsonArray arr = Http.getJsonArray(BASE + enc(gameVersion));
        List<Loader> out = new ArrayList<>();
        for (JsonElement el : arr) {
            JsonObject loader = el.getAsJsonObject().getAsJsonObject("loader");
            Loader l = new Loader();
            l.version = loader.get("version").getAsString();
            l.stable = loader.has("stable") && loader.get("stable").getAsBoolean();
            out.add(l);
        }
        return out;
    }

    public String latestStableLoader(String gameVersion) throws IOException, InterruptedException {
        for (Loader l : listLoaders(gameVersion)) if (l.stable) return l.version;
        List<Loader> all = listLoaders(gameVersion);
        return all.isEmpty() ? null : all.get(0).version;
    }

    /**
     * Merges the Fabric profile into {@code rv}: overrides the main class with
     * the Fabric knot launcher and appends Fabric's libraries (loader,
     * intermediary, and dependencies) to the classpath and download list.
     */
    public void applyTo(ResolvedVersion rv, String gameVersion, String loaderVersion)
            throws IOException, InterruptedException {
        String url = BASE + enc(gameVersion) + "/" + enc(loaderVersion) + "/profile/json";
        JsonObject profile = Http.getJson(url);

        if (profile.has("mainClass") && profile.get("mainClass").isJsonPrimitive()) {
            rv.mainClass = profile.get("mainClass").getAsString();
        }

        JsonArray libs = profile.getAsJsonArray("libraries");
        if (libs != null) {
            for (JsonElement el : libs) {
                JsonObject lib = el.getAsJsonObject();
                String name = lib.get("name").getAsString();
                String repo = lib.has("url") ? lib.get("url").getAsString() : "https://maven.fabricmc.net/";
                if (!repo.endsWith("/")) repo += "/";
                String relPath = mavenPath(name);
                Path dest = LuminaPaths.libraries().resolve(relPath);
                String sha1 = lib.has("sha1") ? lib.get("sha1").getAsString() : null;
                rv.classpath.add(dest);
                rv.downloads.add(new DownloadTask(repo + relPath, dest, sha1, 0, name));
            }
        }

        // Fabric profiles may add extra JVM/game args (rule-gated objects included).
        if (profile.has("arguments")) {
            JsonObject args = profile.getAsJsonObject("arguments");
            MojangMeta.collectArgs(args.has("jvm") ? args.getAsJsonArray("jvm") : null, rv.jvmArgs);
            MojangMeta.collectArgs(args.has("game") ? args.getAsJsonArray("game") : null, rv.gameArgs);
        }
    }

    /** Converts a Maven coordinate {@code group:artifact:version[:classifier]} to a repo path. */
    public static String mavenPath(String coord) {
        String[] parts = coord.split(":");
        if (parts.length < 3) {
            throw new IllegalArgumentException("Malformed Maven coordinate (need group:artifact:version): " + coord);
        }
        String group = parts[0].replace('.', '/');
        String artifact = parts[1];
        String version = parts[2];
        String classifier = parts.length > 3 ? "-" + parts[3] : "";
        return group + "/" + artifact + "/" + version + "/" + artifact + "-" + version + classifier + ".jar";
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, java.nio.charset.StandardCharsets.UTF_8);
    }
}
