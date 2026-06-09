package net.squxso.lumina.feature.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.level.LightLayer;
import net.squxso.lumina.feature.HudFeature;

/** Shows block/sky light at the player's position — useful for mob-proofing and building. */
public final class LightLevelHudFeature extends HudFeature {

    private final Setting<Integer> color = add(Setting.color("color", "Text color", 0xFFB7AED0));

    public LightLevelHudFeature() {
        super("light_hud", "Light Level", "Show block/sky light where you stand (mob-proofing).", 6, 66);
    }

    private String text(Minecraft mc) {
        var pos = mc.player.blockPosition();
        int block = mc.level.getBrightness(LightLayer.BLOCK, pos);
        int sky = mc.level.getBrightness(LightLayer.SKY, pos);
        return "Light: " + block + "  (sky " + sky + ")";
    }

    @Override public void render(GuiGraphics ctx, Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        int block = mc.level.getBrightness(LightLayer.BLOCK, mc.player.blockPosition());
        // Tint red when mobs can spawn (block light 0), else the configured colour.
        ctx.drawString(mc.font, text(mc), 0, 0, block == 0 ? 0xFFFF6B6B : color.asInt());
    }
    @Override public int width(Minecraft mc)  { return (mc.level == null || mc.player == null) ? 0 : mc.font.width(text(mc)); }
    @Override public int height(Minecraft mc) { return (mc.level == null || mc.player == null) ? 0 : 9; }
}
