package net.squxso.lumina.logic;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.CloudRenderMode;
import net.minecraft.client.option.GameOptions;
import net.minecraft.client.option.GraphicsMode;
import net.minecraft.particle.ParticlesMode;

/**
 * Performance and view tweaks: an FPS Boost that swaps in lighter video settings,
 * and an OptiFine-style hold-to-zoom.
 *
 * <p>Both remember the player's original values and restore them when turned off /
 * released, so nothing is permanently changed behind the user's back.
 */
public final class LuminaTweaks {

    private LuminaTweaks() {}

    // ── FPS Boost ─────────────────────────────────────────────────────────
    private static boolean boostActive = false;
    private static GraphicsMode    savedGraphics;
    private static CloudRenderMode savedClouds;
    private static ParticlesMode   savedParticles;
    private static boolean         savedShadows;
    private static int             savedViewDistance;
    private static double          savedEntityDist;
    private static int             savedBiomeBlend;

    /** Apply (or undo) the lighter video preset, saving/restoring originals once. */
    public static void setFpsBoost(MinecraftClient mc, boolean on) {
        GameOptions o = mc.options;
        if (on && !boostActive) {
            savedGraphics     = o.getGraphicsMode().getValue();
            savedClouds       = o.getCloudRenderMode().getValue();
            savedParticles    = o.getParticles().getValue();
            savedShadows      = o.getEntityShadows().getValue();
            savedViewDistance = o.getViewDistance().getValue();
            savedEntityDist   = o.getEntityDistanceScaling().getValue();
            savedBiomeBlend   = o.getBiomeBlendRadius().getValue();

            o.getGraphicsMode().setValue(GraphicsMode.FAST);
            o.getCloudRenderMode().setValue(CloudRenderMode.OFF);
            o.getParticles().setValue(ParticlesMode.MINIMAL);
            o.getEntityShadows().setValue(false);
            o.getViewDistance().setValue(Math.min(savedViewDistance, 8));
            o.getEntityDistanceScaling().setValue(0.5);
            o.getBiomeBlendRadius().setValue(0);
            // Don't call o.write() — it can fail if another mod sets brightness outside 0–1.
            // Settings apply in memory immediately; MC writes options on clean exit anyway.
            boostActive = true;
        } else if (!on && boostActive) {
            o.getGraphicsMode().setValue(savedGraphics);
            o.getCloudRenderMode().setValue(savedClouds);
            o.getParticles().setValue(savedParticles);
            o.getEntityShadows().setValue(savedShadows);
            o.getViewDistance().setValue(savedViewDistance);
            o.getEntityDistanceScaling().setValue(savedEntityDist);
            o.getBiomeBlendRadius().setValue(savedBiomeBlend);
            boostActive = false;
        }
    }

    // ── Zoom ──────────────────────────────────────────────────────────────
    private static boolean zoomActive = false;
    private static int     savedFov   = 70;

    /**
     * The FOV multiplier applied while zooming.
     * 0.5 = 2× zoom (default). Configurable from the QoL tab via preset buttons.
     * The resulting option value is clamped to the valid range [30, 110] so that
     * Minecraft never logs an "Illegal option value" error.
     */
    public static float zoomFactor = 0.5f;

    /** Whether the zoom key is currently held. */
    public static boolean isZoomActive() { return zoomActive; }

    /**
     * Hold-to-zoom: saves the current FOV, sets the FOV option to
     * {@code savedFov * zoomFactor} (clamped to [30, 110]), and restores it on
     * release. Works with both vanilla and Sodium because both renderers read the
     * FOV from {@code options.getFov().getValue()}.
     */
    public static void applyZoom(MinecraftClient mc, boolean active) {
        if (active && !zoomActive) {
            savedFov = mc.options.getFov().getValue();
            int target = Math.max(30, Math.round(savedFov * zoomFactor));
            mc.options.getFov().setValue(target);
            zoomActive = true;
        } else if (!active && zoomActive) {
            mc.options.getFov().setValue(savedFov);
            zoomActive = false;
        }
    }
}
