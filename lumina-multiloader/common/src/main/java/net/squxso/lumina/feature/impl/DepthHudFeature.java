package net.squxso.lumina.feature.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.Level;
import net.squxso.lumina.feature.FeatureCategory;
import net.squxso.lumina.feature.HudFeature;

/**
 * Depth HUD: your current Y-level plus a hint of the best mining heights for the
 * dimension you're in (common knowledge — does NOT reveal any blocks). A fair aid.
 */
public final class DepthHudFeature extends HudFeature {

    private final Setting<Integer> color = add(Setting.color("color", "Text color", 0xFFB7AED0));

    public DepthHudFeature() {
        super("depth_hud", "Depth", "Your Y-level + the best heights for every ore (Overworld & Nether).",
                FeatureCategory.MINING, 6, 138);
    }

    private String tip(Minecraft mc, int y) {
        if (mc.level != null && Level.NETHER.equals(mc.level.dimension())) return netherTip(y);
        if (mc.level != null && Level.END.equals(mc.level.dimension())) return "";
        return overworldTip(y);
    }

    /** Best ore heights per band (peaks: diamond/redstone ≈ -59, gold ≈ -16, lapis ≈ 0,
     *  iron ≈ 16, copper ≈ 48, coal ≈ 96, iron/emerald ≈ 232 in mountains). */
    private static String overworldTip(int y) {
        if (y <= -50) return "diamond · redstone · gold · lapis";
        if (y <= -16) return "gold · redstone · lapis · diamond";
        if (y <= 15)  return "lapis · iron · gold · copper";
        if (y <= 47)  return "iron · copper · coal";
        if (y <= 79)  return "copper · coal";
        if (y <= 191) return "coal · iron (mtn)";
        return "emerald · iron · coal (mtn)";   // 192+, mountain biomes
    }

    private static String netherTip(int y) {
        if (y >= 8 && y <= 22) return "ancient debris ·Y15 · quartz · gold";
        if (y >= 118) return "near ceiling";
        return "quartz · gold";
    }

    private String text(Minecraft mc) {
        int y = (int) Math.floor(mc.player.getY());
        String t = tip(mc, y);
        return "Y: " + y + (t.isEmpty() ? "" : " §7(" + t + ")");
    }

    @Override public void render(GuiGraphics ctx, Minecraft mc) {
        ctx.drawString(mc.font, text(mc), 0, 0, color.asInt());
    }
    @Override public int width(Minecraft mc)  { return mc.font.width("Y: -000 (ancient debris ·Y15 · quartz · gold)"); }
    @Override public int height(Minecraft mc) { return 9; }
}
