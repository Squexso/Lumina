package net.squxso.lumina.mixin;

import com.mojang.blaze3d.vertex.PoseStack;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.HumanoidModel;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.layers.CapeLayer;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;
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

    private static final Identifier LUMINA$CAPE =
            Identifier.fromNamespaceAndPath("lumina", "textures/entity/lumina_cape.png");

    @Inject(method = "submit", at = @At("HEAD"), cancellable = true)
    private void lumina$renderCape(PoseStack pose, SubmitNodeCollector collector, int light,
                                   AvatarRenderState state, float yRot, float xRot, CallbackInfo ci) {
        Minecraft mc = Minecraft.getInstance();
        if (mc.player == null) return;
        if (state.id != mc.player.getId()) return;        // local player only (client-side)
        if (!FeatureManager.isEnabled("lumina_cape")) return;
        if (state.isInvisible) return;
        if (state.isFallFlying) return;                   // don't fight the elytra's wings

        pose.pushPose();
        collector.submitModel(this.model, state, pose,
                RenderTypes.entitySolid(LUMINA$CAPE), light,
                OverlayTexture.NO_OVERLAY, state.outlineColor, null);
        pose.popPose();
        ci.cancel();   // replace vanilla's cape (Mojang cape or none) with ours
    }
}
