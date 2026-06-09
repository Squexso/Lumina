package net.squxso.lumina.mixin;

import net.minecraft.client.renderer.LightTexture;
import net.minecraft.world.level.dimension.DimensionType;
import net.squxso.lumina.feature.impl.FullbrightFeature;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

/**
 * Fullbright: forces the per-light-level brightness curve to maximum while the feature is
 * on, so dark areas render fully lit. Brightness only — reveals nothing through walls.
 */
@Mixin(LightTexture.class)
public class LuminaLightMixin {

    @Inject(method = "getBrightness(FI)F", at = @At("RETURN"), cancellable = true)
    private static void lumina$fullbright(float ambient, int lightLevel, CallbackInfoReturnable<Float> cir) {
        if (FullbrightFeature.active()) cir.setReturnValue(1.0f);
    }

    @Inject(method = "getBrightness(Lnet/minecraft/world/level/dimension/DimensionType;I)F", at = @At("RETURN"), cancellable = true)
    private static void lumina$fullbrightDim(DimensionType dim, int lightLevel, CallbackInfoReturnable<Float> cir) {
        if (FullbrightFeature.active()) cir.setReturnValue(1.0f);
    }
}
