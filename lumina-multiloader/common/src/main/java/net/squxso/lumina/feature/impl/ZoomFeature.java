package net.squxso.lumina.feature.impl;

import net.minecraft.client.Minecraft;
import net.squxso.lumina.feature.Feature;
import net.squxso.lumina.feature.FeatureCategory;
import org.lwjgl.glfw.GLFW;

/**
 * OptiFine-style Zoom: hold <b>C</b> to zoom in. Purely a view/FOV change — no gameplay
 * advantage, allowed everywhere. The actual FOV scaling is applied by {@code LuminaFovMixin}.
 */
public final class ZoomFeature extends Feature {

    private static ZoomFeature instance;
    private static volatile boolean held;

    private final Setting<Integer> strength = add(Setting.intRange("strength", "Zoom strength (x)", 4, 2, 10));

    public ZoomFeature() {
        super("zoom", "Zoom", "Hold C to zoom in (view-only, OptiFine-style).", FeatureCategory.VISUAL, false);
        instance = this;
    }

    @Override
    public void onClientTick(Minecraft mc) {
        held = mc.screen == null
                && GLFW.glfwGetKey(mc.getWindow().handle(), GLFW.GLFW_KEY_C) == GLFW.GLFW_PRESS;
    }

    /** FOV multiplier read every frame by {@code LuminaFovMixin} (1.0 = no zoom). */
    public static float multiplier() {
        if (instance == null || !instance.isEnabled() || !held) return 1f;
        return 1f / Math.max(2, instance.strength.asInt());
    }
}
