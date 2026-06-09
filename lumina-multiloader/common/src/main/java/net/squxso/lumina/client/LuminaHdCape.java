package net.squxso.lumina.client;

import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;

/**
 * A high-resolution replacement for Minecraft's tiny (64&nbsp;px) cape, so the Lumina
 * crystal renders big and sharp in-game.
 *
 * <p>Vanilla's cape is a {@code addBox(-5,0,-1, 10,16,1)} box on a 64&times;32 atlas,
 * so its back face is only ~10&times;16 texels — far too coarse for a clean logo. We
 * rebuild that <em>exact same box</em> but {@value #S}&times; larger (→ 80&times;128
 * texels for the back face) baked onto a 256&times;256 atlas, then render it scaled by
 * {@code 1/S}. The cape ends up in the identical position and follows the same physics
 * (the flap/lean is baked into the pose handed to {@code CapeLayer.submit}), but carries
 * an {@value #S}&times; sharper texture. The atlas layout produced by the launcher
 * ({@code GameCapeTexture}) mirrors this box's unwrap.
 */
public final class LuminaHdCape {

    /** Texel-density multiplier: box built S&times; big, rendered 1/S → same size, S&times; resolution. */
    public static final int S = 8;

    private static EntityModel<AvatarRenderState> model;

    private LuminaHdCape() {}

    public static EntityModel<AvatarRenderState> model() {
        if (model != null) return model;
        MeshDefinition md = new MeshDefinition();
        PartDefinition root = md.getRoot();
        root.addOrReplaceChild("cape",
                CubeListBuilder.create().texOffs(0, 0)
                        .addBox(-5f * S, 0f, -1f * S, 10f * S, 16f * S, 1f * S),
                PartPose.ZERO);
        ModelPart baked = LayerDefinition.create(md, 256, 256).bakeRoot();
        model = new EntityModel<AvatarRenderState>(baked, RenderTypes::entitySolid) {};
        return model;
    }
}
