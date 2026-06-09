package net.squxso.lumina.feature.impl;

import net.squxso.lumina.feature.Feature;
import net.squxso.lumina.feature.FeatureCategory;

/**
 * The Lumina Cape cosmetic. Unlocked and equipped in the LuminaMC launcher shop;
 * the launcher writes {@code features.lumina_cape} into the instance's
 * {@code config/lumina.json}, and {@link net.squxso.lumina.mixin.LuminaCapeMixin}
 * renders it client-side on the local player.
 *
 * <p>It is registered like any other feature (so it loads/saves and
 * {@link net.squxso.lumina.feature.FeatureManager#isEnabled} works), but lives in
 * the hidden {@link FeatureCategory#COSMETIC} category, so it never shows up as a
 * toggle in the Right-Shift panel — equipping is done from the launcher.
 */
public final class CapeCosmeticFeature extends Feature {

    public CapeCosmeticFeature() {
        super("lumina_cape", "Lumina Cape",
                "Wear your Lumina Cape (unlocked in the LuminaMC shop).",
                FeatureCategory.COSMETIC, false);
    }
}
