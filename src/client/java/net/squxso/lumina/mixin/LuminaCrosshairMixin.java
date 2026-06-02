package net.squxso.lumina.mixin;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.hud.InGameHud;
import net.minecraft.client.render.RenderTickCounter;
import net.squxso.lumina.feature.FeatureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Hides the vanilla crosshair while LuminaMC's custom crosshair is enabled, so
 * the two never overlap. The custom crosshair is drawn separately by
 * {@code CrosshairFeature} through the HUD render callback.
 *
 * <p>{@code require = 0}: silently does nothing if the method signature changes.
 */
@Mixin(InGameHud.class)
public abstract class LuminaCrosshairMixin {

    @Inject(method = "renderCrosshair", at = @At("HEAD"), cancellable = true, require = 0)
    private void lumina$hideVanillaCrosshair(DrawContext context, RenderTickCounter tickCounter, CallbackInfo ci) {
        if (FeatureManager.isEnabled("crosshair")) {
            ci.cancel();
        }
    }
}
