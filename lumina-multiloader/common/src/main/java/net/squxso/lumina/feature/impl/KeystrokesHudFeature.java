package net.squxso.lumina.feature.impl;

import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.squxso.lumina.feature.HudFeature;

/** Compact WASD + Space keystroke overlay; keys light up when pressed. */
public final class KeystrokesHudFeature extends HudFeature {

    private final Setting<Integer> accent = add(Setting.color("accent", "Pressed color", 0xFF8B5CF6));
    private static final int K = 16, GAP = 2;

    public KeystrokesHudFeature() { super("keystrokes_hud", "Keystrokes", "Show pressed movement keys (WASD + Space).", 6, 80); }

    @Override public void render(GuiGraphics ctx, Minecraft mc) {
        var o = mc.options;
        key(ctx, mc, K + GAP, 0, "W", o.keyUp);
        key(ctx, mc, 0,             K + GAP, "A", o.keyLeft);
        key(ctx, mc, K + GAP,       K + GAP, "S", o.keyDown);
        key(ctx, mc, (K + GAP) * 2, K + GAP, "D", o.keyRight);
        int sy = (K + GAP) * 2, sw = K * 3 + GAP * 2;
        boolean sp = o.keyJump.isDown();
        ctx.fill(0, sy, sw, sy + 7, sp ? accent.asInt() : 0xAA101018);
        ctx.fill(0, sy, sw, sy + 1, 0x55C36BFF);
    }

    private void key(GuiGraphics ctx, Minecraft mc, int kx, int ky, String label, KeyMapping kb) {
        boolean p = kb.isDown();
        ctx.fill(kx, ky, kx + K, ky + K, p ? accent.asInt() : 0xAA101018);
        ctx.fill(kx, ky, kx + K, ky + 1, 0x55C36BFF);
        ctx.drawCenteredString(mc.font, label, kx + K / 2, ky + 4, p ? 0xFFFFFFFF : 0xFFB7AED0);
    }

    @Override public int width(Minecraft mc)  { return K * 3 + GAP * 2; }
    @Override public int height(Minecraft mc) { return (K + GAP) * 2 + 7; }
}
