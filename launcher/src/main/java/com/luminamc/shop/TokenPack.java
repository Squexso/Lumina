package com.luminamc.shop;

/**
 * A real-money token bundle, sold later through PayPal. The bonus grows gently
 * with bundle size so bigger purchases feel rewarding without being unfair.
 *
 * @param tokens how many Lumina Tokens the buyer receives
 * @param euros  the price in euros
 * @param badge  optional "+X%" value-bonus badge ({@code null} for the base pack)
 */
public record TokenPack(long tokens, double euros, String badge) {

    /** Base conversion rate: 1000 tokens per euro before any bonus. */
    public static final double BASE_TOKENS_PER_EURO = 1000.0;

    /** Whole-percent value bonus vs. the base rate (0 for the entry pack). */
    public int bonusPercent() {
        double base = euros * BASE_TOKENS_PER_EURO;
        if (base <= 0) return 0;
        return (int) Math.round((tokens - base) / base * 100.0);
    }
}
