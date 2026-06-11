package com.luminamc.shop;

import java.util.ArrayList;
import java.util.List;

/**
 * The fixed catalogue of everything the Lumina shop offers: cosmetics paid for
 * in Lumina Tokens, and real-money token bundles (PayPal, coming soon).
 */
public final class ShopCatalog {

    private ShopCatalog() {}

    public static final String LUMINA_CAPE_ID = "lumina_cape";

    /** Standard fair price per rarity tier. */
    private static long price(Rarity r) {
        return switch (r) {
            case COMMON -> 250;
            case UNCOMMON -> 750;
            case RARE -> 1_750;
            case EPIC -> 3_500;
            case LEGENDARY -> 6_000;
        };
    }

    private static Cosmetic cape(String id, String name, Rarity rarity, String top, String bottom, String desc) {
        return new Cosmetic(id, name, desc, price(rarity), Cosmetic.Kind.CAPE, rarity, top, bottom, null);
    }

    private static Cosmetic accessory(String id, String name, Rarity rarity, String color, String type, String desc) {
        return new Cosmetic(id, name, desc, price(rarity), Cosmetic.Kind.ACCESSORY, rarity, color, color, type);
    }

    /** Accessories (separate equip slot from capes), rendered on the 3D model. */
    public static final List<Cosmetic> ACCESSORIES = List.of(
            accessory("star_aura", "Star Aura", Rarity.RARE, "#A855F7", "aura",
                    "A soft cosmic glow that surrounds you."),
            accessory("angel_wings", "Angel Wings", Rarity.EPIC, "#EAF0FF", "wings",
                    "Radiant feathered wings on your back."),
            accessory("halo", "Halo", Rarity.LEGENDARY, "#FCD34D", "halo",
                    "A glowing golden ring floating above your head."),
            accessory("ember_aura", "Ember Aura", Rarity.RARE, "#FDBA74", "aura",
                    "A warm cluster of glowing embers orbiting you."),
            accessory("shadow_wings", "Shadow Wings", Rarity.EPIC, "#94A3B8", "wings",
                    "Dark feathered wings edged with pale light."),
            accessory("void_halo", "Void Halo", Rarity.LEGENDARY, "#A78BFA", "halo",
                    "A glowing violet ring of pure void energy."));

    public static Cosmetic accessory(String id) {
        for (Cosmetic c : ACCESSORIES) if (c.id().equals(id)) return c;
        return null;
    }

