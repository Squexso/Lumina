package net.squxso.lumina.client;

import com.mojang.blaze3d.platform.NativeImage;
import com.mojang.blaze3d.vertex.PoseStack;
import com.mojang.math.Axis;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.Model;
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

    private static Model wings, halo, aura;
    private static int colourArgb;
    private static Identifier colourTex;

    public static void render(SubmitNodeCollector collector, PoseStack pose, AvatarRenderState state,
                              int light, String type, int argb) {
        Model model = switch (type) {
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
        collector.submitModel(model, state, pose, RenderTypes.entitySolid(colourTex(argb)),
                light, OverlayTexture.NO_OVERLAY, state.outlineColor, null);
        pose.popPose();
    }

    // ── colour texture ───────────────────────────────────────────────────────

    private static Identifier colourTex(int argb) {
        if (colourTex != null && colourArgb == argb) return colourTex;
        int a = (argb >>> 24) & 0xFF, r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
        int abgr = (a << 24) | (b << 16) | (g << 8) | r;   // NativeImage is ABGR
        NativeImage img = new NativeImage(8, 8, false);
        for (int y = 0; y < 8; y++) for (int x = 0; x < 8; x++) img.setPixel(x, y, abgr);
        Identifier id = Identifier.fromNamespaceAndPath("lumina", "textures/entity/accessory_color");
        Minecraft.getInstance().getTextureManager().register(id, new DynamicTexture(() -> "lumina_accessory", img));
        colourArgb = argb;
        colourTex = id;
        return id;
    }

    // ── geometry (built in player model space: up = -Y, back = +Z, 16 units = 1 block) ──

    private static Model wrap(ModelPart root) {
        return new Model.Simple(root, RenderTypes::entitySolid);
    }

    private static Model wings() {
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

    private static Model halo() {
        if (halo != null) return halo;
        MeshDefinition md = new MeshDefinition();
        CubeListBuilder c = CubeListBuilder.create().texOffs(0, 0);
        int n = 18;
        for (int i = 0; i < n; i++) {
            double a = i / (double) n * Math.PI * 2;
            float cx = (float) (Math.cos(a) * 5.0);
            float cz = (float) (Math.sin(a) * 5.0);
            c.addBox(cx - 0.5f, -13.0f, cz - 0.5f, 1.0f, 1.0f, 1.0f);   // a ring above the head
        }
        md.getRoot().addOrReplaceChild("halo", c, PartPose.ZERO);
        return halo = wrap(LayerDefinition.create(md, 64, 64).bakeRoot());
    }

    private static Model aura() {
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
