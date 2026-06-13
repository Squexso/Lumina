package com.luminamc.shop;

import com.luminamc.config.LauncherConfig;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.HexFormat;

/**
 * VIP membership for LuminaMC.
 *
 * <p>Purchasing VIP costs {@link #PRICE} tokens and produces a one-time
 * redemption code the user pastes into Discord ({@code /redeem-vip CODE}).
 * The Discord bot validates the code cryptographically using the same shared
 * secret — no central server required.
 *
 * <p>Code format: {@code VIP-XXXXXXXX-YYYYYY}
 * <ul>
 *   <li>{@code XXXXXXXX} — 8 hex chars (4 random bytes)</li>
 *   <li>{@code YYYYYY}   — 6 hex chars (first 3 bytes of HMAC-SHA256(XXXXXXXX, secret))</li>
 * </ul>
 */
public final class VipManager {

    public static final long   PRICE          = 15_000L;
    public static final String DEFAULT_SECRET = "lumina-vip-2026-squexso";

    private VipManager() {}

    /**
     * Attempts to purchase VIP for the given account. Deducts tokens, generates
     * a signed code, and persists both to {@code config}.
     *
     * @return the redemption code, or {@code null} if the user can't afford it
     *         or already owns VIP
     */
    public static String purchase(LauncherConfig config) {
        if (config.vipOwned) return config.vipRedeemCode;         // already VIP
        long balance = new TokenEconomy(config).balance();
        if (balance < PRICE) return null;                          // not enough tokens

        // Deduct tokens.
        config.luminaTokens -= PRICE;
        // Generate and save the code so the user can copy it later.
        String code = generateCode(secret(config));
        config.vipOwned = true;
        config.vipRedeemCode = code;
        config.save();
        return code;
    }

    /** Generates a new signed VIP redemption code. */
    public static String generateCode(String secret) {
        byte[] salt = new byte[4];
        new SecureRandom().nextBytes(salt);
        String saltHex = HexFormat.of().formatHex(salt).toUpperCase();
        String check   = hmac6(saltHex, secret);
        return "VIP-" + saltHex + "-" + check;
    }

    /**
     * Validates a code against the given secret.
     * Does NOT check whether the code has been used — that is the bot's job.
     */
    public static boolean isValid(String code, String secret) {
        if (code == null) return false;
        String[] parts = code.split("-");
        if (parts.length != 3 || !"VIP".equals(parts[0])) return false;
        if (parts[1].length() != 8 || parts[2].length() != 6) return false;
        return parts[2].equalsIgnoreCase(hmac6(parts[1].toUpperCase(), secret));
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** Returns the configured secret, falling back to the default. */
    public static String secret(LauncherConfig config) {
        String s = config.vipCodeSecret;
        return (s != null && !s.isBlank()) ? s.trim() : DEFAULT_SECRET;
    }

    private static String hmac6(String data, String secret) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] raw = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(raw).substring(0, 6).toUpperCase();
        } catch (Exception e) {
            return "FFFFFF";
        }
    }
}
