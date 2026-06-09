package net.squxso.lumina.feature.impl;

import net.squxso.lumina.feature.Feature;
import net.squxso.lumina.feature.FeatureCategory;

/**
 * Fullbright: brightens the lightmap so even pitch-black caves are fully lit. Applied by
 * {@code LuminaLightMixin}. Purely a brightness change — it does NOT reveal hidden blocks
 * or show anything through walls (not X-ray), so it stays fair.
 */
public final class FullbrightFeature extends Feature {

    private static FullbrightFeature instance;

    public FullbrightFeature() {
        super("fullbright", "Fullbright", "See in total darkness (brightness only — not X-ray).",
                FeatureCategory.MISC, false);
        instance = this;
    }

    public static boolean active() { return instance != null && instance.isEnabled(); }
}
