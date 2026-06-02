package net.squxso.lumina.feature.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.util.math.Direction;
import net.squxso.lumina.feature.HudFeature;

/** Cardinal-direction HUD, movable/scalable. */
public final class DirectionHudFeature extends HudFeature {

    private final Setting<Integer> color = add(Setting.color("color", "Text color", 0xFFB7AED0));

    public DirectionHudFeature() { super("direction_hud", "Direction", "Show which way you're facing (N/S/E/W).", 6, 42); }

    private String text(MinecraftClient mc) {
        Direction d = mc.player.getHorizontalFacing();
        String label = switch (d) { case NORTH -> "N"; case SOUTH -> "S"; case EAST -> "E"; case WEST -> "W"; default -> "?"; };
        return "Facing: " + label + " §7(" + d.asString() + ")";
    }
    @Override public void render(DrawContext ctx, MinecraftClient mc) {
        ctx.drawTextWithShadow(mc.textRenderer, text(mc), 0, 0, color.asInt());
    }
    @Override public int width(MinecraftClient mc) { return mc.textRenderer.getWidth("Facing: W (north)"); }
    @Override public int height(MinecraftClient mc) { return 9; }
}
