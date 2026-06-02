package com.luminamc.auth;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;

/**
 * OAuth 2.0 Authorization Code flow with PKCE (RFC 7636).
 *
 * <p>PKCE replaces the client secret entirely — the {@code code_verifier}
 * is generated fresh at runtime and never leaves the process until it is
 * exchanged (once) with the auth server. There is nothing useful for an
 * attacker to find by decompiling this class.
 *
 * <p>Flow:
 * <ol>
 *   <li>Generate a random {@code code_verifier} and its SHA-256
 *       {@code code_challenge}.</li>
 *   <li>Start a local HTTP server on a random port to receive the redirect.</li>
 *   <li>Open the system browser to the Microsoft authorization URL (includes
 *       {@code code_challenge} and the local redirect URI).</li>
 *   <li>Microsoft redirects to {@code http://localhost:{port}/callback?code=…}</li>
 *   <li>Exchange {@code code + code_verifier} for an MSA access token —
 *       <strong>no secret required</strong>.</li>
 * </ol>
 *
 * <p>The MSA token is then handed to {@link MicrosoftAuth} for the
 * Xbox Live → XSTS → Minecraft services chain, identical for both flows.
 */
public final class PkceAuth {

    private PkceAuth() {}

    private static final SecureRandom RNG = new SecureRandom();

    // Microsoft identity endpoints for personal accounts.
    private static final String AUTH_URL  =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/authorize";
    private static final String TOKEN_URL =
            "https://login.microsoftonline.com/consumers/oauth2/v2.0/token";
    private static final String SCOPE     = "XboxLive.signin offline_access";

    /** A matched pair: the plain verifier (sent at exchange) and its challenge (sent in the URL). */
    public record Pkce(String verifier, String challenge) {}

    /** A running local callback server on a random OS-assigned port. */
    public record CallbackServer(HttpServer server, int port,
                                  CompletableFuture<String> codeFuture) {
        /** Shuts the server down regardless of whether a code arrived. */
        public void stop() { server.stop(0); }
    }

    // ── public API ───────────────────────────────────────────────────────

    /** Generates a fresh PKCE pair. Call once per login attempt. */
    public static Pkce generate() {
        byte[] buf = new byte[96];          // 768 bits → 128-char base64url verifier
        RNG.nextBytes(buf);
        String verifier  = b64url(buf);
        String challenge = b64url(sha256(verifier.getBytes(StandardCharsets.UTF_8)));
        return new Pkce(verifier, challenge);
    }

    /**
     * Builds the Microsoft authorization URL the user's browser must open.
     *
     * @param clientId  the Azure AD application (public client, no secret)
     * @param port      the local callback server's port
     * @param challenge {@link Pkce#challenge()} from {@link #generate()}
     */
    public static String authUrl(String clientId, int port, String challenge) {
        return AUTH_URL
                + "?client_id="             + enc(clientId)
                + "&response_type=code"
                + "&redirect_uri="          + enc(redirectUri(port))
                + "&scope="                 + enc(SCOPE)
                + "&code_challenge="        + challenge      // already base64url, no double-encode
                + "&code_challenge_method=S256"
                + "&prompt=select_account"
                + "&response_mode=query";
    }

    /**
     * Starts a local one-shot HTTP server.  When the browser lands on
     * {@code /callback}, the server extracts the {@code code} (or error),
     * sends a user-friendly HTML page, and completes the future.
     */
    public static CallbackServer startCallbackServer() throws IOException {
        CompletableFuture<String> future = new CompletableFuture<>();
        HttpServer srv = HttpServer.create(new InetSocketAddress("localhost", 0), 1);
        int port = srv.getAddress().getPort();

        srv.createContext("/callback", exchange -> {
            String query = exchange.getRequestURI().getRawQuery();
            String code = null, errorDesc = null;
            if (query != null) {
                for (String part : query.split("&")) {
                    String[] kv = part.split("=", 2);
                    if (kv.length != 2) continue;
                    String k = kv[0], v = URLDecoder.decode(kv[1], StandardCharsets.UTF_8);
                    switch (k) {
                        case "code"              -> code      = v;
                        case "error_description" -> errorDesc = v;
                        case "error"             -> { if (errorDesc == null) errorDesc = v; }
                    }
                }
            }
            boolean ok = code != null;
            String html = callbackHtml(ok, errorDesc);
            byte[] body = html.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().set("Content-Type", "text/html; charset=utf-8");
            exchange.sendResponseHeaders(200, body.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(body); }

            if (ok) {
                future.complete(code);
            } else {
                future.completeExceptionally(new IOException(
                        "Microsoft login failed: " + (errorDesc != null ? errorDesc : "unknown error")));
            }
            // Stop the server after a short delay so the HTML has time to render.
            new Thread(() -> { try { Thread.sleep(400); } catch (Exception ignored) {} srv.stop(0); },
                    "luminamc-cbsrv-stopper").start();
        });
        srv.setExecutor(null); // default executor
        srv.start();
        return new CallbackServer(srv, port, future);
    }

