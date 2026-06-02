package net.squxso.lumina.feature.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.squxso.lumina.feature.HudFeature;

/** Toggleable FPS counter (movable/scalable via the HUD editor). */
public final class FpsHudFeature extends HudFeature {

    private final Setting<Integer> color = add(Setting.color("color", "Text color", 0xFFE9D5FF));

    public FpsHudFeature() { super("fps_hud", "FPS Counter", "Show your current frames per second.", 6, 6); }

    private String text(MinecraftClient mc) { return mc.getCurrentFps() + " FPS"; }

    @Override public void render(DrawContext ctx, MinecraftClient mc) {
        ctx.drawTextWithShadow(mc.textRenderer, text(mc), 0, 0, color.asInt());
    }
    @Override public int width(MinecraftClient mc) { return mc.textRenderer.getWidth(text(mc)); }
    @Override public int height(MinecraftClient mc) { return 9; }
}
