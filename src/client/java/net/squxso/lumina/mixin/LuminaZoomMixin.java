package net.squxso.lumina.mixin;

import net.minecraft.client.render.Camera;
import net.minecraft.client.render.GameRenderer;
import net.squxso.lumina.feature.impl.ZoomFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Applies the LuminaMC zoom by dividing the camera FOV while the zoom key is
 * held (state is driven by {@link ZoomFeature}). {@code require = 0} keeps it
 * crash-safe if the FOV method signature changes.
 */
@Mixin(GameRenderer.class)
public abstract class LuminaZoomMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true, require = 0)
    private void lumina$zoom(Camera camera, float tickDelta, boolean changingFov,
                             CallbackInfoReturnable<Float> cir) {
        if (ZoomFeature.active) {
            int f = Math.max(2, ZoomFeature.factor());
            cir.setReturnValue(cir.getReturnValueF() / (float) f);
        }
    }
}
