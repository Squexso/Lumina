package com.luminamc.skin;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.luminamc.auth.Account;
import com.luminamc.download.Http;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.net.URI;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;

/**
 * Skin utilities: resolve a player name → UUID, read their current skin texture,
 * and apply a skin to the signed-in account via the Minecraft services API
 * (the same flow Essential / the official launcher use to change your skin).
 */
public final class SkinService {

    private SkinService() {}

    public record Profile(String uuid, String name) {}
    public record Skin(String url, boolean slim) {}

    /** Resolves a player name to a Mojang profile, or null if it doesn't exist. */
    public static Profile resolve(String name) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(
                        URI.create("https://api.mojang.com/users/profiles/minecraft/" + enc(name)))
                .header("User-Agent", "LuminaMC-Launcher/0.1.0").GET().build();
        HttpResponse<String> resp = Http.CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 200 && resp.body() != null && !resp.body().isBlank()) {
            JsonObject o = JsonParser.parseString(resp.body()).getAsJsonObject();
            return new Profile(o.get("id").getAsString(), o.get("name").getAsString());
        }
        return null; // 404 / 204 → not found
    }

    /** Reads the current skin (texture URL + slim flag) for a UUID. */
    public static Skin fetchSkin(String uuid) throws IOException, InterruptedException {
        JsonObject profile = Http.getJson(
                "https://sessionserver.mojang.com/session/minecraft/profile/" + uuid.replace("-", ""));
        var props = profile.getAsJsonArray("properties");
        for (var el : props) {
            JsonObject p = el.getAsJsonObject();
            if (!"textures".equals(p.get("name").getAsString())) continue;
            String decoded = new String(Base64.getDecoder().decode(p.get("value").getAsString()), StandardCharsets.UTF_8);
            JsonObject tex = JsonParser.parseString(decoded).getAsJsonObject().getAsJsonObject("textures");
            if (!tex.has("SKIN")) return null;
            JsonObject skin = tex.getAsJsonObject("SKIN");
            String url = skin.get("url").getAsString();
            boolean slim = skin.has("metadata")
                    && "slim".equals(skin.getAsJsonObject("metadata").get("model").getAsString());
            return new Skin(url, slim);
        }
        return null;
    }

    /** A rendered body image URL for previews (public render service, no key). */
    public static String bodyRenderUrl(String uuid) {
        return "https://mc-heads.net/body/" + uuid.replace("-", "") + "/128";
    }

    public static byte[] download(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", "LuminaMC-Launcher/0.1.0").GET().build();
        return Http.CLIENT.send(req, HttpResponse.BodyHandlers.ofByteArray()).body();
    }

    /**
     * Applies a PNG skin to the signed-in account. Requires a real Microsoft
     * account token. {@code variant} = "classic" or "slim".
     */
    public static void apply(Account account, byte[] png, String variant) throws IOException, InterruptedException {
        if (account == null || account.mcAccessToken == null || "offline".equals(account.type) || "0".equals(account.mcAccessToken)) {
            throw new IOException("Applying a skin needs a Microsoft account — sign in first.");
        }
        String boundary = "----LuminaSkin" + System.currentTimeMillis();
        ByteArrayOutputStream body = new ByteArrayOutputStream();
        writeAscii(body, "--" + boundary + "\r\n");
        writeAscii(body, "Content-Disposition: form-data; name=\"variant\"\r\n\r\n" + variant + "\r\n");
        writeAscii(body, "--" + boundary + "\r\n");
        writeAscii(body, "Content-Disposition: form-data; name=\"file\"; filename=\"skin.png\"\r\n");
        writeAscii(body, "Content-Type: image/png\r\n\r\n");
        body.write(png);
        writeAscii(body, "\r\n--" + boundary + "--\r\n");

        HttpRequest req = HttpRequest.newBuilder(URI.create("https://api.minecraftservices.com/minecraft/profile/skins"))
                .header("Authorization", "Bearer " + account.mcAccessToken)
                .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                .header("User-Agent", "LuminaMC-Launcher/0.1.0")
                .POST(HttpRequest.BodyPublishers.ofByteArray(body.toByteArray()))
                .build();
        HttpResponse<String> resp = Http.CLIENT.send(req, HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("Skin change failed (HTTP " + resp.statusCode() + "): " + resp.body());
        }
    }

    private static void writeAscii(ByteArrayOutputStream out, String s) throws IOException {
        out.write(s.getBytes(StandardCharsets.UTF_8));
    }

    private static String enc(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
