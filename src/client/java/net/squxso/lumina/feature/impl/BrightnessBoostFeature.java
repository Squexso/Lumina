package net.squxso.lumina.feature.impl;

import net.minecraft.client.MinecraftClient;
import net.squxso.lumina.feature.Feature;
import net.squxso.lumina.feature.FeatureCategory;

/**
 * Raises screen brightness to the vanilla maximum while enabled, restoring the
 * previous value when turned off. (Pure option tweak — no mixin.)
 */
public final class BrightnessBoostFeature extends Feature {

    private double saved = -1;

    public BrightnessBoostFeature() {
        super("brightness", "Brightness Boost", "Raise screen brightness to maximum (easier to see in caves).",
                FeatureCategory.VISUAL, false);
    }

    @Override protected void onEnabled() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null) saved = mc.options.getGamma().getValue();
    }

    @Override protected void onDisabled() {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.options != null && saved >= 0) mc.options.getGamma().setValue(saved);
    }

    @Override public void onClientTick(MinecraftClient mc) {
        if (mc.options != null) mc.options.getGamma().setValue(1.0);
    }
}
