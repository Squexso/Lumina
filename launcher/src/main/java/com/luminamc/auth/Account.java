package com.luminamc.auth;

import java.util.UUID;

/** A signed-in Minecraft account, persisted (with tokens) to accounts.json. */
public final class Account {

    public String id = UUID.randomUUID().toString();

    public String username;      // Minecraft profile name
    public String mcUuid;        // Minecraft profile UUID (no dashes)
    public String mcAccessToken; // Minecraft session token used at launch
    public String msRefreshToken;// Microsoft refresh token for silent re-auth
    public long   expiresAtEpochMs;
    public String  type = "msa";
    public boolean usedCustomClientId = false;

    public boolean isExpired() {
        return System.currentTimeMillis() >= expiresAtEpochMs - 60_000; // 1 min skew
    }

    /** UUID formatted with dashes, as Minecraft argument templates expect. */
    public String dashedUuid() {
        if (mcUuid == null) return new UUID(0, 0).toString();
        if (mcUuid.contains("-")) return mcUuid;
        return mcUuid.replaceFirst(
                "(\\p{XDigit}{8})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}{4})(\\p{XDigit}+)",
                "$1-$2-$3-$4-$5");
    }
}
