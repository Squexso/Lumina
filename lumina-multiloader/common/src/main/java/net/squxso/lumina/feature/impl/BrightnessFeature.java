package net.squxso.lumina.feature.impl;

import net.minecraft.client.Minecraft;
import net.squxso.lumina.feature.Feature;
import net.squxso.lumina.feature.FeatureCategory;

/** Raises the gamma/brightness (vanilla "Bright") so caves and nights are easier to see. */
public final class BrightnessFeature extends Feature {

    private Double saved;
    private final Setting<Integer> level = add(Setting.intRange("level", "Brightness (%)", 100, 50, 100));

    public BrightnessFeature() {
        super("brightness", "Brightness Boost", "Crank up the brightness (vanilla gamma).", FeatureCategory.MISC, false);
    }

    @Override public void onClientTick(Minecraft mc) {
        if (mc.options == null) return;
        if (saved == null) saved = mc.options.gamma().get();
        mc.options.gamma().set(level.asInt() / 100.0);
    }

    @Override protected void onDisabled() {
        Minecraft mc = Minecraft.getInstance();
        if (saved != null && mc.options != null) mc.options.gamma().set(saved);
        saved = null;
    }
}
