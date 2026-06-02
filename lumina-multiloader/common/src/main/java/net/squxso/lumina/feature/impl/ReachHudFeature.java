package net.squxso.lumina.feature.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.phys.EntityHitResult;
import net.squxso.lumina.client.LuminaTheme;
import net.squxso.lumina.feature.FeatureCategory;
import net.squxso.lumina.feature.HudFeature;

/** Shows the distance to the entity you're aiming at, on a readable card. */
public final class ReachHudFeature extends HudFeature {

    private final Setting<Integer> color = add(Setting.color("color", "Text color", 0xFFE9D5FF));

    public ReachHudFeature() {
        super("reach_hud", "Reach", "Show the distance to the entity you're aiming at.",
                FeatureCategory.COMBAT, 6, 102);
    }

    private String text(Minecraft mc) {
        if (mc.player != null && mc.hitResult instanceof EntityHitResult e)
            return String.format("Reach: %.2fm", mc.player.distanceTo(e.getEntity()));
        return null;
    }

    @Override public void render(GuiGraphics ctx, Minecraft mc) {
        String s = text(mc);
        if (s == null) return;
        int w = mc.font.width(s) + 16, h = 14;
        LuminaTheme.card(ctx, 0, 0, w, h, 5);
        ctx.drawString(mc.font, s, 8, 3, color.asInt());
    }
    @Override public int width(Minecraft mc) { String s = text(mc); return s == null ? 0 : mc.font.width(s) + 16; }
    @Override public int height(Minecraft mc) { return text(mc) == null ? 0 : 14; }
}
