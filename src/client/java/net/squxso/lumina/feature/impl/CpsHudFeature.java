package net.squxso.lumina.feature.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.squxso.lumina.feature.HudFeature;

import java.util.ArrayDeque;
import java.util.Deque;

/** Clicks-per-second counter (left-click), movable/scalable. */
public final class CpsHudFeature extends HudFeature {

    private final Setting<Integer> color = add(Setting.color("color", "Text color", 0xFFB7AED0));
    private final Deque<Long> clicks = new ArrayDeque<>();
    private boolean wasPressed = false;

    public CpsHudFeature() { super("cps_hud", "CPS Counter", "Show your left-clicks per second.", 6, 18); }

    @Override public void onClientTick(MinecraftClient mc) {
        boolean now = mc.options.attackKey.isPressed();
        if (now && !wasPressed) clicks.addLast(System.currentTimeMillis());
        wasPressed = now;
    }

    private int cps() {
        long cutoff = System.currentTimeMillis() - 1000;
        while (!clicks.isEmpty() && clicks.peekFirst() < cutoff) clicks.pollFirst();
        return clicks.size();
    }

    @Override public void render(DrawContext ctx, MinecraftClient mc) {
        ctx.drawTextWithShadow(mc.textRenderer, "CPS: " + cps(), 0, 0, color.asInt());
    }
    @Override public int width(MinecraftClient mc) { return mc.textRenderer.getWidth("CPS: 00"); }
    @Override public int height(MinecraftClient mc) { return 9; }
}
