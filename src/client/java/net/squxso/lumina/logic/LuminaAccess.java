package net.squxso.lumina.logic;

import com.google.gson.Gson;
import com.google.gson.JsonObject;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;

import java.io.*;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.*;
import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

/**
 * Access control: only players on the allow-list may use Lumina.
 *
 * HOW TO SET IT UP:
 * ------------------------------------------------------------------
 * Option A — local file (no internet required):
 *   Edit .minecraft/config/lumina/access.json  (created automatically).
 *   Add player names or UUIDs to the "allowed" array.
 *   Example:
 *       {
 *         "allowed": ["YourName", "FriendName", "friend-uuid-here"],
 *         "remoteUrl": ""
 *       }
 *
 * Option B — remote URL (you control the list from a server):
 *   Host a plain JSON file with the same "allowed" array anywhere
 *   (GitHub Gist, your own VPS, Pastebin raw, etc.) and put its URL
 *   in "remoteUrl". Lumina fetches it on startup and caches it locally
 *   in case the server is unreachable next time.
 *   Example:
 *       {
 *         "allowed": [],
 *         "remoteUrl": "https://raw.githubusercontent.com/you/repo/main/lumina-access.json"
 *       }
 *
 * If the file doesn't exist yet Lumina creates it with "allowed": []
 * and blocks everyone until you add names. The mod owner should add
 * their own name/UUID first.
 * ------------------------------------------------------------------
 */
public final class LuminaAccess {

    private static final Path CONFIG_DIR  =
            FabricLoader.getInstance().getConfigDir().resolve("lumina");
    private static final Path CONFIG_FILE = CONFIG_DIR.resolve("access.json");

    private static final Gson GSON = new Gson();

    private static final Set<String> allowed = new HashSet<>();
    private static boolean loaded = false;

    public static boolean isAllowed(MinecraftClient mc) {
        if (!loaded) load(mc);
        if (allowed.isEmpty()) return true; // no list configured → open to all

        String name = mc.getSession() != null ? mc.getSession().getUsername().toLowerCase() : "";
        String uuid = mc.getSession() != null
                && mc.getSession().getUuidOrNull() != null
                ? mc.getSession().getUuidOrNull().toString() : "";

        return allowed.contains(name) || allowed.contains(uuid);
    }

    // ── Load ──────────────────────────────────────────────────────────────

    private static void load(MinecraftClient mc) {
        loaded = true;
        ensureDefault();

        try {
            JsonObject root = GSON.fromJson(Files.readString(CONFIG_FILE), JsonObject.class);
            parseAllowed(root);

            String remoteUrl = root.has("remoteUrl") ? root.get("remoteUrl").getAsString().trim() : "";
            if (!remoteUrl.isEmpty()) fetchRemote(mc, remoteUrl);
        } catch (Exception e) {
            LuminaLogic.sendChat(mc, "§cAccess config error: " + e.getMessage());
        }
    }

    private static void parseAllowed(JsonObject root) {
        allowed.clear();
        if (root.has("allowed") && root.get("allowed").isJsonArray()) {
            root.getAsJsonArray("allowed").forEach(el -> {
                String v = el.getAsString().trim().toLowerCase();
                if (!v.isEmpty()) allowed.add(v);
            });
        }
    }

    private static void fetchRemote(MinecraftClient mc, String url) {
        Thread.ofVirtual().start(() -> {
            try {
                HttpClient http = HttpClient.newBuilder()
                        .connectTimeout(Duration.ofSeconds(5))
                        .build();
                HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(8))
                        .GET().build();
                HttpResponse<String> resp = http.send(req, HttpResponse.BodyHandlers.ofString());
                if (resp.statusCode() == 200) {
                    JsonObject remote = GSON.fromJson(resp.body(), JsonObject.class);
                    parseAllowed(remote);
                    // Merge remote list into local file so it works offline next launch.
                    mergeIntoLocal(remote);
                }
            } catch (Exception e) {
                mc.execute(() -> LuminaLogic.sendChat(mc, "§eAccess: remote fetch failed, using cached list"));
            }
        });
    }

    private static void mergeIntoLocal(JsonObject remote) {
        try {
            JsonObject local = GSON.fromJson(Files.readString(CONFIG_FILE), JsonObject.class);
            local.add("allowed", remote.getAsJsonArray("allowed"));
            Files.writeString(CONFIG_FILE, GSON.toJson(local));
        } catch (Exception ignored) {}
    }

    // ── Default config ────────────────────────────────────────────────────

    private static void ensureDefault() {
        try {
            Files.createDirectories(CONFIG_DIR);
            if (!Files.exists(CONFIG_FILE)) {
                JsonObject def = new JsonObject();
                def.add("allowed", GSON.toJsonTree(new String[0]));
                def.addProperty("remoteUrl", "");
                Files.writeString(CONFIG_FILE, GSON.toJson(def));
            }
        } catch (Exception ignored) {}
    }

    public static void reload() { loaded = false; allowed.clear(); }
}
