package net.squxso.lumina.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.SplashOverlay;
import net.minecraft.text.Text;
import net.squxso.lumina.gui.LuminaTheme;
import org.joml.Matrix3x2fStack;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Rebrands Minecraft's red Mojang loading screen with the Lumina look:
 * a cosmic-violet background, the LUMINA MC wordmark, and a violet progress bar
 * driven by the real resource-reload progress.
 *
 * <p>Painted on top of the vanilla overlay at every {@code render} return — the
 * original control flow (fade timing, completion, hand-off to the title screen)
 * is left untouched, so loading still finishes exactly as before.
 */
@Mixin(SplashOverlay.class)
public abstract class LuminaSplashMixin {

    @Shadow private float progress;

    @Inject(method = "render", at = @At("RETURN"))
    private void lumina$brandSplash(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        int w = ctx.getScaledWindowWidth();
        int h = ctx.getScaledWindowHeight();
        int cy = h / 2;
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        // Cosmic background — fully covers the vanilla red + Mojang logo.
        ctx.fillGradient(0, 0, w, h, 0xFF20103E, 0xFF0B0617);
        // Subtle accent hairlines framing the centre.
        ctx.fill(0, cy - 42, w, cy - 41, 0x33C36BFF);
        ctx.fill(0, cy + 36, w, cy + 37, 0x18C36BFF);

        // Big "LUMINA MC" wordmark (matrix-scaled text).
        Matrix3x2fStack m = ctx.getMatrices();
        m.pushMatrix();
        m.translate(w / 2f, cy - 28f);
        float s = 4.0f;
        m.scale(s, s);
        String a = "LUMINA", b = "MC";
        int wa = tr.getWidth(a), gap = 4, wb = tr.getWidth(b);
        int total = wa + gap + wb;
        ctx.drawTextWithShadow(tr, Text.literal(a), -total / 2, 0, 0xFFE3A8FF);
        ctx.drawTextWithShadow(tr, Text.literal(b), -total / 2 + wa + gap, 0, 0xFF8B5CF6);
        m.popMatrix();

        // Progress bar driven by the real reload progress.
        int bw = Math.min(360, w - 80);
        int bx = (w - bw) / 2;
        int by = cy + 42;
        int bh = 6;
        float p = Math.max(0f, Math.min(1f, this.progress));
        int fillW = Math.round(bw * p);
        ctx.fill(bx - 1, by - 1, bx + bw + 1, by + bh + 1, 0xFF2A1556);              // border
        ctx.fill(bx, by, bx + bw, by + bh, 0xFF120722);                              // track
        if (fillW > 0) {
            ctx.fill(bx, by, bx + fillW, by + bh, LuminaTheme.ACCENT);               // fill
            ctx.fill(bx, by, bx + fillW, by + 1, LuminaTheme.ACCENT_HOT);            // top sheen
        }

        // Percentage under the bar.
        String pct = Math.round(p * 100) + "%";
        ctx.drawCenteredTextWithShadow(tr, Text.literal(pct), w / 2, by + 12, 0xFF9A90B5);
    }
}
