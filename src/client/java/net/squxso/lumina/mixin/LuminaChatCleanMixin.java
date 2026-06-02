package net.squxso.lumina.mixin;

import com.mojang.blaze3d.pipeline.RenderPipeline;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.ChatHud;
import net.squxso.lumina.feature.FeatureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

/**
 * Clean chat: while "Chat Tweaks" is on, the grey background boxes behind chat
 * lines are skipped for a minimalist, transparent chat.
 *
 * <p>1.21.10 draws fills via two overloads ({@code fill(IIIII)} and the newer
 * {@code fill(RenderPipeline, IIIII)}), so both are redirected — whichever the
 * chat actually uses gets neutralised. {@code require = 0} keeps it crash-safe.
 */
@Mixin(ChatHud.class)
public abstract class LuminaChatCleanMixin {

    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(IIIII)V"),
            require = 0
    )
    private void lumina$noBgClassic(DrawContext ctx, int x1, int y1, int x2, int y2, int color) {
        if (FeatureManager.isEnabled("chat")) return;
        ctx.fill(x1, y1, x2, y2, color);
    }

    @Redirect(
            method = "render",
            at = @At(value = "INVOKE", target = "Lnet/minecraft/client/gui/DrawContext;fill(Lcom/mojang/blaze3d/pipeline/RenderPipeline;IIIII)V"),
            require = 0
    )
    private void lumina$noBgPipeline(DrawContext ctx, RenderPipeline pipeline, int x1, int y1, int x2, int y2, int color) {
        if (FeatureManager.isEnabled("chat")) return;
        ctx.fill(pipeline, x1, y1, x2, y2, color);
    }
}
