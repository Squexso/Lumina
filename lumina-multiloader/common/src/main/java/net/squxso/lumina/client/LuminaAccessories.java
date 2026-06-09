package net.squxso.lumina.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.EntityModel;
import net.minecraft.client.model.geom.ModelPart;
import net.minecraft.client.model.geom.PartPose;
import net.minecraft.client.model.geom.builders.CubeListBuilder;
import net.minecraft.client.model.geom.builders.LayerDefinition;
import net.minecraft.client.model.geom.builders.MeshDefinition;
import net.minecraft.client.model.geom.builders.PartDefinition;
import net.minecraft.client.renderer.SubmitNodeCollector;
import net.minecraft.client.renderer.entity.state.AvatarRenderState;
import net.minecraft.client.renderer.rendertype.RenderTypes;
import net.minecraft.client.renderer.texture.DynamicTexture;
import net.minecraft.client.renderer.texture.OverlayTexture;
import net.minecraft.resources.Identifier;

/**
 * Renders the equipped accessory (wings / halo / aura) on the local player in-game,
 * as voxel geometry coloured by the accessory's tint. The geometry is baked once; the
 * colour is a tiny solid-colour dynamic texture (so the cubes pick up Minecraft's own
 * face lighting and read as 3D). Aura slowly orbits.
 *
 * <p>All vanilla classes, so the same code drives Fabric / Forge / NeoForge.
 */
public final class LuminaAccessories {

    private LuminaAccessories() {}

    private static final int FULL_BRIGHT = 0xF000F0;

    private static EntityModel<AvatarRenderState> wings, halo, aura;
    private static int colourArgb;
    private static Identifier colourTex;

    public static void render(SubmitNodeCollector collector, PoseStack pose, AvatarRenderState state,
                              int light, String type, int argb) {
        EntityModel<AvatarRenderState> model = switch (type) {
            case "wings" -> wings();
            case "halo"  -> halo();
            case "aura"  -> aura();
            default      -> null;
        };
        if (model == null) return;

        pose.pushPose();
        if ("aura".equals(type)) {
            float angle = (float) ((System.currentTimeMillis() / 24L) % 360L);
            pose.translate(0, 1.0, 0);
            pose.mulPose(Axis.YP.rotationDegrees(angle));
            pose.translate(0, -1.0, 0);
        }
        // Full-bright so the cosmetic glows uniformly (no per-cube world shading).
        collector.submitModel(model, state, pose, RenderTypes.entitySolid(colourTex(argb)),
                FULL_BRIGHT, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
        pose.popPose();
    }

    // ── colour texture ───────────────────────────────────────────────────────

    private static Identifier colourTex(int argb) {
        if (colourTex != null && colourArgb == argb) return colourTex;
        NativeImage img = new NativeImage(8, 8, false);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) img.setPixel(x, y, argb);  // setPixel takes ARGB
        Identifier id = Identifier.fromNamespaceAndPath("lumina", "textures/entity/accessory_color");
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "lumina_accessory", img));
        colourArgb = argb;
        colourTex = id;
        return id;
    }




    // ── geometry (built in player model space: up = -Y, back = +Z, 16 units = 1 block) ──

    // A static model typed to AvatarRenderState so submitModel's setupAnim(state) won't
    // ClassCastException (Model.Simple is Model<Unit>). setupAnim just resets the pose.
    private static EntityModel<AvatarRenderState> wrap(ModelPart root) {
        return new EntityModel<AvatarRenderState>(root, RenderTypes::entitySolid) {};
    }

    private static EntityModel<AvatarRenderState> wings() {
        if (wings != null) return wings;
        MeshDefinition md = new MeshDefinition();
        CubeListBuilder c = CubeListBuilder.create().texOffs(0, 0);
        for (int side = -1; side <= 1; side += 2) {
            int s = side;
            int primaries = 8;
            for (int f = 0; f < primaries; f++) {
                double v = f / (primaries - 1.0);
                double rx = s * (1.0 + v * 11.0);                 // out from the spine
                double ry = 1.0 - bell(v, 0.7) * 9.0;             // up (-Y) toward the wrist
                double rz = 2.6 + v * 2.0;                        // behind the back
                int len = (int) Math.round(4 + bell(v, 0.74) * 7);
                for (int r = 0; r < len; r++) {
                    float cx = (float) (rx + s * -0.5 * r);        // grain drifts toward centre as it falls
                    float cy = (float) (ry + 0.9 * r);            // hangs down (+Y)
                    float cz = (float) (rz - 0.08 * r);
                    c.addBox(cx - 0.5f, cy - 0.5f, cz - 0.5f, 1.0f, 1.0f, 1.0f);
                }
            }
        }
        md.getRoot().addOrReplaceChild("wings", c, PartPose.ZERO);
        return wings = wrap(LayerDefinition.create(md, 64, 64).bakeRoot());
    }

    /** A halo torus (donut), built from small cubes around the ring + tube — the cleanest
     *  always-visible 3D ring MC can do (glows full-bright). A flat smooth ring would be
     *  cleaner but does not render reliably in the deferred entity pipeline. */
    private static EntityModel<AvatarRenderState> halo() {
        if (halo != null) return halo;
        MeshDefinition md = new MeshDefinition();
        CubeListBuilder c = CubeListBuilder.create().texOffs(0, 0);
        int major = 64, minor = 4;
        double R = 4.3, r = 0.42, sz = 0.3, baseY = -15;
        for (int i = 0; i < major; i++) {
            double th = i / (double) major * Math.PI * 2;
            double ct = Math.cos(th), st = Math.sin(th);
            for (int j = 0; j < minor; j++) {
                double ph = j / (double) minor * Math.PI * 2;
                double rr = R + r * Math.cos(ph);
                float cx = (float) (ct * rr);
                float cz = (float) (st * rr);
                float cy = (float) (baseY + r * Math.sin(ph));
                c.addBox(cx - (float) (sz / 2), cy - (float) (sz / 2), cz - (float) (sz / 2),
                        (float) sz, (float) sz, (float) sz);
            }
        }
        md.getRoot().addOrReplaceChild("halo", c, PartPose.ZERO);
        return halo = wrap(LayerDefinition.create(md, 64, 64).bakeRoot());
    }

    private static EntityModel<AvatarRenderState> aura() {
        if (aura != null) return aura;
        MeshDefinition md = new MeshDefinition();
        CubeListBuilder c = CubeListBuilder.create().texOffs(0, 0);
        double[][] stars = {{0, -10, 6}, {6, -4, 4}, {-6, -2, 5}, {5, 4, 5},
                {-5, 6, 4}, {0, 8, 6}, {7, 0, 3}, {-7, -6, 4}};
        for (double[] p : stars) {
            c.addBox((float) p[0] - 0.7f, (float) p[1] - 0.7f, (float) p[2] - 0.7f, 1.4f, 1.4f, 1.4f);
        }
        md.getRoot().addOrReplaceChild("aura", c, PartPose.ZERO);
        return aura = wrap(LayerDefinition.create(md, 64, 64).bakeRoot());
    }

    private static double bell(double a, double peak) {
        double x = (a - peak) / Math.max(peak, 1 - peak);
        return Math.max(0, 1 - x * x);
    }
}
