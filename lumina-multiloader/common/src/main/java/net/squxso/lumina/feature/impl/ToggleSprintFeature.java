package net.squxso.lumina.feature.impl;

import net.minecraft.client.Minecraft;
import net.squxso.lumina.feature.Feature;
import net.squxso.lumina.feature.FeatureCategory;

/**
 * Toggle Sprint: keeps you sprinting while moving forward without holding the sprint
 * key — pure quality-of-life (vanilla even has this as an option), allowed anywhere.
 */
public final class ToggleSprintFeature extends Feature {

    public ToggleSprintFeature() {
        super("toggle_sprint", "Toggle Sprint", "Always sprint while moving forward (no key holding).",
                FeatureCategory.MOVEMENT, false);
    }

    @Override
    public void onClientTick(Minecraft mc) {
        if (mc.player == null || mc.screen != null) return;
        boolean forward = mc.options.keyUp.isDown();
        boolean canSprint = !mc.player.isShiftKeyDown() && mc.player.getFoodData().getFoodLevel() > 6;
        if (forward && canSprint) mc.player.setSprinting(true);
    }
}
