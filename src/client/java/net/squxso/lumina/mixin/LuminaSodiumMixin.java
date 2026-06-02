package net.squxso.lumina.mixin;

import net.caffeinemc.mods.sodium.client.gui.SodiumOptionsGUI;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.squxso.lumina.gui.LuminaTheme;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Applies the Lumina dark-space background to Sodium's video-settings screen.
 * Loaded only when Sodium is present (lumina.sodium.mixins.json, required=false).
 * Width/height are read from the window to avoid @Shadow issues on inherited fields.
 */
@Mixin(SodiumOptionsGUI.class)
public abstract class LuminaSodiumMixin {

    @Inject(method = "render", at = @At("HEAD"))
    private void drawLuminaBg(DrawContext ctx, int mx, int my, float delta, CallbackInfo ci) {
        MinecraftClient mc = MinecraftClient.getInstance();
        int w = mc.getWindow().getScaledWidth();
        int h = mc.getWindow().getScaledHeight();
        long t = System.currentTimeMillis();

        ctx.fillGradient(0, 0, w, h, 0xFF160622, 0xFF070210);

        int sweep = (int) ((t / 40) % (w + 200)) - 100;
        ctx.fillGradient(sweep, 0, sweep + 120, h, 0x00C36BFF, 0x18C36BFF);

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
