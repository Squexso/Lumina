package net.squxso.lumina.feature.impl;

import net.minecraft.client.Minecraft;
import net.squxso.lumina.feature.Feature;
import net.squxso.lumina.feature.FeatureCategory;

/** Disables the walking camera bob — pure visual preference. */
public final class NoBobbingFeature extends Feature {

    private Boolean saved;

    public NoBobbingFeature() {
        super("no_bobbing", "No View Bobbing", "Disable the camera bob while walking.", FeatureCategory.MISC, false);
    }

    @Override public void onClientTick(Minecraft mc) {
        if (mc.options == null) return;
        if (saved == null) saved = mc.options.bobView().get();
        mc.options.bobView().set(false);
    }

    @Override protected void onDisabled() {
        Minecraft mc = Minecraft.getInstance();
        if (saved != null && mc.options != null) mc.options.bobView().set(saved);
        saved = null;
    }
}
