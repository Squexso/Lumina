package com.luminamc.download;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.luminamc.instance.ModLoader;

import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Thin client for the <a href="https://docs.modrinth.com/">Modrinth v2 API</a>:
 * mod search and project-version lookup, both filtered to a mod loader and a
 * Minecraft version. Modrinth indexes Fabric, Forge, NeoForge and Quilt mods,
 * so the same endpoint serves every loader Lumina supports.
 *
 * <p>Pure data access — no disk writes. {@link com.luminamc.game.ModrinthInstaller}
 * turns the {@link Version} objects this returns into installed jars (resolving
 * required dependencies along the way).
 */
public final class ModrinthApi {

    private static final String BASE = "https://api.modrinth.com/v2";

    /** A search hit — one mod project. */
    public record Project(String id, String slug, String title, String description,
                          String author, String iconUrl, long downloads) {}

    /** A downloadable file attached to a version. */
    public record FileInfo(String url, String filename, String sha1, long size) {}

    /** A version's relation to another project ({@code type} = required/optional/…). */
    public record Dependency(String projectId, String versionId, String type) {}

    /** One published build of a project. */
    public record Version(String id, String projectId, String versionNumber, String type,
                          FileInfo file, List<Dependency> dependencies) {}

    /** One page of search hits plus the total number of matches available. */
    public record SearchResult(List<Project> hits, int totalHits) {}

    /** Modrinth's id for a loader, or {@code null} for vanilla (no mods). */
    public static String loaderId(ModLoader loader) {
        return switch (loader) {
            case FABRIC   -> "fabric";
            case FORGE    -> "forge";
            case NEOFORGE -> "neoforge";
            default       -> null;
        };
    }

    /**
     * Searches a project type ("mod", "resourcepack", "shader", …), restricted to
     * {@code mcVersion} (e.g. "1.21.10") and — when non-null — to a {@code loader}.
     * Packs and shaders aren't loader-specific, so pass {@code loader = null} there.
     *
     * <p>Paginated: {@code offset} skips earlier hits so callers can page through
     * the full result set ({@link SearchResult#totalHits()} reports how many exist).
     */
    public SearchResult search(String projectType, String query, String loader, String mcVersion,
                               int limit, int offset) throws Exception {
        boolean blank = query == null || query.isBlank();
        StringBuilder facets = new StringBuilder("[[\"project_type:").append(projectType).append("\"]");
        if (loader != null)                       facets.append(",[\"categories:").append(loader).append("\"]");
        if (mcVersion != null && !mcVersion.isBlank()) facets.append(",[\"versions:").append(mcVersion).append("\"]");
        facets.append("]");

        String url = BASE + "/search?limit=" + limit + "&offset=" + Math.max(0, offset)
                + "&index=" + (blank ? "downloads" : "relevance")
                + "&query=" + enc(blank ? "" : query.trim())
                + "&facets=" + enc(facets.toString());

        JsonObject root = Http.getJson(url);
        List<Project> out = new ArrayList<>();
        JsonArray hits = root.getAsJsonArray("hits");
        if (hits != null) {
            for (JsonElement el : hits) {
                JsonObject h = el.getAsJsonObject();
                out.add(new Project(
                        str(h, "project_id"),
                        str(h, "slug"),
                        str(h, "title"),
                        str(h, "description"),
                        str(h, "author"),
                        str(h, "icon_url"),
                        longOr(h, "downloads", 0)));
            }
        }
        return new SearchResult(out, (int) longOr(root, "total_hits", out.size()));
    }

    /**
     * Lists a project's versions, newest first. Both filters are optional — pass
     * {@code loader = null} for packs/shaders, or {@code mcVersion = null} to list
     * across all versions.
     */
    public List<Version> versions(String projectIdOrSlug, String loader, String mcVersion) throws Exception {
        StringBuilder url = new StringBuilder(BASE + "/project/" + projectIdOrSlug + "/version");
        char sep = '?';
        if (loader != null) {
            url.append(sep).append("loaders=").append(enc("[\"" + loader + "\"]"));
            sep = '&';
        }
        if (mcVersion != null && !mcVersion.isBlank()) {
            url.append(sep).append("game_versions=").append(enc("[\"" + mcVersion + "\"]"));
        }
        JsonArray arr = Http.getJsonArray(url.toString());
        List<Version> out = new ArrayList<>();
        for (JsonElement el : arr) out.add(parseVersion(el.getAsJsonObject()));
        return out;
    }

    /** The newest stable release for {@code loader}+{@code mcVersion} (falls back to newest of any type). */
    public Version bestVersion(String projectIdOrSlug, String loader, String mcVersion) throws Exception {
        Version first = null;
        for (Version v : versions(projectIdOrSlug, loader, mcVersion)) {
            if (first == null) first = v;
            if ("release".equals(v.type())) return v;
        }
        return first;
    }

    /** Fetches a single version by its id (used to follow pinned dependencies). */
    public Version versionById(String versionId) throws Exception {
        return parseVersion(Http.getJson(BASE + "/version/" + versionId));
    }

    // ── parsing ──────────────────────────────────────────────────────────

    private Version parseVersion(JsonObject v) {
        FileInfo file = parsePrimaryFile(v.getAsJsonArray("files"));
        List<Dependency> deps = new ArrayList<>();
        if (v.has("dependencies") && v.get("dependencies").isJsonArray()) {
            for (JsonElement de : v.getAsJsonArray("dependencies")) {
                JsonObject d = de.getAsJsonObject();
                deps.add(new Dependency(strOrNull(d, "project_id"), strOrNull(d, "version_id"),
                        str(d, "dependency_type")));
            }
        }
        return new Version(str(v, "id"), str(v, "project_id"), str(v, "version_number"),
                str(v, "version_type"), file, deps);
    }

    private FileInfo parsePrimaryFile(JsonArray files) {
        if (files == null || files.isEmpty()) return null;
        JsonObject chosen = files.get(0).getAsJsonObject();
        for (JsonElement el : files) {
            JsonObject f = el.getAsJsonObject();
            if (f.has("primary") && f.get("primary").getAsBoolean()) { chosen = f; break; }
        }
        String sha1 = chosen.has("hashes") && chosen.getAsJsonObject("hashes").has("sha1")
                ? chosen.getAsJsonObject("hashes").get("sha1").getAsString() : null;
        return new FileInfo(str(chosen, "url"), str(chosen, "filename"), sha1, longOr(chosen, "size", 0));
    }

    private static String str(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : "";
    }
    private static String strOrNull(JsonObject o, String k) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsString() : null;
    }
    private static long longOr(JsonObject o, String k, long dflt) {
        return o.has(k) && !o.get(k).isJsonNull() ? o.get(k).getAsLong() : dflt;
    }
    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
