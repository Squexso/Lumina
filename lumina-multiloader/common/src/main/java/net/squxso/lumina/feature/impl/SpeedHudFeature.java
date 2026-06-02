package net.squxso.lumina.feature.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.squxso.lumina.feature.FeatureCategory;
import net.squxso.lumina.feature.HudFeature;

/** Shows your horizontal movement speed in blocks/second. Informational only. */
public final class SpeedHudFeature extends HudFeature {

    private final Setting<Integer> color = add(Setting.color("color", "Text color", 0xFFB7AED0));

    private double lastX, lastZ, bps;
    private boolean primed;

    public SpeedHudFeature() {
        super("speed_hud", "Speed", "Show your horizontal speed in blocks/second.",
                FeatureCategory.MOVEMENT, 6, 124);
    }

    @Override
    public void onClientTick(Minecraft mc) {
        if (mc.player == null) return;
        double x = mc.player.getX(), z = mc.player.getZ();
        if (primed) {
            double dx = x - lastX, dz = z - lastZ;
            bps = Math.sqrt(dx * dx + dz * dz) * 20.0; // 20 ticks per second
        }
        lastX = x; lastZ = z; primed = true;
    }

    @Override public void render(GuiGraphics ctx, Minecraft mc) {
        ctx.drawString(mc.font, String.format("Speed: %.2f b/s", bps), 0, 0, color.asInt());
    }
    @Override public int width(Minecraft mc) { return mc.font.width("Speed: 00.00 b/s"); }
    @Override public int height(Minecraft mc) { return 9; }
}
