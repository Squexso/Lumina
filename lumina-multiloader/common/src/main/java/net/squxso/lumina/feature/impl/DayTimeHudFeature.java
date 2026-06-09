package net.squxso.lumina.feature.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.squxso.lumina.feature.HudFeature;

/** Shows the in-game day number and time of day (with a day/night hint). */
public final class DayTimeHudFeature extends HudFeature {

    private final Setting<Integer> color = add(Setting.color("color", "Text color", 0xFFB7AED0));

    public DayTimeHudFeature() { super("daytime_hud", "Day & Time", "Show the Minecraft day and time of day.", 6, 42); }

    private String text(Minecraft mc) {
        long dt = mc.level.getDayTime();
        long day = Math.floorDiv(dt, 24000L) + 1;
        int t = (int) Math.floorMod(dt, 24000L);
        int hours = (t / 1000 + 6) % 24;
        int minutes = (t % 1000) * 60 / 1000;
        String phase = (t >= 13000 && t < 23000) ? "night" : "day";
        return String.format("Day %d  %02d:%02d  (%s)", day, hours, minutes, phase);
    }

    @Override public void render(GuiGraphics ctx, Minecraft mc) {
        if (mc.level == null) return;
        ctx.drawString(mc.font, text(mc), 0, 0, color.asInt());
    }
    @Override public int width(Minecraft mc)  { return mc.level == null ? 0 : mc.font.width(text(mc)); }
    @Override public int height(Minecraft mc) { return mc.level == null ? 0 : 9; }
}
