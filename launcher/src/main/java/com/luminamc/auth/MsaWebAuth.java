package com.luminamc.auth;

import com.google.gson.JsonObject;
import com.luminamc.download.Http;

import java.io.IOException;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Map;

/**
 * Microsoft account authentication using the legacy Windows Live / Xbox client.
 *
 * <p>Uses the publicly known Xbox app client id ({@code 00000000402b5328}) with the
 * {@code oauth20_desktop.srf} redirect URI — <strong>no Azure AD registration
 * required</strong>. The login page is loaded inside the launcher's embedded WebView
 * ({@link com.luminamc.ui.MicrosoftLoginBrowser}); no external browser is opened.
 *
 * <p>Auth chain: Windows Live implicit token → Xbox Live → XSTS →
 * Minecraft services token → Minecraft profile.
 */
public final class MsaWebAuth {

    private MsaWebAuth() {}

    // ── Well-known Xbox/Windows Live client id ────────────────────────────
    // Used by the official Xbox app and many open-source Minecraft launchers.
    // This is a PUBLIC application identifier — not a secret. Safe to embed.
    public static final String CLIENT_ID   = "00000000402b5328";
    public static final String REDIRECT    = "https://login.live.com/oauth20_desktop.srf";
    public static final String SCOPE       = "service::user.auth.xboxlive.com::MBI_SSL";

    /** The URL to load in the embedded WebView. */
    public static final String AUTH_URL =
            "https://login.live.com/oauth20_authorize.srf"
            + "?client_id="    + CLIENT_ID
            + "&scope="        + urlEnc(SCOPE)
            + "&display=touch" // simplified UI suited for embedded browsers
            + "&response_type=token"
            + "&redirect_uri=" + urlEnc(REDIRECT)
            + "&locale=en";

    // Xbox Live / Minecraft endpoints (identical to PKCE flow).
    private static final String XBL_URL    = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL   = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN   = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE = "https://api.minecraftservices.com/minecraft/profile";

    // ── Token extraction ─────────────────────────────────────────────────

    /**
     * Parses the {@code access_token} from the URL fragment returned by the
     * implicit flow redirect, e.g.:
     * {@code https://login.live.com/oauth20_desktop.srf#access_token=XXX&expires_in=86400&…}
     *
     * @param fragment the hash/fragment portion of the redirect URL, with or without leading {@code #}
     * @return the raw access token, or {@code null} if not found
     */
    public static String extractToken(String fragment) {
        if (fragment == null || fragment.isBlank()) return null;
        if (fragment.startsWith("#")) fragment = fragment.substring(1);
        for (String part : fragment.split("&")) {
            String[] kv = part.split("=", 2);
            if (kv.length == 2 && "access_token".equals(kv[0])) {
                return URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
            }
        }
        return null;
    }

    // ── Auth chain ───────────────────────────────────────────────────────

    /**
     * Completes the full chain from a Windows Live access token to a ready
     * {@link Account}.  Blocks — call from a background thread.
     *
     * <p>Key difference from the PKCE flow: the RpsTicket prefix is {@code t=}
     * (legacy Windows Live token) rather than {@code d=} (modern MSA OAuth token).
     */
    public static Account completeAuth(String wlToken) throws IOException, InterruptedException {
        // 1. Xbox Live (note: "t=" for Windows Live tokens, not "d=")
        JsonObject xbl = Http.postJson(XBL_URL,
                """
                {"Properties":{"AuthMethod":"RPS","SiteName":"user.auth.xboxlive.com",\
                "RpsTicket":"t=%s"},"RelyingParty":"http://auth.xboxlive.com","TokenType":"JWT"}
                """.formatted(wlToken), Map.of());
        String xblToken = xbl.get("Token").getAsString();
        String uhs = xbl.getAsJsonObject("DisplayClaims")
                .getAsJsonArray("xui").get(0).getAsJsonObject()
                .get("uhs").getAsString();

        // 2. XSTS
        JsonObject xsts = Http.postJson(XSTS_URL,
                """
                {"Properties":{"SandboxId":"RETAIL","UserTokens":["%s"]},\
                "RelyingParty":"rp://api.minecraftservices.com/","TokenType":"JWT"}
                """.formatted(xblToken), Map.of());
        if (xsts.has("XErr")) {
            throw new IOException(xstsError(xsts.get("XErr").getAsLong()));
        }
        String xstsToken = xsts.get("Token").getAsString();

        // 3. Minecraft login
        JsonObject mc = Http.postJson(MC_LOGIN,
                "{\"identityToken\":\"XBL3.0 x=" + uhs + ";" + xstsToken + "\"}", Map.of());
        String mcToken = mc.get("access_token").getAsString();

        // 4. Minecraft profile
        var req = java.net.http.HttpRequest.newBuilder(
                        java.net.URI.create(MC_PROFILE))
                .header("Authorization", "Bearer " + mcToken)
                .GET().build();
        var resp = Http.CLIENT.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 404) {
            throw new IOException("This Microsoft account does not own Minecraft Java Edition.");
        }
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("Minecraft profile error: HTTP " + resp.statusCode());
        }
        JsonObject profile = com.google.gson.JsonParser.parseString(resp.body()).getAsJsonObject();

        Account account = new Account();
        account.type            = "msa";
        account.mcAccessToken   = mcToken;
        account.msRefreshToken  = null;   // implicit flow has no refresh token
        account.username        = profile.get("name").getAsString();
        account.mcUuid          = profile.get("id").getAsString();
        account.expiresAtEpochMs = System.currentTimeMillis() + 86_400_000L; // 24 h
        return account;
    }

    private static String xstsError(long code) {
        return switch ((int) code) {
            case 0x8015DC16 -> "Your Microsoft account has no Xbox profile. Visit xbox.com to create one.";
            case 0x8015DC19 -> "Xbox Live is not available in your region.";
            case 0x8015DC1A -> "Child accounts require parental approval for Xbox Live.";
            default -> "Xbox authentication failed (XErr=" + Long.toHexString(code) + ").";
        };
    }

    private static String urlEnc(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
