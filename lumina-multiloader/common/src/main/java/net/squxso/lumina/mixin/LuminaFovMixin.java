package net.squxso.lumina.mixin;

import net.minecraft.client.Camera;
import net.minecraft.client.renderer.GameRenderer;
import net.squxso.lumina.feature.impl.ZoomFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/** Scales the rendered field-of-view when {@link ZoomFeature} is active (hold C). */
@Mixin(GameRenderer.class)
public class LuminaFovMixin {

    @Inject(method = "getFov", at = @At("RETURN"), cancellable = true)
    private void lumina$zoom(Camera camera, float partialTick, boolean useFov, CallbackInfoReturnable<Float> cir) {
        float m = ZoomFeature.multiplier();
        if (m != 1f) cir.setReturnValue(cir.getReturnValueF() * m);
    }
}
