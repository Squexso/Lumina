package net.squxso.lumina.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.GameMenuScreen;
import net.squxso.lumina.gui.LuminaTheme;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Gives the vanilla pause / game-menu screen the Lumina dark-space background
 * without replacing the screen (which caused width=0 / missing-buttons issues
 * when done via {@code ScreenEvents.AFTER_INIT}).
 *
 * <p>All vanilla buttons (Back to Game, Options, Disconnect, etc.) are
 * untouched — only {@code renderBackground()} is overridden.
 *
 * <p>{@code require = 0}: silently skips if the method signature changes.
 * <p>Width/height are read from the window instead of @Shadow to avoid
 * "field not in target class" errors for inherited fields.
 */
@Mixin(GameMenuScreen.class)
public abstract class LuminaGameMenuBgMixin {

    @Inject(
            method    = "renderBackground",
            at        = @At("HEAD"),
            cancellable = true,
            require   = 0
    )
    private void applyLuminaBackground(DrawContext ctx, int mx, int my, float delta,
                                        CallbackInfo ci) {
        if (!net.squxso.lumina.feature.FeatureManager.isEnabled("ui_theme")) return; // plain vanilla
        MinecraftClient mc = MinecraftClient.getInstance();
        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        long t = System.currentTimeMillis();

        // Deep-space base gradient.
        ctx.fillGradient(0, 0, w, h, 0xFF160622, 0xFF070210);

        // Slow diagonal light-sweep.
        int sweep = (int) ((t / 40) % (w + 200)) - 100;
        ctx.fillGradient(sweep, 0, sweep + 120, h, 0x00C36BFF, 0x18C36BFF);

        // Twinkling star particles.
        for (int i = 0; i < 22; i++) {
            int dx = (int) ((i * 79 + t / 60) % w);
            int dy = (i * 131) % h;
            int tw = (int) ((Math.sin(t / 500.0 + i) + 1) * 55);
            ctx.fill(dx, dy, dx + 2, dy + 2, ((tw & 0xFF) << 24) | 0xC36BFF);
        }

        // Accent rails.
        ctx.fillGradient(0, 0,     w, 3,     LuminaTheme.ACCENT,      LuminaTheme.ACCENT2);
        ctx.fillGradient(0, h - 3, w, h,     LuminaTheme.ACCENT_DEEP, LuminaTheme.ACCENT_DIM);

        ci.cancel();
    }
}
