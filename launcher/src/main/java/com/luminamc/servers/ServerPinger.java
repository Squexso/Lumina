package com.luminamc.servers;

import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import java.util.Hashtable;

/**
 * Minecraft Server List Ping — the same status query the in-game multiplayer
 * screen uses: MOTD, online/max players, version and latency. Modern protocol
 * (handshake → status request → JSON response) with a best-effort SRV lookup
 * for addresses without an explicit port.
 */
public final class ServerPinger {

    private ServerPinger() {}

    private static final int TIMEOUT_MS = 3000;

    /** The result of one ping; {@code online == false} leaves the rest defaulted. */
    public record Status(boolean online, String motd, int playersOnline, int playersMax,
                         String version, long latencyMs) {
        public static Status offline() { return new Status(false, "", 0, 0, "", 0); }
    }

    /** Pings {@code host[:port]}; never throws — unreachable servers come back offline. */
    public static Status ping(String address) {
        try {
            String host = address.trim();
            int port = 25565;
            int colon = host.lastIndexOf(':');
            boolean explicitPort = colon > 0;
            if (explicitPort) {
                try { port = Integer.parseInt(host.substring(colon + 1)); } catch (NumberFormatException e) { explicitPort = false; }
                host = host.substring(0, colon);
            }
            if (!explicitPort) {
                String[] srv = lookupSrv(host);
                if (srv != null) { host = srv[0]; port = Integer.parseInt(srv[1]); }
            }
            return query(host, port);
        } catch (Exception e) {
            if (Boolean.getBoolean("luminamc.pingDebug")) {
                System.out.println("[ping] " + address + " failed: " + e);
            }
            return Status.offline();
        }
    }

    // ── status query ─────────────────────────────────────────────────────

    private static Status query(String host, int port) throws IOException {
        long start = System.nanoTime();
        try (Socket socket = new Socket()) {
            socket.setSoTimeout(TIMEOUT_MS);
            socket.connect(new InetSocketAddress(host, port), TIMEOUT_MS);
            DataOutputStream out = new DataOutputStream(socket.getOutputStream());
            DataInputStream in = new DataInputStream(socket.getInputStream());

            // Handshake (state 1 = status) + status request — assembled into ONE buffer and
            // sent in a single write: strict proxies (e.g. Hypixel) drop connections whose
            // handshake arrives fragmented across many tiny TCP segments.
            ByteArrayOutputStream hs = new ByteArrayOutputStream();
            DataOutputStream h = new DataOutputStream(hs);
            h.writeByte(0x00);                          // packet id: handshake
            writeVarInt(h, 767);                        // a real protocol version (1.21) — strict
                                                        // servers reject -1
            byte[] hostBytes = host.getBytes(StandardCharsets.UTF_8);
            writeVarInt(h, hostBytes.length);
            h.write(hostBytes);
            h.writeShort(port);
            writeVarInt(h, 1);                          // next state: status

            ByteArrayOutputStream wire = new ByteArrayOutputStream();
            DataOutputStream w = new DataOutputStream(wire);
            writeVarInt(w, hs.size());
            w.write(hs.toByteArray());
            w.writeByte(0x01);                          // length 1
            w.writeByte(0x00);                          // packet id: status request
            out.write(wire.toByteArray());
            out.flush();

            readVarInt(in);                             // response length
            readVarInt(in);                             // packet id (0x00)
            int jsonLen = readVarInt(in);
            if (jsonLen <= 0 || jsonLen > 1 << 21) return Status.offline();
            byte[] body = new byte[jsonLen];
            in.readFully(body);
            long latency = (System.nanoTime() - start) / 1_000_000;

            JsonObject o = JsonParser.parseString(new String(body, StandardCharsets.UTF_8)).getAsJsonObject();
            String motd = o.has("description") ? flattenText(o.get("description")) : "";
            int onl = 0, max = 0;
            if (o.has("players") && o.get("players").isJsonObject()) {
                JsonObject p = o.getAsJsonObject("players");
                if (p.has("online")) onl = p.get("online").getAsInt();
                if (p.has("max")) max = p.get("max").getAsInt();
            }
            String ver = o.has("version") && o.get("version").isJsonObject()
                    && o.getAsJsonObject("version").has("name")
                    ? o.getAsJsonObject("version").get("name").getAsString() : "";
            ver = ver.replaceAll("§.", "").trim();
            return new Status(true, motd, onl, max, ver, latency);
        }
    }

    /** Flattens a chat-component MOTD (string, or object with text/extra) to plain text. */
    private static String flattenText(JsonElement el) {
        StringBuilder sb = new StringBuilder();
        collect(el, sb);
        // Strip § formatting codes and collapse whitespace/newlines.
        return sb.toString().replaceAll("§.", "").replaceAll("\\s+", " ").trim();
    }

    private static void collect(JsonElement el, StringBuilder sb) {
        if (el == null) return;
        if (el.isJsonPrimitive()) { sb.append(el.getAsString()); return; }
        if (el.isJsonArray()) { for (JsonElement e : el.getAsJsonArray()) collect(e, sb); return; }
        if (el.isJsonObject()) {
            JsonObject o = el.getAsJsonObject();
            if (o.has("text")) sb.append(o.get("text").getAsString());
            if (o.has("extra") && o.get("extra").isJsonArray()) {
                JsonArray ex = o.getAsJsonArray("extra");
                for (JsonElement e : ex) collect(e, sb);
            }
        }
    }

    // ── SRV lookup (best-effort, like the vanilla client) ────────────────

    private static String[] lookupSrv(String host) {
        try {
            Hashtable<String, String> env = new Hashtable<>();
            env.put("java.naming.factory.initial", "com.sun.jndi.dns.DnsContextFactory");
            env.put("com.sun.jndi.dns.timeout.initial", "1500");
            env.put("com.sun.jndi.dns.timeout.retries", "1");
            javax.naming.directory.DirContext ctx = new javax.naming.directory.InitialDirContext(env);
            javax.naming.directory.Attributes attrs =
                    ctx.getAttributes("_minecraft._tcp." + host, new String[]{"SRV"});
            javax.naming.directory.Attribute srv = attrs.get("SRV");
            if (srv == null || srv.size() == 0) return null;
            // "priority weight port target" — take the first record.
            String[] parts = srv.get(0).toString().split(" ");
            String target = parts[3].endsWith(".") ? parts[3].substring(0, parts[3].length() - 1) : parts[3];
            return new String[]{target, parts[2]};
        } catch (Exception e) {
            return null;
        }
    }

    // ── VarInt framing ───────────────────────────────────────────────────

    private static void writeVarInt(DataOutputStream out, int value) throws IOException {
        while ((value & ~0x7F) != 0) {
            out.writeByte((value & 0x7F) | 0x80);
            value >>>= 7;
        }
        out.writeByte(value);
    }

    private static int readVarInt(DataInputStream in) throws IOException {
        int value = 0, position = 0;
        while (true) {
            byte b = in.readByte();
            value |= (b & 0x7F) << position;
            if ((b & 0x80) == 0) return value;
            position += 7;
            if (position >= 32) throw new IOException("VarInt too big");
        }
    }
}
