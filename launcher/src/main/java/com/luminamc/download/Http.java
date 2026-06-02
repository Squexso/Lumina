package com.luminamc.download;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.MessageDigest;
import java.time.Duration;
import java.util.Map;

/** Shared HTTP client and small helpers for JSON fetches and verified downloads. */
public final class Http {

    private Http() {}

    public static final HttpClient CLIENT = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(20))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    private static final String UA = "LuminaMC-Launcher/0.1.0";

    public static String getString(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", UA)
                .timeout(Duration.ofSeconds(60))
                .GET().build();
        HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("GET " + url + " -> HTTP " + resp.statusCode());
        }
        return resp.body();
    }

    /** Fetches the raw response body — used for binary resources like remote icons. */
    public static byte[] getBytes(String url) throws IOException, InterruptedException {
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", UA)
                .timeout(Duration.ofSeconds(60))
                .GET().build();
        HttpResponse<byte[]> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofByteArray());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("GET " + url + " -> HTTP " + resp.statusCode());
        }
        return resp.body();
    }

    public static JsonObject getJson(String url) throws IOException, InterruptedException {
        return JsonParser.parseString(getString(url)).getAsJsonObject();
    }

    public static JsonArray getJsonArray(String url) throws IOException, InterruptedException {
        return JsonParser.parseString(getString(url)).getAsJsonArray();
    }

    /** POST form-encoded body, returning the parsed JSON object. */
    public static JsonObject postForm(String url, Map<String, String> form) throws IOException, InterruptedException {
        StringBuilder body = new StringBuilder();
        for (Map.Entry<String, String> e : form.entrySet()) {
            if (body.length() > 0) body.append('&');
            body.append(urlEncode(e.getKey())).append('=').append(urlEncode(e.getValue()));
        }
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", UA)
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(body.toString(), StandardCharsets.UTF_8))
                .build();
        HttpResponse<String> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        JsonElement parsed = JsonParser.parseString(resp.body().isBlank() ? "{}" : resp.body());
        JsonObject obj = parsed.isJsonObject() ? parsed.getAsJsonObject() : new JsonObject();
        obj.addProperty("__http_status", resp.statusCode());
        return obj;
    }

    /** POST a JSON body, returning the parsed JSON object. */
    public static JsonObject postJson(String url, String jsonBody, Map<String, String> headers)
            throws IOException, InterruptedException {
        HttpRequest.Builder b = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", UA)
                .header("Content-Type", "application/json")
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(60))
                .POST(HttpRequest.BodyPublishers.ofString(jsonBody, StandardCharsets.UTF_8));
        if (headers != null) headers.forEach(b::header);
        HttpResponse<String> resp = CLIENT.send(b.build(), HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("POST " + url + " -> HTTP " + resp.statusCode() + ": " + resp.body());
        }
        return JsonParser.parseString(resp.body()).getAsJsonObject();
    }

    /**
     * Downloads {@code url} to {@code dest}, reporting bytes via {@code onChunk}.
     * If {@code expectedSha1} is non-null and the existing file already matches,
     * the download is skipped.
     */
    public static void download(String url, Path dest, String expectedSha1, ChunkListener onChunk)
            throws IOException, InterruptedException {
        if (expectedSha1 != null && Files.exists(dest) && sha1(dest).equalsIgnoreCase(expectedSha1)) {
            return; // cached and valid
        }
        Files.createDirectories(dest.getParent());
        HttpRequest req = HttpRequest.newBuilder(URI.create(url))
                .header("User-Agent", UA)
                .timeout(Duration.ofMinutes(10))
                .GET().build();
        HttpResponse<InputStream> resp = CLIENT.send(req, HttpResponse.BodyHandlers.ofInputStream());
        if (resp.statusCode() / 100 != 2) {
            throw new IOException("Download failed " + url + " -> HTTP " + resp.statusCode());
        }
        Path tmp = dest.resolveSibling(dest.getFileName() + ".part");
        try (InputStream in = resp.body();
             var out = Files.newOutputStream(tmp)) {
            byte[] buf = new byte[1 << 16];
            int n;
            while ((n = in.read(buf)) != -1) {
                out.write(buf, 0, n);
                if (onChunk != null) onChunk.onBytes(n);
            }
        }
        if (expectedSha1 != null) {
            String actual = sha1(tmp);
            if (!actual.equalsIgnoreCase(expectedSha1)) {
                Files.deleteIfExists(tmp);
                throw new IOException("SHA1 mismatch for " + url + " (expected " + expectedSha1 + ", got " + actual + ")");
            }
        }
        Files.move(tmp, dest, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
    }

    public static String sha1(Path file) throws IOException {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-1");
            try (InputStream in = Files.newInputStream(file)) {
                byte[] buf = new byte[1 << 16];
                int n;
                while ((n = in.read(buf)) != -1) md.update(buf, 0, n);
            }
            StringBuilder sb = new StringBuilder();
            for (byte b : md.digest()) sb.append(String.format("%02x", b));
            return sb.toString();
        } catch (Exception e) {
            throw new IOException("SHA1 failed for " + file, e);
        }
    }

    private static String urlEncode(String s) {
        return java.net.URLEncoder.encode(s, StandardCharsets.UTF_8);
    }

    /** Callback receiving the number of bytes read in each chunk. */
    @FunctionalInterface
    public interface ChunkListener {
        void onBytes(long bytes);
    }
}
