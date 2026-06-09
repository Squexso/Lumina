package net.squxso.lumina.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.squxso.lumina.client.LuminaCapeTextures;
import net.squxso.lumina.client.LuminaHdCape;
import net.squxso.lumina.feature.FeatureManager;
import org.spongepowered.asm.mixin.Mixin;
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

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void lumina$renderCape(PoseStack pose, SubmitNodeCollector collector, int light,
                                   AvatarRenderState state, float yRot, float xRot, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (state.id != mc.player.getId()) return;        // local player only (client-side)
        if (state.isInvisible) return;
        if (state.isFallFlying) return;                   // don't fight the elytra's wings
        if (!FeatureManager.isEnabled("lumina_cape")) return;
        // Draw our own HD cape instead of vanilla's. Vanilla's cape lives entirely in its
        // model: PlayerCapeModel places the cape cube as a child of "body" at PartPose
        // offset(0,0,2) and animates its rotation in setupAnim. We replicate both on the
        // pose, then scale the 8×-built HD box back to 1× — so it lands exactly where vanilla's
        // cape would (and swings the same way when you walk/turn), just far sharper.
        //
        // Net rotation = the PartPose's Ry(180°) composed with setupAnim's quaternion, which
        // simplifies to Rx(flap)·Rz(lean)·Ry(180°−lean): capeFlap drives the walk swing,
        // capeLean/capeLean2 the side lean.
        final float DEG = 0.017453292f;
        org.joml.Quaternionf capeRot = new org.joml.Quaternionf()
                .rotateX((6.0f + state.capeLean / 2.0f + state.capeFlap) * DEG)
                .rotateZ((state.capeLean2 / 2.0f) * DEG)
                .rotateY((180.0f - state.capeLean2 / 2.0f) * DEG);
        pose.pushPose();
        pose.translate(0.0f, 0.0f, 2.0f / 16.0f);         // cape part offset (ModelPart divides offsets by 16)
        pose.mulPose(capeRot);                            // animated cape rotation (flap + lean)
        float s = 1f / LuminaHdCape.S;
        pose.scale(s, s, s);
        collector.submitModel(LuminaHdCape.model(), state, pose,
                RenderTypes.entitySolid(LuminaCapeTextures.get()), light,
                OverlayTexture.NO_OVERLAY, state.outlineColor, null);
        pose.popPose();
        ci.cancel();
    }
}
