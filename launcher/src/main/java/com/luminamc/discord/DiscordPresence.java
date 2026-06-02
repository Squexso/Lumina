package com.luminamc.discord;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.net.StandardProtocolFamily;
import java.net.UnixDomainSocketAddress;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.channels.SocketChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.UUID;

/**
 * Minimal, dependency-free Discord <b>Rich Presence</b> client (the local IPC
 * protocol over Discord's named pipe / unix socket). While the launcher runs it
 * shows e.g. <i>"LuminaMC — Developing the launcher"</i> with the Lumina logo in the
 * user's Discord status.
 *
 * <p>Best-effort: if Discord isn't running, or no client id is configured, it stays
 * silent and retries periodically. Requires a Discord application id
 * ({@code discordClientId}); upload the logo as a Rich-Presence art asset named
 * {@code "logo"} in the Discord Developer Portal.
 */
public final class DiscordPresence {

    private static final int OP_HANDSHAKE = 0;
    private static final int OP_FRAME     = 1;

    private final String clientId;
    private final long startEpoch = System.currentTimeMillis() / 1000L;
    private String inviteUrl = "";

    private volatile boolean active;
    private String connectedTo = "?";
    private RandomAccessFile pipe;     // Windows named pipe
    private SocketChannel channel;     // unix domain socket

    public DiscordPresence(String clientId) {
        this.clientId = clientId == null ? "" : clientId.trim();
    }

    /** Adds a "Join our Discord" button (visible to other users viewing your profile). */
    public DiscordPresence button(String url) {
        this.inviteUrl = url == null ? "" : url.trim();
        return this;
    }

    /** Connects (in the background) and shows the given activity; reconnects if Discord starts later. */
    public void start(String details, String state) {
        if (clientId.isEmpty()) { log("disabled (no client id in launcher.json)"); return; }
        log("starting for client id " + clientId);
        Thread t = new Thread(() -> loop(details, state), "luminamc-discord-rpc");
        t.setDaemon(true);
        t.start();
        Runtime.getRuntime().addShutdownHook(new Thread(this::stop, "luminamc-discord-rpc-stop"));
    }

