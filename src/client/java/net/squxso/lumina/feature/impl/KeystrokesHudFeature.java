package net.squxso.lumina.feature.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.text.Text;
import net.squxso.lumina.feature.HudFeature;

/** Compact WASD + Space keystroke overlay; keys light up when pressed. Movable/scalable. */
public final class KeystrokesHudFeature extends HudFeature {

    private final Setting<Integer> accent = add(Setting.color("accent", "Pressed color", 0xFF8B5CF6));
    private static final int K = 16, GAP = 2;

    public KeystrokesHudFeature() { super("keystrokes_hud", "Keystrokes", "Show pressed movement keys (WASD + Space).", 6, 80); }

    @Override public void render(DrawContext ctx, MinecraftClient mc) {
        var o = mc.options;
        key(ctx, mc, K + GAP, 0, "W", o.forwardKey);
        key(ctx, mc, 0,             K + GAP, "A", o.leftKey);
        key(ctx, mc, K + GAP,       K + GAP, "S", o.backKey);
        key(ctx, mc, (K + GAP) * 2, K + GAP, "D", o.rightKey);
        int sy = (K + GAP) * 2, sw = K * 3 + GAP * 2;
        boolean sp = o.jumpKey.isPressed();
        ctx.fill(0, sy, sw, sy + 7, sp ? accent.asInt() : 0xAA101018);
        ctx.fill(0, sy, sw, sy + 1, 0x55C36BFF);
    }

    private void key(DrawContext ctx, MinecraftClient mc, int kx, int ky, String label, KeyBinding kb) {
        boolean p = kb.isPressed();
        ctx.fill(kx, ky, kx + K, ky + K, p ? accent.asInt() : 0xAA101018);
        ctx.fill(kx, ky, kx + K, ky + 1, 0x55C36BFF);
        ctx.drawCenteredTextWithShadow(mc.textRenderer, Text.literal(label), kx + K / 2, ky + 4, p ? 0xFFFFFFFF : 0xFFB7AED0);
    }

    @Override public int width(MinecraftClient mc)  { return K * 3 + GAP * 2; }
    @Override public int height(MinecraftClient mc) { return (K + GAP) * 2 + 7; }
}
