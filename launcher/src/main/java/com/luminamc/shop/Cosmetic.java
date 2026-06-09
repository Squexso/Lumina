package com.luminamc.shop;

/**
 * A purchasable cosmetic in the Lumina shop. Priced in Lumina Tokens.
 *
 * @param id          stable identifier persisted in {@code ownedCosmetics} / {@code equippedCape}
 * @param name        display name
 * @param description shop blurb
 * @param price       cost in Lumina Tokens
 * @param kind        what slot/category this cosmetic occupies
 * @param rarity      rarity tier (drives the badge colour and rough price band)
 * @param colorTop    primary hex colour (cape gradient top / accessory colour)
 * @param colorBottom secondary hex colour (cape gradient bottom)
 * @param accessoryType render variant for accessories ({@code "halo"}/{@code "wings"}/{@code "aura"}); {@code null} for capes
 */
public record Cosmetic(String id, String name, String description, long price, Kind kind,
                       Rarity rarity, String colorTop, String colorBottom, String accessoryType) {

    public enum Kind { CAPE, ACCESSORY }
}
