package net.squxso.lumina.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.ingame.HandledScreen;
import net.squxso.lumina.gui.LuminaTheme;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Replaces the vanilla background (blurred world or dirt texture) with the
 * Lumina dark-space theme for every screen that does <em>not</em> already
 * define its own {@code renderBackground()} override.
 *
 * <p>Screens with their own override are not affected at runtime — Java's
 * dynamic dispatch calls their override directly, never reaching this code:
 * {@code LuminaTitleScreen}, {@code LuminaMultiplayerScreen},
 * {@code LuminaGameMenuScreen}, {@code LuminaOptionsScreen}.
 *
 * <p>Inventory / container screens ({@link HandledScreen}) are excluded so
 * that chests, crafting tables, the player's inventory, etc. keep the normal
 * Minecraft look.
 *
 * <p>{@code require = 0}: if the method signature changes between MC builds
 * the mixin silently skips rather than crashing the game.
 */
@Mixin(Screen.class)
public abstract class LuminaScreenBgMixin {

    @Shadow public int width;
    @Shadow public int height;

    @Inject(
            method    = "renderBackground",
            at        = @At("HEAD"),
            cancellable = true,
            require   = 0
    )
    private void applyLuminaBackground(DrawContext ctx, int mx, int my, float delta,
                                        CallbackInfo ci) {
        if (!net.squxso.lumina.feature.FeatureManager.isEnabled("ui_theme")) return; // plain vanilla
        // Keep vanilla for inventory / crafting / chest screens.
        // Cast to Object first — required in mixin context so the compiler
        // knows we're checking the runtime type of the injected target.
        if ((Object) this instanceof HandledScreen<?>) return;

        int w = this.width, h = this.height;
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

        // Accent rails — top and bottom.
        ctx.fillGradient(0, 0,     w, 3,     LuminaTheme.ACCENT,      LuminaTheme.ACCENT2);
        ctx.fillGradient(0, h - 3, w, h,     LuminaTheme.ACCENT_DEEP, LuminaTheme.ACCENT_DIM);

        ci.cancel(); // skip vanilla dirt / blur
    }
}
