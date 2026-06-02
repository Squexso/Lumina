package net.squxso.lumina.feature.impl;

import net.squxso.lumina.feature.Feature;
import net.squxso.lumina.feature.FeatureCategory;

/**
 * OptiFine-style zoom. Hold the zoom key (default C) to zoom in; the zoom factor
 * (2×–20×) is adjustable in the feature's settings. The actual FOV change is
 * applied by {@code LuminaZoomMixin}, which reads {@link #active} + {@link #factor()}.
 */
public final class ZoomFeature extends Feature {

    private static ZoomFeature instance;

    /** Set each tick by the client when the zoom key is held + this feature is on. */
    public static volatile boolean active = false;

    private final Setting<Integer> factor = add(Setting.intRange("factor", "Zoom (x)", 4, 2, 20));

    public ZoomFeature() {
        super("zoom", "Zoom", "Hold the zoom key (default C) to zoom in. Adjustable 2x–20x.",
                FeatureCategory.MISC, false);
        instance = this;
    }

    public static boolean featureEnabled() { return instance != null && instance.isEnabled(); }
    public static int factor() { return instance != null ? instance.factor.asInt() : 4; }
}
