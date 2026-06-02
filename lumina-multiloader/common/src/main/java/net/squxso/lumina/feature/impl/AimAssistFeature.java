package net.squxso.lumina.feature.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.player.LocalPlayer;
import net.minecraft.world.entity.Entity;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.AABB;
import net.minecraft.world.phys.Vec3;
import net.squxso.lumina.feature.Feature;
import net.squxso.lumina.feature.FeatureCategory;

/**
 * Aim assist: smoothly pulls the player's aim toward the nearest living entity that
 * is already roughly in front of them (within a cone). It nudges rather than snaps —
 * only assists when a target is inside {@link #FOV} degrees and within {@link #RANGE}
 * blocks, easing toward it by {@link #STRENGTH} each tick.
 */
public final class AimAssistFeature extends Feature {

    private static final double RANGE = 5.0;       // blocks
    private static final double FOV = 35.0;        // only assist within this cone (degrees)
    private static final float STRENGTH = 0.35f;   // how hard it pulls per tick (0..1)

    public AimAssistFeature() {
        super("aim_assist", "Aim Assist",
                "Smoothly pull your aim onto the nearest mob in front of you.",
                FeatureCategory.COMBAT, false);
    }

    @Override
    public void onClientTick(Minecraft mc) {
        LocalPlayer p = mc.player;
        if (p == null || mc.level == null || mc.screen != null) return;

        Vec3 eye = p.getEyePosition();
        AABB box = p.getBoundingBox().inflate(RANGE);

        float bestYaw = 0f, bestPitch = 0f;
        double bestAngle = FOV;
        boolean found = false;

        for (Entity e : mc.level.getEntities(p, box, x -> x instanceof LivingEntity && x.isAlive() && x != p)) {
            Vec3 c = e.getBoundingBox().getCenter();
            double dx = c.x - eye.x, dy = c.y - eye.y, dz = c.z - eye.z;
            double horiz = Math.sqrt(dx * dx + dz * dz);
            if (Math.sqrt(horiz * horiz + dy * dy) > RANGE) continue;

            float wantYaw = (float) Math.toDegrees(Math.atan2(-dx, dz));
            float wantPitch = (float) -Math.toDegrees(Math.atan2(dy, horiz));
            float dYaw = wrap(wantYaw - p.getYRot());
            float dPitch = wantPitch - p.getXRot();
            double ang = Math.sqrt(dYaw * dYaw + dPitch * dPitch);
            if (ang < bestAngle) { bestAngle = ang; bestYaw = dYaw; bestPitch = dPitch; found = true; }
        }

        if (!found) return;
        p.setYRot(p.getYRot() + bestYaw * STRENGTH);
        p.setXRot(clamp(p.getXRot() + bestPitch * STRENGTH, -90f, 90f));
    }

    private static float wrap(float a) {
        a %= 360f;
        if (a >= 180f) a -= 360f;
        if (a < -180f) a += 360f;
        return a;
    }
    private static float clamp(float v, float lo, float hi) { return v < lo ? lo : Math.min(v, hi); }
}
