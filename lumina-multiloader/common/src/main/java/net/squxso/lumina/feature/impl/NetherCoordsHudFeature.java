package net.squxso.lumina.feature.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.Level;
import net.squxso.lumina.feature.HudFeature;

/**
 * Shows the matching coordinates in the other dimension (Overworld ↔ Nether, the 1:8
 * ratio) — so you can build perfectly linked Nether portals.
 */
public final class NetherCoordsHudFeature extends HudFeature {

    private final Setting<Integer> color = add(Setting.color("color", "Text color", 0xFFE9A5FF));

    public NetherCoordsHudFeature() {
        super("nethercoords_hud", "Portal Coords", "Linked-portal coordinates for the other dimension.", 6, 78);
    }

    private String text(Minecraft mc) {
        boolean inNether = Level.NETHER.equals(mc.level.dimension());
        double factor = inNether ? 8.0 : 0.125;
        long x = Math.round(mc.player.getX() * factor);
        long z = Math.round(mc.player.getZ() * factor);
        return (inNether ? "Overworld" : "Nether") + ": " + x + " / " + z;
    }

    @Override public void render(GuiGraphics ctx, Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        ctx.drawString(mc.font, text(mc), 0, 0, color.asInt());
    }
    @Override public int width(Minecraft mc)  { return (mc.level == null || mc.player == null) ? 0 : mc.font.width(text(mc)); }
    @Override public int height(Minecraft mc) { return (mc.level == null || mc.player == null) ? 0 : 9; }
}
