package net.squxso.lumina.feature.impl;

import net.squxso.lumina.feature.Feature;
import net.squxso.lumina.feature.FeatureCategory;

/**
 * Master toggle for the Lumina UI re-skin (themed buttons, menu backgrounds).
 * The theming mixins read {@code FeatureManager.isEnabled("ui_theme")}, so
 * turning this off restores the plain vanilla menus.
 */
public final class UiThemeFeature extends Feature {
    public UiThemeFeature() {
        super("ui_theme", "Lumina UI Theme",
                "Re-skin vanilla menus & buttons in the Lumina style. Turn off for plain vanilla menus.",
                FeatureCategory.MISC, true);
    }
}
