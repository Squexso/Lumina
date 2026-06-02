package com.luminamc.tools;

import com.sun.net.httpserver.HttpServer;

import java.io.OutputStream;
import java.net.Inet4Address;
import java.net.InetSocketAddress;
import java.net.NetworkInterface;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Collections;

/**
 * Minimal static HTTP server for hosting LuminaMC update releases.
 *
 * <p>Serves a directory (the staged {@code build/release} folder containing
 * {@code latest.json} + the app jar) so the launcher's auto-updater can fetch
 * from it. Run via the {@code serveUpdates} Gradle task.
 *
 * <p>Usage: {@code UpdateServer <dir> [port]}  (default port 8770).
 */
public final class UpdateServer {

    private UpdateServer() {}

    public static void main(String[] args) throws Exception {
        Path root = Paths.get(args.length > 0 ? args[0] : ".").toAbsolutePath().normalize();
        int port = args.length > 1 ? Integer.parseInt(args[1]) : 8770;

        if (!Files.isDirectory(root)) {
            System.err.println("Release directory does not exist yet: " + root);
            System.err.println("Run  gradlew -p launcher publishRelease  first.");
            return;
        }

        HttpServer server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext("/", exchange -> {
            String path = exchange.getRequestURI().getPath();
            if (path == null || path.equals("/") || path.isBlank()) path = "/latest.json";
            Path file = root.resolve(path.substring(1)).normalize();
            if (!file.startsWith(root) || !Files.isRegularFile(file)) {
                exchange.sendResponseHeaders(404, -1);
                exchange.close();
                return;
            }
            byte[] data = Files.readAllBytes(file);
            String ct = file.getFileName().toString().endsWith(".json")
                    ? "application/json" : "application/octet-stream";
            exchange.getResponseHeaders().set("Content-Type", ct);
            exchange.getResponseHeaders().set("Cache-Control", "no-cache");
            System.out.println("  → " + exchange.getRequestMethod() + " " + path);
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) { os.write(data); }
        });
        server.start();

        System.out.println("LuminaMC update server — serving " + root);
        System.out.println("  Local:    http://localhost:" + port + "/latest.json");
        printLanUrls(port);
        System.out.println("Point the launcher's update URL at one of the above. Ctrl+C to stop.");
        Thread.currentThread().join();
    }

    private static void printLanUrls(int port) {
        try {
            for (NetworkInterface ni : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!ni.isUp() || ni.isLoopback() || ni.isVirtual()) continue;
                for (var addr : Collections.list(ni.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        System.out.println("  Network:  http://" + addr.getHostAddress() + ":" + port + "/latest.json");
                    }
                }
            }
        } catch (Exception ignored) {}
    }
}