    /** Capes, in display order (roughly by rarity). */
    public static final List<Cosmetic> COSMETICS = List.of(
            cape(LUMINA_CAPE_ID, "Lumina Cape", Rarity.COMMON, "#C084FC", "#5B21B6",
                    "The signature Lumina violet cape, crowned with the crystal — the first official LuminaMC cosmetic."),
            cape("emerald_cape", "Emerald Cape", Rarity.UNCOMMON, "#6EE7B7", "#047857",
                    "A lush emerald drape with a soft green glow."),
            cape("crimson_cape", "Crimson Cape", Rarity.UNCOMMON, "#FCA5A5", "#991B1B",
                    "Bold crimson fabric for a fearless look."),
            cape("ocean_cape", "Ocean Cape", Rarity.RARE, "#67E8F9", "#0E7490",
                    "Cool teal waves flowing down your back."),
            cape("sunset_cape", "Sunset Cape", Rarity.RARE, "#FDBA74", "#BE185D",
                    "Warm orange melting into a rose-pink dusk."),
            cape("void_cape", "Void Cape", Rarity.EPIC, "#6B7280", "#0B0B14",
                    "A sleek shadow cape that fades into the void."),
            cape("aurora_cape", "Aurora Cape", Rarity.EPIC, "#5EEAD4", "#7C3AED",
                    "Shifting aurora hues from teal to violet."),
            cape("galaxy_cape", "Galaxy Cape", Rarity.LEGENDARY, "#A5B4FC", "#1E1B4B",
                    "Deep cosmic indigo — wear the night sky itself."),
            cape("phoenix_cape", "Phoenix Cape", Rarity.LEGENDARY, "#FDE68A", "#B91C1C",
                    "Molten gold igniting into fiery red. Reborn in flame."),
            cape("midnight_cape", "Midnight Cape", Rarity.UNCOMMON, "#6366F1", "#0B1026",
                    "Deep indigo dusk fading into the midnight sky."),
            cape("rose_cape", "Rose Cape", Rarity.UNCOMMON, "#FBCFE8", "#9D174D",
                    "Soft rose petals deepening into rich magenta."),
            cape("amber_cape", "Amber Cape", Rarity.RARE, "#FCD34D", "#92400E",
                    "Warm amber glowing down into burnished bronze."),
            cape("frost_cape", "Frost Cape", Rarity.RARE, "#BAE6FD", "#0C4A6E",
                    "Crisp glacial blue, cold as the deep winter."),
            cape("venom_cape", "Venom Cape", Rarity.EPIC, "#A3E635", "#365314",
                    "Toxic venom-green with a faint, sickly glow."),
            cape("celestial_cape", "Celestial Cape", Rarity.LEGENDARY, "#DDD6FE", "#312E81",
                    "Pale starlight drifting over deep cosmic violet."),

            // ── wave 2 ──
            cape("slate_cape", "Slate Cape", Rarity.COMMON, "#CBD5E1", "#334155",
                    "Simple, clean grey slate — an everyday classic."),
            cape("moss_cape", "Moss Cape", Rarity.COMMON, "#86EFAC", "#166534",
                    "Soft mossy green, fresh from the forest floor."),
            cape("sand_cape", "Sand Cape", Rarity.COMMON, "#FDE68A", "#A16207",
                    "Warm desert sand, sun-bleached and calm."),
            cape("coal_cape", "Coal Cape", Rarity.COMMON, "#6B7280", "#111827",
                    "Matte charcoal black with a smoky sheen."),
            cape("lavender_cape", "Lavender Cape", Rarity.UNCOMMON, "#E9D5FF", "#9333EA",
                    "A gentle lavender bloom on a summer breeze."),
            cape("mint_cape", "Mint Cape", Rarity.UNCOMMON, "#99F6E4", "#0F766E",
                    "Cool mint — crisp, clean and refreshing."),
            cape("honey_cape", "Honey Cape", Rarity.UNCOMMON, "#FDE68A", "#D97706",
                    "Golden honey, warm and sweet."),
            cape("berry_cape", "Berry Cape", Rarity.UNCOMMON, "#E879F9", "#86198F",
                    "Juicy wild-berry purple, picked at dusk."),
            cape("flame_cape", "Flame Cape", Rarity.UNCOMMON, "#FDBA74", "#C2410C",
                    "Flickering campfire orange that never burns out."),
            cape("sky_cape", "Sky Cape", Rarity.UNCOMMON, "#BAE6FD", "#0284C7",
                    "A clear summer sky draped over your shoulders."),
            cape("sakura_cape", "Sakura Cape", Rarity.RARE, "#FFE4E6", "#FB7185",
                    "Cherry blossoms drifting in the spring wind."),
            cape("storm_cape", "Storm Cape", Rarity.RARE, "#94A3B8", "#1E293B",
                    "Brooding storm clouds a breath before the strike."),
            cape("tropic_cape", "Tropic Cape", Rarity.RARE, "#5EEAD4", "#065F46",
                    "Tropical waters shading into deep reef green."),
            cape("dawn_cape", "Dawn Cape", Rarity.RARE, "#FDE68A", "#DB2777",
                    "First light bleeding into the fading night."),
            cape("royal_cape", "Royal Cape", Rarity.RARE, "#C4B5FD", "#4C1D95",
                    "Regal violet fit for a throne."),
            cape("bloodmoon_cape", "Bloodmoon Cape", Rarity.RARE, "#F87171", "#450A0A",
                    "The deep red of a rising blood moon."),
            cape("glacier_cape", "Glacier Cape", Rarity.EPIC, "#E0F2FE", "#1E40AF",
                    "Ancient glacial ice, blue to the very core."),
            cape("eclipse_cape", "Eclipse Cape", Rarity.EPIC, "#FCD34D", "#111827",
                    "A golden corona swallowed by darkness."),
            cape("dragonfire_cape", "Dragonfire Cape", Rarity.EPIC, "#FB923C", "#7C2D12",
                    "Searing dragonflame over charred embers."),
            cape("nether_cape", "Nether Cape", Rarity.EPIC, "#FB7185", "#4C0519",
                    "Glowing crimson straight from the depths of the Nether."),
            cape("prism_cape", "Prism Cape", Rarity.EPIC, "#FBCFE8", "#0EA5E9",
                    "Light split through a crystal prism."),
            cape("singularity_cape", "Singularity Cape", Rarity.LEGENDARY, "#E879F9", "#0C0118",
                    "Matter bending into a violet event horizon."),
            cape("solar_cape", "Solar Flare Cape", Rarity.LEGENDARY, "#FDE047", "#EA580C",
                    "A burst of raw solar fire, frozen mid-eruption."),
            cape("abyss_cape", "Abyss Cape", Rarity.LEGENDARY, "#38BDF8", "#020617",
                    "Pale light fading into the deepest ocean trench."),
            cape("spectral_cape", "Spectral Cape", Rarity.LEGENDARY, "#F5F3FF", "#6D28D9",
                    "A ghostly shimmer caught between worlds."));

    /**
     * Real-money token bundles (PayPal — coming soon). Base rate is 1000 tokens
     * per euro; the value bonus grows with pack size
     * (0% → 10% → 20% → 30% → 35% → 40% → 45%).
     */
    public static final List<TokenPack> TOKEN_PACKS = buildPacks();

    private static List<TokenPack> buildPacks() {
        long[][] tiers = {            // { euros, tokens }
                {1,   1_000},
                {5,   5_500},
                {10,  12_000},
                {20,  26_000},
                {35,  47_250},
                {50,  70_000},
                {100, 145_000},
        };
        List<TokenPack> packs = new ArrayList<>();
        for (long[] t : tiers) {
            double euros = t[0];
            long tokens = t[1];
            double base = euros * TokenPack.BASE_TOKENS_PER_EURO;
            int bonus = (int) Math.round((tokens - base) / base * 100.0);
            packs.add(new TokenPack(tokens, euros, bonus == 0 ? null : "+" + bonus + "%"));
        }
        return List.copyOf(packs);
    }

    public static Cosmetic cosmetic(String id) {
        for (Cosmetic c : COSMETICS) if (c.id().equals(id)) return c;
        return null;
    }

    /** Finds a cosmetic (cape or accessory) by id. */
    public static Cosmetic find(String id) {
        Cosmetic c = cosmetic(id);
        return c != null ? c : accessory(id);
    }

    // ── creator / promo codes ────────────────────────────────────────────────

    /** Redeemable creator codes (matched case-insensitively). */
    public static final List<CreatorCode> CREATOR_CODES = List.of(
            new CreatorCode("hodenkobold", 10_000, true));

    /** Looks up a creator code by its text (case-insensitive); {@code null} if unknown. */
    public static CreatorCode creatorCode(String input) {
        if (input == null) return null;
        String norm = input.trim().toLowerCase();
        for (CreatorCode c : CREATOR_CODES) if (c.code().equals(norm)) return c;
        return null;
    }
}
