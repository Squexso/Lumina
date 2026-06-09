package com.luminamc.shop;

import com.luminamc.config.LauncherConfig;

import java.util.ArrayList;

/**
 * The Lumina Tokens wallet — the single place that reads/writes the economy
 * fields on {@link LauncherConfig}. Tokens are earned passively by playing
 * (so the shop is reachable for free) and, later, bought with real money via
 * PayPal token packs.
 *
 * <p>Every mutating call persists immediately, so a crash never loses tokens.
 */
public final class TokenEconomy {

    /** 5 tokens earned per hour of in-game playtime (one token every 12 minutes). */
    public static final long MS_PER_TOKEN = 720_000L;

    /** One-time grant the first time the wallet is initialised. */
    public static final long WELCOME_BONUS = 50L;

    private final LauncherConfig cfg;

    public TokenEconomy(LauncherConfig cfg) {
        this.cfg = cfg;
        if (cfg.ownedCosmetics == null) cfg.ownedCosmetics = new ArrayList<>();
        if (cfg.redeemedCodes == null) cfg.redeemedCodes = new ArrayList<>();
        if (cfg.redemptionLog == null) cfg.redemptionLog = new ArrayList<>();
    }

    /** How many redemption-history entries are kept. */
    private static final int MAX_LOG = 50;

    /**
     * Brings the balance up to date against the lifetime playtime: grants the
     * one-time welcome bonus and converts any not-yet-credited playtime into
     * tokens (5 per hour). The sub-interval remainder is carried forward.
     *
     * @param totalPlayMillis the launcher's lifetime {@code totalPlayMillis}
     * @return how many tokens were just granted (for a "you earned X" toast)
     */
    public long sync(long totalPlayMillis) {
        long gained = 0;

        if (!cfg.luminaWelcomeGiven) {
            cfg.luminaWelcomeGiven = true;
            cfg.luminaTokens += WELCOME_BONUS;
            gained += WELCOME_BONUS;
        }

        if (totalPlayMillis > cfg.tokensAccruedMillis) {
            long pending = totalPlayMillis - cfg.tokensAccruedMillis;
            long earned = pending / MS_PER_TOKEN;
            if (earned > 0) {
                cfg.luminaTokens += earned;
                cfg.tokensAccruedMillis += earned * MS_PER_TOKEN; // keep the remainder
                gained += earned;
            }
        }

        if (gained > 0) cfg.save();
        return gained;
    }

    public long balance() { return cfg.luminaTokens; }

    public boolean owns(String cosmeticId) {
        return cfg.ownedCosmetics != null && cfg.ownedCosmetics.contains(cosmeticId);
    }

    public boolean canAfford(long price) { return cfg.luminaTokens >= price; }

    /** Tokens still needed to afford {@code price} (0 if affordable). */
    public long shortfall(long price) { return Math.max(0, price - cfg.luminaTokens); }

    /**
     * Buys a cosmetic if affordable and not already owned. Deducts the price,
     * records ownership, and persists.
     *
     * @return {@code true} if the user now owns it (bought just now or already did)
     */
    public boolean buy(Cosmetic c) {
        if (c == null) return false;
        if (owns(c.id())) return true;
        if (cfg.luminaTokens < c.price()) return false;
        cfg.luminaTokens -= c.price();
        cfg.ownedCosmetics.add(c.id());
        cfg.save();
        return true;
    }

    public boolean isCapeEquipped(String cosmeticId) {
        return cosmeticId != null && cosmeticId.equals(cfg.equippedCape);
    }

    public String equippedCape() { return cfg.equippedCape; }

    public void equipCape(String cosmeticId) {
        cfg.equippedCape = cosmeticId;
        cfg.save();
    }

    public void unequipCape() {
        cfg.equippedCape = null;
        cfg.save();
    }

    // ── accessory slot ──────────────────────────────────────────────────────

    public boolean isAccessoryEquipped(String cosmeticId) {
        return cosmeticId != null && cosmeticId.equals(cfg.equippedAccessory);
    }

    public String equippedAccessory() { return cfg.equippedAccessory; }

    public void equipAccessory(String cosmeticId) {
        cfg.equippedAccessory = cosmeticId;
        cfg.save();
    }

    public void unequipAccessory() {
        cfg.equippedAccessory = null;
        cfg.save();
    }

    // ── generic (dispatch by cosmetic kind) ─────────────────────────────────

    public boolean isEquipped(Cosmetic c) {
        return c.kind() == Cosmetic.Kind.CAPE ? isCapeEquipped(c.id()) : isAccessoryEquipped(c.id());
    }

    public void equip(Cosmetic c) {
        if (c.kind() == Cosmetic.Kind.CAPE) equipCape(c.id()); else equipAccessory(c.id());
    }

    public void unequip(Cosmetic c) {
        if (c.kind() == Cosmetic.Kind.CAPE) unequipCape(); else unequipAccessory();
    }

    /**
     * Credits tokens directly — used by the (future) PayPal purchase flow once a
     * payment is confirmed. Persists immediately.
     */
    public void grant(long tokens) {
        if (tokens <= 0) return;
        cfg.luminaTokens += tokens;
        cfg.save();
    }

    // ── creator codes ────────────────────────────────────────────────────────

    public enum RedeemStatus { SUCCESS, INVALID, ALREADY_USED }

    /** Outcome of a {@link #redeem} attempt; {@code tokens} is the amount granted on success. */
    public record RedeemResult(RedeemStatus status, long tokens) {}

    /**
     * Redeems a creator code. Repeatable codes always pay out; one-time codes pay
     * out once per launcher (tracked in {@code redeemedCodes}). Persists on success.
     */
    public RedeemResult redeem(String input) {
        CreatorCode code = ShopCatalog.creatorCode(input);
        if (code == null) return new RedeemResult(RedeemStatus.INVALID, 0);

        if (!code.repeatable()) {
            if (cfg.redeemedCodes.contains(code.code())) {
                return new RedeemResult(RedeemStatus.ALREADY_USED, 0);
            }
            cfg.redeemedCodes.add(code.code());
        }
        cfg.luminaTokens += code.tokens();
        cfg.redemptionLog.add(new RedemptionEntry(code.code(), code.tokens(), System.currentTimeMillis()));
        while (cfg.redemptionLog.size() > MAX_LOG) cfg.redemptionLog.remove(0);
        cfg.save();
        return new RedeemResult(RedeemStatus.SUCCESS, code.tokens());
    }

    /** The redemption history, oldest first. */
    public java.util.List<RedemptionEntry> log() { return cfg.redemptionLog; }

    /** Credits tokens and records a labelled entry in the history log. Persists. */
    public void grantLogged(String label, long tokens) {
        if (tokens <= 0) return;
        cfg.luminaTokens += tokens;
        cfg.redemptionLog.add(new RedemptionEntry(label, tokens, System.currentTimeMillis()));
        while (cfg.redemptionLog.size() > MAX_LOG) cfg.redemptionLog.remove(0);
        cfg.save();
    }
}
