package com.luminamc.auth;

import com.google.gson.JsonObject;
import com.luminamc.download.Http;

import java.io.IOException;
import java.net.URI;
import java.util.Map;

/**
 * Microsoft account authentication for LuminaMC — <b>legacy device-code flow</b>.
 *
 * <p>Uses Microsoft's own pre-registered Minecraft launcher client id
 * ({@value #CLIENT_ID}) against the legacy {@code login.live.com} endpoints.
 * This client id is already approved for {@code api.minecraftservices.com},
 * so it sidesteps the "Invalid app registration" 403 that hits freshly created
 * Azure AD apps (those require a manual Microsoft approval form + up to 24 h
 * propagation). <b>No Azure setup is required.</b>
 *
 * <p>Flow (RFC&nbsp;8628 device authorization, legacy variant):
 * <ol>
 *   <li>POST {@code oauth20_connect.srf} → device_code + user_code.</li>
 *   <li>User opens {@code microsoft.com/link} and types the user_code.</li>
 *   <li>Poll {@code oauth20_token.srf} until a Windows Live token is returned.</li>
 *   <li>Windows Live token → Xbox Live ({@code RpsTicket: "t=…"}) → XSTS →
 *       Minecraft services token → Minecraft profile.</li>
 * </ol>
 *
 * <p>No client secret is ever used or stored — the device-code flow needs none.
 */
public final class MicrosoftAuth {

    // ── Pre-approved Minecraft launcher client id (legacy Live SDK app) ──
    // NOT a secret: it is a public application identifier baked into every
    // copy of the official launcher. Works only with login.live.com endpoints.
    private static final String CLIENT_ID = "00000000402b5328";
    private static final String SCOPE     = "service::user.auth.xboxlive.com::MBI_SSL";

    // Legacy Windows Live endpoints (these accept the client id above).
    private static final String DEVICE_CODE_URL = "https://login.live.com/oauth20_connect.srf";
    private static final String TOKEN_URL       = "https://login.live.com/oauth20_token.srf";

    /**
     * Signs the browser out of the current Microsoft account. Opening this before a
     * new device-code sign-in forces the verification page to ask for credentials
     * again, so a <em>different</em> account can be added (multi-account support).
     */
    public static final String LOGOUT_URL = "https://login.live.com/logout.srf";

    // Xbox Live / Minecraft endpoints.
    private static final String XBL_URL    = "https://user.auth.xboxlive.com/user/authenticate";
    private static final String XSTS_URL   = "https://xsts.auth.xboxlive.com/xsts/authorize";
    private static final String MC_LOGIN   = "https://api.minecraftservices.com/authentication/login_with_xbox";
    private static final String MC_PROFILE = "https://api.minecraftservices.com/minecraft/profile";

    // ── Public API ────────────────────────────────────────────────────────

    /** The built-in client id always works — no configuration needed. */
    public static boolean hasClientId() { return true; }
    public static boolean isConfigured() { return true; }

    /** Receives the device code so the UI can display it and open the browser. */
    @FunctionalInterface
    public interface DeviceCodeCallback {
        /** @param userCode        short code the user types at the verification URL
         *  @param verificationUri the URL to open (e.g. https://www.microsoft.com/link)
         *  @param expiresIn       seconds until the code expires */
        void show(String userCode, String verificationUri, int expiresIn);
    }

    /**
     * Device-code login. Blocks until the user authorises (or the code expires).
     * Call from a background thread.
     */
    public Account loginViaDeviceCode(DeviceCodeCallback callback)
            throws IOException, InterruptedException {

        JsonObject dev = Http.postForm(DEVICE_CODE_URL, Map.of(
                "client_id",     CLIENT_ID,
                "scope",         SCOPE,
                "response_type", "device_code"));

        if (!dev.has("device_code")) {
            throw new IOException("Could not start sign-in: " + describe(dev));
        }
        String deviceCode = dev.get("device_code").getAsString();
        String userCode   = dev.get("user_code").getAsString();
        String verifyUri  = dev.has("verification_uri")
                ? dev.get("verification_uri").getAsString()
                : "https://www.microsoft.com/link";
        int interval  = dev.has("interval")   ? dev.get("interval").getAsInt()   : 5;
        int expiresIn = dev.has("expires_in") ? dev.get("expires_in").getAsInt() : 900;

        callback.show(userCode, verifyUri, expiresIn);

        JsonObject token = pollForToken(deviceCode, interval, expiresIn);
        String accessToken  = token.get("access_token").getAsString();
        String refreshToken = token.has("refresh_token") ? token.get("refresh_token").getAsString() : null;
        int    expSec       = token.has("expires_in")    ? token.get("expires_in").getAsInt()        : 86_400;

        return completeChain(accessToken, refreshToken, expSec);
    }

    private JsonObject pollForToken(String deviceCode, int interval, int expiresIn)
            throws IOException, InterruptedException {
        long deadline = System.currentTimeMillis() + expiresIn * 1000L;
        while (System.currentTimeMillis() < deadline) {
            Thread.sleep(Math.max(1, interval) * 1000L);
            JsonObject resp = Http.postForm(TOKEN_URL, Map.of(
                    "client_id",   CLIENT_ID,
                    "grant_type",  "urn:ietf:params:oauth:grant-type:device_code",
                    "device_code", deviceCode));
            if (resp.has("access_token")) return resp;

            String error = resp.has("error") ? resp.get("error").getAsString() : "";
            switch (error) {
                case "authorization_pending" -> { /* keep waiting */ }
                case "slow_down"             -> interval = Math.min(interval + 5, 30);
                case "authorization_declined", "access_denied" ->
                        throw new IOException("Sign-in was cancelled.");
                case "expired_token", "code_expired" ->
                        throw new IOException("The code expired. Please try again.");
                default -> {
                    if (!error.isBlank())
                        throw new IOException("Sign-in failed: " + describe(resp));
                }
            }
        }
        throw new IOException("Sign-in timed out. Please try again.");
    }

