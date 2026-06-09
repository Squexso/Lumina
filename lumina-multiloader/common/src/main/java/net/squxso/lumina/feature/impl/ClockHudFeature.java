package net.squxso.lumina.feature.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.squxso.lumina.feature.HudFeature;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** Shows the real-world clock — handy so you notice how late it's gotten. */
public final class ClockHudFeature extends HudFeature {

    private static final DateTimeFormatter FMT = DateTimeFormatter.ofPattern("HH:mm");
    private final Setting<Integer> color = add(Setting.color("color", "Text color", 0xFFB7AED0));

    public ClockHudFeature() { super("clock_hud", "Clock", "Show the real-world time.", 6, 30); }

    private String text() { return "Time: " + LocalTime.now().format(FMT); }

    @Override public void render(GuiGraphics ctx, Minecraft mc) { ctx.drawString(mc.font, text(), 0, 0, color.asInt()); }
    @Override public int width(Minecraft mc) { return mc.font.width(text()); }
    @Override public int height(Minecraft mc) { return 9; }
}
