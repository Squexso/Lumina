package com.luminamc.shop;

/**
 * A redeemable creator/promo code that grants free Lumina Tokens.
 *
 * @param code       the code to type (compared case-insensitively, stored lower-case)
 * @param tokens     how many Lumina Tokens redeeming grants
 * @param repeatable {@code true} = can be redeemed any number of times;
 *                   {@code false} = one redemption per launcher
 */
public record CreatorCode(String code, long tokens, boolean repeatable) {}