    // ── Silent refresh ────────────────────────────────────────────────────

    public Account refresh(Account account) throws IOException, InterruptedException {
        if (account.msRefreshToken == null) throw new IOException("No refresh token stored.");
        JsonObject token = Http.postForm(TOKEN_URL, Map.of(
                "client_id",     CLIENT_ID,
                "grant_type",    "refresh_token",
                "refresh_token", account.msRefreshToken,
                "scope",         SCOPE));
        if (!token.has("access_token")) throw new IOException("Session refresh failed: " + describe(token));
        Account refreshed = completeChain(
                token.get("access_token").getAsString(),
                token.has("refresh_token") ? token.get("refresh_token").getAsString() : account.msRefreshToken,
                token.has("expires_in")    ? token.get("expires_in").getAsInt()       : 86_400);
        refreshed.id = account.id;
        return refreshed;
    }

    // ── Windows Live token → Xbox Live → XSTS → Minecraft ────────────────

    private Account completeChain(String liveToken, String refreshToken, int expiresIn)
            throws IOException, InterruptedException {
        // 1. Xbox Live — legacy Live token uses the "t=" RpsTicket prefix.
        JsonObject xbl = Http.postJson(XBL_URL,
                """
                {"Properties":{"AuthMethod":"RPS","SiteName":"user.auth.xboxlive.com",\
                "RpsTicket":"t=%s"},"RelyingParty":"http://auth.xboxlive.com","TokenType":"JWT"}
                """.formatted(liveToken), Map.of());
        String xblToken = xbl.get("Token").getAsString();
        String uhs = xbl.getAsJsonObject("DisplayClaims")
                .getAsJsonArray("xui").get(0).getAsJsonObject()
                .get("uhs").getAsString();

        // 2. XSTS — relying party is the Minecraft services API.
        JsonObject xsts = Http.postJson(XSTS_URL,
                """
                {"Properties":{"SandboxId":"RETAIL","UserTokens":["%s"]},\
                "RelyingParty":"rp://api.minecraftservices.com/","TokenType":"JWT"}
                """.formatted(xblToken), Map.of());
        if (xsts.has("XErr")) {
            long xerr = xsts.get("XErr").getAsLong();
            throw new IOException(switch ((int) xerr) {
                case 0x8015DC16, 0x8015DC09 ->
                        "Your Microsoft account has no Xbox profile yet. "
                        + "Sign in once at minecraft.net or xbox.com to create one, then retry.";
                case 0x8015DC19 -> "Xbox Live is not available in your region.";
                case 0x8015DC1A -> "This account is a child account and needs to be added to a Family.";
                default -> "Xbox sign-in failed (XErr=" + Long.toHexString(xerr) + ").";
            });
        }
        String xstsToken = xsts.get("Token").getAsString();

        // 3. Minecraft services token.
        JsonObject mc = Http.postJson(MC_LOGIN,
                "{\"identityToken\":\"XBL3.0 x=" + uhs + ";" + xstsToken + "\"}", Map.of());
        String mcToken = mc.get("access_token").getAsString();

        // 4. Minecraft profile.
        JsonObject profile = getProfile(mcToken);

        Account account = new Account();
        account.type             = "msa";
        account.mcAccessToken    = mcToken;
        account.msRefreshToken   = refreshToken;
        account.username         = profile.get("name").getAsString();
        account.mcUuid           = profile.get("id").getAsString();
        account.expiresAtEpochMs = System.currentTimeMillis() + expiresIn * 1000L;
        return account;
    }

    private JsonObject getProfile(String mcToken) throws IOException, InterruptedException {
        var req = java.net.http.HttpRequest.newBuilder(URI.create(MC_PROFILE))
                .header("Authorization", "Bearer " + mcToken)
                .GET().build();
        var resp = Http.CLIENT.send(req, java.net.http.HttpResponse.BodyHandlers.ofString());
        if (resp.statusCode() == 404)
            throw new IOException("This Microsoft account does not own Minecraft Java Edition.");
        if (resp.statusCode() / 100 != 2)
            throw new IOException("Could not read your Minecraft profile (HTTP " + resp.statusCode() + ").");
        return com.google.gson.JsonParser.parseString(resp.body()).getAsJsonObject();
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static String describe(JsonObject resp) {
        if (resp.has("error_description")) return resp.get("error_description").getAsString();
        if (resp.has("error"))             return resp.get("error").getAsString();
        return resp.toString();
    }

    /** Opens a URL in the system browser (reliable across platforms). */
    public static void openBrowser(String url) {
        new Thread(() -> {
            String os = System.getProperty("os.name", "").toLowerCase();
            if (os.contains("win")) {
                if (launch("powershell", "-NoProfile", "-NonInteractive",
                           "-Command", "Start-Process '" + url.replace("'", "''") + "'")) return;
                if (launch("rundll32", "url.dll,FileProtocolHandler", url)) return;
            } else if (os.contains("mac")) {
                if (launch("open", url)) return;
            } else {
                if (launch("xdg-open", url)) return;
            }
            try { java.awt.Desktop.getDesktop().browse(URI.create(url)); }
            catch (Exception ignored) {}
        }, "luminamc-browse").start();
    }

    private static boolean launch(String... cmd) {
        try { new ProcessBuilder(cmd).redirectErrorStream(true).start(); return true; }
        catch (Exception e) { return false; }
    }
}
