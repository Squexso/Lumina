package net.squxso.lumina.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.multiplayer.MultiplayerScreen;

/**
 * Multiplayer server-list screen with the Lumina dark background instead of
 * the vanilla blurred panorama. All server-list functionality is unchanged.
 */
public class LuminaMultiplayerScreen extends MultiplayerScreen {

    public LuminaMultiplayerScreen(Screen parent) {
        super(parent);
    }

    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        int w = this.width, h = this.height;
        long t = System.currentTimeMillis();

        ctx.fillGradient(0, 0, w, h, 0xFF160622, 0xFF070210);

        // Slow diagonal sweep.
        int sweep = (int) ((t / 40) % (w + 200)) - 100;
        ctx.fillGradient(sweep, 0, sweep + 120, h, 0x00C36BFF, 0x18C36BFF);

        // Twinkling stars.
        for (int i = 0; i < 22; i++) {
            int dx = (int) ((i * 79 + t / 60) % w);
            int dy = (i * 131) % h;
            int tw = (int) ((Math.sin(t / 500.0 + i) + 1) * 55);
            ctx.fill(dx, dy, dx + 2, dy + 2, ((tw & 0xFF) << 24) | 0xC36BFF);
        }

        ctx.fillGradient(0, 0,     w, 3,     LuminaTheme.ACCENT,      LuminaTheme.ACCENT2);
        ctx.fillGradient(0, h - 3, w, h,     LuminaTheme.ACCENT_DEEP, LuminaTheme.ACCENT_DIM);
    }
}
