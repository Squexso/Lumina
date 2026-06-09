package com.luminamc.shop;

/** Cosmetic rarity tiers, each with a display label and signature colour. */
public enum Rarity {
    COMMON("Common", "#9CA3AF"),
    UNCOMMON("Uncommon", "#22C55E"),
    RARE("Rare", "#3B82F6"),
    EPIC("Epic", "#A855F7"),
    LEGENDARY("Legendary", "#F59E0B");

    public final String label;
    public final String color;

    Rarity(String label, String color) {
        this.label = label;
        this.color = color;
    }
}
