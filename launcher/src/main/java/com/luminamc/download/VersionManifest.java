package com.luminamc.download;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

/**
 * The Mojang {@code version_manifest_v2.json} — the catalog of every available
 * Minecraft version. Used to populate the version picker when creating
 * instances. Supports both the "1.x.x" and "26.x.x" naming schemes.
 */
public final class VersionManifest {

    public static final String URL =
            "https://launchermeta.mojang.com/mc/game/version_manifest_v2.json";

    public static final class Entry {
        public String id;          // e.g. "1.21.10" or "26.1.0"
        public String type;        // release | snapshot | old_beta | old_alpha
        public String url;         // version JSON url
        public String releaseTime;

        public boolean isRelease()  { return "release".equals(type); }
        public boolean isSnapshot() { return "snapshot".equals(type); }

        @Override public String toString() { return id; }
    }

    public String latestRelease;
    public String latestSnapshot;
    public final List<Entry> versions = new ArrayList<>();

    /** Fetches and parses the live manifest from Mojang. */
    public static VersionManifest fetch() throws IOException, InterruptedException {
        JsonObject root = Http.getJson(URL);
        VersionManifest m = new VersionManifest();
        JsonObject latest = root.getAsJsonObject("latest");
        if (latest != null) {
            m.latestRelease  = optString(latest, "release");
            m.latestSnapshot = optString(latest, "snapshot");
        }
        JsonArray arr = root.getAsJsonArray("versions");
        for (var el : arr) {
            JsonObject v = el.getAsJsonObject();
            Entry e = new Entry();
            e.id = optString(v, "id");
            e.type = optString(v, "type");
            e.url = optString(v, "url");
            e.releaseTime = optString(v, "releaseTime");
            m.versions.add(e);
        }
        return m;
    }

    public Entry find(String id) {
        return versions.stream().filter(v -> v.id.equals(id)).findFirst().orElse(null);
    }

    private static String optString(JsonObject o, String key) {
        return o.has(key) && !o.get(key).isJsonNull() ? o.get(key).getAsString() : null;
    }
}
