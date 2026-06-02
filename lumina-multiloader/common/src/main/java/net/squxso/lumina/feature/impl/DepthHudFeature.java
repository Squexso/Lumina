package net.squxso.lumina.feature.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.squxso.lumina.feature.FeatureCategory;
import net.squxso.lumina.feature.HudFeature;

/**
 * Depth HUD: your current Y-level plus a hint of the best mining heights (common
 * knowledge — does NOT reveal any blocks). A fair mining aid.
 */
public final class DepthHudFeature extends HudFeature {

    private final Setting<Integer> color = add(Setting.color("color", "Text color", 0xFFB7AED0));

    public DepthHudFeature() {
        super("depth_hud", "Depth", "Your Y-level + the best mining heights.",
                FeatureCategory.MINING, 6, 138);
    }

    private static String tip(int y) {
        if (y >= -64 && y <= -48) return "prime diamond level";
        if (y < 0) return "deepslate ores";
        if (y >= 8 && y <= 24) return "iron / copper";
        return "";
    }

    private String text(Minecraft mc) {
        int y = (int) Math.floor(mc.player.getY());
        String t = tip(y);
        return "Y: " + y + (t.isEmpty() ? "" : " §7(" + t + ")");
    }

    @Override public void render(GuiGraphics ctx, Minecraft mc) {
        ctx.drawString(mc.font, text(mc), 0, 0, color.asInt());
    }
    @Override public int width(Minecraft mc) { return mc.font.width("Y: -000 (prime diamond level)"); }
    @Override public int height(Minecraft mc) { return 9; }
}
