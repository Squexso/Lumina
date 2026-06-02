package net.squxso.lumina.feature.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.squxso.lumina.feature.HudFeature;

/** XYZ coordinates HUD, movable/scalable. */
public final class CoordinatesHudFeature extends HudFeature {

    private final Setting<Integer> color = add(Setting.color("color", "Text color", 0xFFB7AED0));

    public CoordinatesHudFeature() { super("coords_hud", "Coordinates", "Show your X / Y / Z position.", 6, 30); }

    private String text(MinecraftClient mc) {
        var p = mc.player;
        return String.format("XYZ: %.0f / %.0f / %.0f", p.getX(), p.getY(), p.getZ());
    }
    @Override public void render(DrawContext ctx, MinecraftClient mc) {
        ctx.drawTextWithShadow(mc.textRenderer, text(mc), 0, 0, color.asInt());
    }
    @Override public int width(MinecraftClient mc) { return mc.textRenderer.getWidth(text(mc)); }
    @Override public int height(MinecraftClient mc) { return 9; }
}
