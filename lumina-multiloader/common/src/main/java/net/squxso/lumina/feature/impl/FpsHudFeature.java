package net.squxso.lumina.feature.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.squxso.lumina.feature.HudFeature;

/** Toggleable FPS counter (movable/scalable via the HUD editor). */
public final class FpsHudFeature extends HudFeature {

    private final Setting<Integer> color = add(Setting.color("color", "Text color", 0xFFE9D5FF));

    public FpsHudFeature() { super("fps_hud", "FPS Counter", "Show your current frames per second.", 6, 6); }

    private String text(Minecraft mc) { return mc.getFps() + " FPS"; }

    @Override public void render(GuiGraphics ctx, Minecraft mc) {
        ctx.drawString(mc.font, text(mc), 0, 0, color.asInt());
    }
    @Override public int width(Minecraft mc) { return mc.font.width(text(mc)); }
    @Override public int height(Minecraft mc) { return 9; }
}