    private void loop(String details, String state) {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                if (!connect()) { log("Discord not found on any ipc pipe (is Discord running?), retrying in 15s"); sleep(15_000); continue; }
                active = true;
                log("connected to " + connectedTo);

                send(OP_HANDSHAKE, handshake());
                readFrame();                                     // wait for READY before sending activity

                send(OP_FRAME, setActivity(details, state));
                String reply = readFrame();
                if (reply.contains("\"evt\":\"ERROR\""))
                    log("rejected by Discord: " + trim(reply));
                else
                    log("presence active: \"" + details + "\"");

                while (active) {
                    sleep(30_000);                               // refresh the elapsed time
                    send(OP_FRAME, setActivity(details, state));
                    if (active) readFrame();                      // consume the response
                }
            } catch (Exception e) {
                log("error: " + e.getClass().getSimpleName() + ": " + e.getMessage());
                closeConn();
                sleep(15_000);
            }
        }
    }

    private static String trim(String s) { return s.length() > 320 ? s.substring(0, 320) + "…" : s; }
    private static void log(String msg) { System.out.println("[Discord] " + msg); }

    /** Clears the activity and closes the connection. */
    public void stop() {
        active = false;
        try { if (isOpen()) send(OP_FRAME, clearActivity()); } catch (Exception ignored) {}
        closeConn();
    }

    // ── connection ─────────────────────────────────────────────────────────

    private boolean connect() {
        boolean windows = System.getProperty("os.name", "").toLowerCase().contains("win");
        for (int i = 0; i < 10; i++) {
            try {
                if (windows) {
                    pipe = new RandomAccessFile("\\\\.\\pipe\\discord-ipc-" + i, "rw");
                } else {
                    String dir = env("XDG_RUNTIME_DIR", env("TMPDIR", "/tmp"));
                    channel = SocketChannel.open(StandardProtocolFamily.UNIX);
                    channel.connect(UnixDomainSocketAddress.of(Path.of(dir, "discord-ipc-" + i)));
                }
                connectedTo = "discord-ipc-" + i;
                return true;
            } catch (Exception ignored) {
                closeConn();
            }
        }
        return false;
    }

    private boolean isOpen() { return pipe != null || (channel != null && channel.isOpen()); }

    private void closeConn() {
        try { if (pipe != null) pipe.close(); } catch (IOException ignored) {}
        try { if (channel != null) channel.close(); } catch (IOException ignored) {}
        pipe = null; channel = null;
    }

    private synchronized void send(int op, String json) throws IOException {
        byte[] data = json.getBytes(StandardCharsets.UTF_8);
        ByteBuffer buf = ByteBuffer.allocate(8 + data.length).order(ByteOrder.LITTLE_ENDIAN);
        buf.putInt(op).putInt(data.length).put(data).flip();
        if (pipe != null) {
            pipe.write(buf.array());
        } else if (channel != null) {
            while (buf.hasRemaining()) channel.write(buf);
        } else {
            throw new IOException("not connected");
        }
    }

    /** Reads one IPC frame (8-byte header + payload) and returns its JSON body. */
    private String readFrame() throws IOException {
        byte[] header = new byte[8];
        readFully(header);
        int len = ByteBuffer.wrap(header).order(ByteOrder.LITTLE_ENDIAN).getInt(4);
        if (len <= 0 || len > (1 << 20)) return "";
        byte[] payload = new byte[len];
        readFully(payload);
        return new String(payload, StandardCharsets.UTF_8);
    }

    private void readFully(byte[] b) throws IOException {
        if (pipe != null) { pipe.readFully(b); return; }
        ByteBuffer buf = ByteBuffer.wrap(b);
        while (buf.hasRemaining()) if (channel.read(buf) < 0) throw new IOException("eof");
    }

    // ── payloads ───────────────────────────────────────────────────────────

    private String handshake() {
        JsonObject o = new JsonObject();
        o.addProperty("v", 1);
        o.addProperty("client_id", clientId);
        return o.toString();
    }

    private String setActivity(String details, String state) {
        JsonObject activity = new JsonObject();
        if (details != null && !details.isBlank()) activity.addProperty("details", details);
        if (state != null && !state.isBlank())     activity.addProperty("state", state);

        JsonObject ts = new JsonObject();
        ts.addProperty("start", startEpoch);
        activity.add("timestamps", ts);

        JsonObject assets = new JsonObject();
        assets.addProperty("large_image", "logo");
        assets.addProperty("large_text", "LuminaMC");
        activity.add("assets", assets);

        if (!inviteUrl.isEmpty()) {
            JsonObject b = new JsonObject();
            b.addProperty("label", "Join our Discord");
            b.addProperty("url", inviteUrl);
            JsonArray buttons = new JsonArray();
            buttons.add(b);
            activity.add("buttons", buttons);
        }

        JsonObject args = new JsonObject();
        args.addProperty("pid", (int) ProcessHandle.current().pid());
        args.add("activity", activity);

        return frame("SET_ACTIVITY", args);
    }

    private String clearActivity() {
        JsonObject args = new JsonObject();
        args.addProperty("pid", (int) ProcessHandle.current().pid());
        args.add("activity", com.google.gson.JsonNull.INSTANCE);
        return frame("SET_ACTIVITY", args);
    }

    private String frame(String cmd, JsonObject args) {
        JsonObject o = new JsonObject();
        o.addProperty("cmd", cmd);
        o.add("args", args);
        o.addProperty("nonce", UUID.randomUUID().toString());
        return o.toString();
    }

    private static String env(String key, String fallback) {
        String v = System.getenv(key);
        return v == null || v.isBlank() ? fallback : v;
    }
    private static void sleep(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException e) { Thread.currentThread().interrupt(); }
    }
}
