package net.squxso.lumina.feature.impl;

import net.minecraft.client.MinecraftClient;
import net.squxso.lumina.feature.Feature;
import net.squxso.lumina.feature.FeatureCategory;

/**
 * Clean Chat — removes the grey background box behind chat lines for a minimal,
 * transparent chat. Implemented via the vanilla "Text Background Opacity" option
 * (set to 0 while enabled, restored on disable) — reliable on every build, no
 * fragile mixin needed.
 */
public final class ChatFeature extends Feature {

    private double saved = -1;

    public ChatFeature() {
        super("chat", "Clean Chat",
                "Remove the grey background boxes behind chat lines — minimal, transparent chat.",
                FeatureCategory.CHAT, false);
    }

    @Override protected void onEnabled() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null) saved = mc.options.getTextBackgroundOpacity().getValue();
    }

    @Override protected void onDisabled() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null && saved >= 0) mc.options.getTextBackgroundOpacity().setValue(saved);
    }

    @Override public void onClientTick(MinecraftClient mc) {
        if (mc.options != null) mc.options.getTextBackgroundOpacity().setValue(0.0);
    }
}
