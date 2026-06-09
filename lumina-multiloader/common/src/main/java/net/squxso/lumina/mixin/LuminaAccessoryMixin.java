package net.squxso.lumina.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.squxso.lumina.client.LuminaAccessories;
import net.squxso.lumina.feature.FeatureManager;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders the equipped accessory (wings / halo / aura) on the local player client-side.
 *
 * <p>Like {@link LuminaCapeMixin} this piggy-backs on {@link CapeLayer}'s {@code submit}
 * (which runs every frame for the player) as a reliable, loader-agnostic hook into the
 * 1.21.11 deferred-render pipeline — but it does NOT cancel, so the cape still renders too.
 */
@Mixin(CapeLayer.class)
public abstract class LuminaAccessoryMixin {

    @Inject(method = "submit", at = @At("HEAD"))
    private void lumina$renderAccessory(PoseStack pose, SubmitNodeCollector collector, int light,
                                        AvatarRenderState state, float yRot, float xRot, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (state.id != mc.player.getId()) return;     // local player only (client-side)
        if (state.isInvisible) return;

        String type = FeatureManager.accessoryType();
        if (type == null) return;

        LuminaAccessories.render(collector, pose, state, light, type, FeatureManager.accessoryColor());
    }
}
