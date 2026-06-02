package net.squxso.lumina.feature.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.core.Direction;
import net.squxso.lumina.feature.HudFeature;

/** Cardinal-direction HUD, movable/scalable. */
public final class DirectionHudFeature extends HudFeature {

    private final Setting<Integer> color = add(Setting.color("color", "Text color", 0xFFB7AED0));

    public DirectionHudFeature() { super("direction_hud", "Direction", "Show which way you're facing (N/S/E/W).", 6, 42); }

    private String text(Minecraft mc) {
        Direction d = mc.player.getDirection();
        String label = switch (d) { case NORTH -> "N"; case SOUTH -> "S"; case EAST -> "E"; case WEST -> "W"; default -> "?"; };
        return "Facing: " + label + " §7(" + d.getName() + ")";
    }
    @Override public void render(GuiGraphics ctx, Minecraft mc) {
        ctx.drawString(mc.font, text(mc), 0, 0, color.asInt());
    }
    @Override public int width(Minecraft mc) { return mc.font.width("Facing: W (north)"); }
    @Override public int height(Minecraft mc) { return 9; }
}
