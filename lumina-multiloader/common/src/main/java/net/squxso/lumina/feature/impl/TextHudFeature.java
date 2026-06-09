package net.squxso.lumina.feature.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.squxso.lumina.feature.FeatureCategory;
import net.squxso.lumina.feature.HudFeature;

import java.util.function.Function;

/** A generic single-line text HUD driven by a supplier — lets us add info readouts cheaply. */
public final class TextHudFeature extends HudFeature {

    private final Setting<Integer> color = add(Setting.color("color", "Text color", 0xFFB7AED0));
    private final Function<Minecraft, String> textFn;

    public TextHudFeature(String id, String name, String desc, FeatureCategory cat, int x, int y,
                          Function<Minecraft, String> textFn) {
        super(id, name, desc, cat, x, y);
        this.textFn = textFn;
    }

    private String safe(Minecraft mc) {
        if (mc.player == null || mc.level == null) return "";
        try { String s = textFn.apply(mc); return s == null ? "" : s; } catch (Exception e) { return ""; }
    }

    @Override public void render(GuiGraphics ctx, Minecraft mc) {
        String s = safe(mc);
        if (!s.isEmpty()) ctx.drawString(mc.font, s, 0, 0, color.asInt());
    }
    @Override public int width(Minecraft mc)  { String s = safe(mc); return s.isEmpty() ? 0 : mc.font.width(s); }
    @Override public int height(Minecraft mc) { return safe(mc).isEmpty() ? 0 : 9; }
}