    /**
     * Exchanges the authorization code + PKCE verifier for an MSA access token.
     * <strong>No client secret is used or required.</strong>
     *
     * @return the MSA access token (short-lived) and an optional refresh token.
     */
    public static TokenResponse exchangeCode(String clientId, int port,
                                              String code, String verifier)
            throws IOException, InterruptedException {
        var form = new java.util.LinkedHashMap<String, String>();
        form.put("client_id",     clientId);
        form.put("grant_type",    "authorization_code");
        form.put("code",          code);
        form.put("redirect_uri",  redirectUri(port));
        form.put("code_verifier", verifier);   // ← replaces client_secret
        form.put("scope",         SCOPE);

        var json = com.luminamc.download.Http.postForm(TOKEN_URL, form);
        if (!json.has("access_token")) {
            String err = json.has("error_description") ? json.get("error_description").getAsString()
                       : json.has("error")             ? json.get("error").getAsString()
                       : json.toString();
            throw new IOException("Token exchange failed: " + err);
        }
        return new TokenResponse(
                json.get("access_token").getAsString(),
                json.has("refresh_token") ? json.get("refresh_token").getAsString() : null,
                json.has("expires_in") ? json.get("expires_in").getAsInt() : 3600);
    }

    /** Refreshes an MSA access token using a previously obtained refresh token. */
    public static TokenResponse refreshToken(String clientId, String refreshToken)
            throws IOException, InterruptedException {
        var form = new java.util.LinkedHashMap<String, String>();
        form.put("client_id",     clientId);
        form.put("grant_type",    "refresh_token");
        form.put("refresh_token", refreshToken);
        form.put("scope",         SCOPE);
        var json = com.luminamc.download.Http.postForm(TOKEN_URL, form);
        if (!json.has("access_token")) {
            throw new IOException("Token refresh failed: " + json);
        }
        return new TokenResponse(
                json.get("access_token").getAsString(),
                json.has("refresh_token") ? json.get("refresh_token").getAsString() : refreshToken,
                json.has("expires_in") ? json.get("expires_in").getAsInt() : 3600);
    }

    public record TokenResponse(String accessToken, String refreshToken, int expiresIn) {}

    // ── helpers ──────────────────────────────────────────────────────────

    public static String redirectUri(int port) {
        return "http://localhost:" + port + "/callback";
    }

    private static String callbackHtml(boolean ok, String errorDesc) {
        String title = ok ? "✓ Signed in to LuminaMC!" : "✗ Login failed";
        String msg   = ok ? "You can close this tab and return to LuminaMC."
                          : "Error: " + (errorDesc != null ? errorDesc : "unknown error");
        return """
                <!DOCTYPE html>
                <html><head><meta charset="utf-8"><title>LuminaMC Auth</title>
                <style>
                  body{margin:0;display:flex;align-items:center;justify-content:center;
                       min-height:100vh;background:#16161C;
                       font-family:"Segoe UI",sans-serif;color:#E6E6EE}
                  .box{background:#1F1F28;border:1px solid #2A2A35;border-radius:16px;
                       padding:48px 56px;text-align:center;max-width:480px}
                  h1{margin:0 0 12px;color:%s;font-size:26px}
                  p{margin:0;color:#8C8C9C;font-size:14px;line-height:1.6}
                </style></head>
                <body><div class="box"><h1>%s</h1><p>%s</p></div></body></html>
                """.formatted(ok ? "#A78BFA" : "#F87171", title, msg);
    }

    private static byte[] sha256(byte[] data) {
        try { return MessageDigest.getInstance("SHA-256").digest(data); }
        catch (Exception e) { throw new RuntimeException(e); }
    }

    private static String b64url(byte[] bytes) {
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private static String enc(String s) {
        return URLEncoder.encode(s, StandardCharsets.UTF_8);
    }
}
