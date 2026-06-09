package com.luminamc.shop;

/**
 * One entry in the creator-code redemption history (persisted in the launcher
 * config). Plain public fields so Gson round-trips it cleanly.
 */
public final class RedemptionEntry {

    public String code;
    public long tokens;
    public long time;     // epoch millis

    public RedemptionEntry() {}

    public RedemptionEntry(String code, long tokens, long time) {
        this.code = code;
        this.tokens = tokens;
        this.time = time;
    }
}
