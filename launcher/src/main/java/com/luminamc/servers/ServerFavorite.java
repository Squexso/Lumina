package com.luminamc.servers;

import java.util.UUID;

/**
 * One saved server: a display name, the address ({@code host[:port]}) and an
 * optional instance to join it with ({@code null} = most recently played).
 * Persisted as part of {@code launcher.json}.
 */
public final class ServerFavorite {

    public String id;
    public String name;
    public String address;

    /** Instance to launch for this server; null = use the most recently played one. */
    public String instanceId;

    public long createdAt = System.currentTimeMillis();
    public long lastJoined = 0L;

    public ServerFavorite() {}

    public static ServerFavorite create(String name, String address, String instanceId) {
        ServerFavorite f = new ServerFavorite();
        f.id = UUID.randomUUID().toString().substring(0, 8);
        f.name = name;
        f.address = address;
        f.instanceId = instanceId;
        return f;
    }
}
