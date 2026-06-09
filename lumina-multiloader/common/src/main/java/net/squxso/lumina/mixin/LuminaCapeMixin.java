package net.squxso.lumina.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.squxso.lumina.client.LuminaAccessories;
import net.squxso.lumina.client.LuminaCapeTextures;
import net.squxso.lumina.feature.FeatureManager;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Renders the Lumina Cape client-side on the local player.
 *
 * <p>Rather than registering a fragile custom render layer (the 1.21.11 player
 * render pipeline is the new {@code submit}/deferred system), this reuses
 * vanilla's own {@link CapeLayer}: it already bakes the cape model and computes
 * the cape physics ({@code capeFlap}/{@code capeLean}). We just hijack its
 * {@code submit} call to draw our own texture whenever the {@code lumina_cape}
 * cosmetic is active, so it works identically on Fabric, Forge and NeoForge.
 *
 * <p>This is client-side only: you see the cape on yourself (third person, F5,
 * the inventory model, etc.). Showing it to other players would require a server.
 */
@Mixin(CapeLayer.class)
public abstract class LuminaCapeMixin {

    @Shadow @Final private HumanoidModel<AvatarRenderState> model;

    // Both the cape AND the accessory render in ONE injector: the cape cancels the method,
    // so a separate accessory mixin at HEAD would be skipped by the cancel. Doing both here
    // (accessory first, independent of the cape) guarantees both draw.
    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void lumina$renderCosmetics(PoseStack pose, SubmitNodeCollector collector, int light,
                                        AvatarRenderState state, float yRot, float xRot, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (state.id != mc.player.getId()) return;        // local player only (client-side)
        if (state.isInvisible) return;

        // Accessory (wings / halo / aura) — independent of the cape, guarded so a render
        // hiccup never breaks the cape below.
        String acc = FeatureManager.accessoryType();
        if (acc != null) {
            try {
                LuminaAccessories.render(collector, pose, state, light, acc, FeatureManager.accessoryColor());
            } catch (Throwable ignored) {}
        }

        // Cape — replace vanilla's cape with ours when one is equipped.
        if (state.isFallFlying) return;                   // don't fight the elytra's wings
        if (!FeatureManager.isEnabled("lumina_cape")) return;
        pose.pushPose();
        collector.submitModel(this.model, state, pose,
                RenderTypes.entitySolid(LuminaCapeTextures.get()), light,
                OverlayTexture.NO_OVERLAY, state.outlineColor, null);
        pose.popPose();
        ci.cancel();
    }
}
