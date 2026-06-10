package com.luminamc.ui.components;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.RadialGradient;
import javafx.scene.paint.Stop;

import java.util.Random;

/**
 * Static, hand-painted space scenery behind the UI: distant galaxies, soft nebula
 * wisps, aurora curtains and star clusters — small details so the background never
 * feels empty. Painted once per resize (zero per-frame cost) and matched to the
 * active background preset; hidden entirely for custom user images.
 */
public final class SpaceArt extends Region {

    private final Canvas canvas = new Canvas();
    private String preset = "nebula";

    public SpaceArt(String presetId) {
        this.preset = presetId == null ? "nebula" : presetId;
        getChildren().add(canvas);
        setMinSize(0, 0);
        setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        setMouseTransparent(true);
    }

    /** Switches the scenery to match a background preset (repaints immediately). */
    public void setPreset(String presetId) {
        this.preset = presetId == null ? "nebula" : presetId;
        paint();
    }

    @Override
    protected void layoutChildren() {
        canvas.setWidth(getWidth());
        canvas.setHeight(getHeight());
        paint();
    }

    // ── composition per preset ───────────────────────────────────────────

    private void paint() {
        double w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, w, h);

        switch (preset) {
            case "nebula" -> {
                nebula(g, w * 0.16, h * 0.74, w * 0.30, Color.web("#7C3AED"), 0.10, 11);
                nebula(g, w * 0.50, h * 0.92, w * 0.22, Color.web("#EC4899"), 0.05, 12);
                galaxy(g, w * 0.85, h * 0.16, 86, -24, Color.web("#C4B5FD"), 0.13);
                cluster(g, w * 0.32, h * 0.22, 60, 26, 21);
            }
            case "universe" -> {
                galaxy(g, w * 0.78, h * 0.20, 150, -18, Color.web("#A78BFA"), 0.16);
                nebula(g, w * 0.20, h * 0.68, w * 0.26, Color.web("#7C3AED"), 0.11, 21);
                nebula(g, w * 0.62, h * 0.88, w * 0.20, Color.web("#22D3EE"), 0.06, 22);
                cluster(g, w * 0.36, h * 0.30, 80, 34, 23);
                galaxy(g, w * 0.10, h * 0.34, 52, 38, Color.web("#67E8F9"), 0.08);
            }
            case "galaxy" -> {
                galaxy(g, w * 0.52, h * 0.40, 230, -22, Color.web("#93C5FD"), 0.17);
                galaxy(g, w * 0.14, h * 0.76, 70, 30, Color.web("#C4B5FD"), 0.09);
                nebula(g, w * 0.85, h * 0.78, w * 0.18, Color.web("#3B82F6"), 0.07, 31);
                cluster(g, w * 0.80, h * 0.30, 70, 30, 32);
            }
            case "dusk" -> {
                haze(g, w * 0.50, h * 0.80, w * 0.65, h * 0.10, Color.web("#F59E0B"), 0.07);
                haze(g, w * 0.45, h * 0.66, w * 0.55, h * 0.08, Color.web("#EC4899"), 0.05);
                galaxy(g, w * 0.82, h * 0.18, 64, -16, Color.web("#FBCFE8"), 0.08);
            }
            case "midnight" -> {
                nebula(g, w * 0.22, h * 0.72, w * 0.26, Color.web("#3B82F6"), 0.08, 41);
                galaxy(g, w * 0.82, h * 0.22, 84, -26, Color.web("#93C5FD"), 0.11);
                cluster(g, w * 0.55, h * 0.16, 70, 28, 42);
            }
            case "ocean" -> {
                nebula(g, w * 0.20, h * 0.76, w * 0.28, Color.web("#14B8A6"), 0.09, 51);
                nebula(g, w * 0.70, h * 0.88, w * 0.20, Color.web("#06B6D4"), 0.06, 52);
                galaxy(g, w * 0.84, h * 0.18, 72, -20, Color.web("#99F6E4"), 0.09);
            }
            case "aurora" -> {
                curtain(g, w * 0.22, h * 0.30, h * 0.42, -14, Color.web("#34D399"), 0.10);
                curtain(g, w * 0.36, h * 0.26, h * 0.50, -8, Color.web("#6EE7B7"), 0.08);
                curtain(g, w * 0.52, h * 0.30, h * 0.44, -3, Color.web("#34D399"), 0.09);
                curtain(g, w * 0.67, h * 0.34, h * 0.36, 5, Color.web("#A7F3D0"), 0.06);
                cluster(g, w * 0.82, h * 0.20, 60, 24, 61);
            }
            case "ember" -> {
                nebula(g, w * 0.24, h * 0.80, w * 0.30, Color.web("#F43F5E"), 0.08, 71);
                nebula(g, w * 0.70, h * 0.90, w * 0.20, Color.web("#F59E0B"), 0.05, 72);
                galaxy(g, w * 0.84, h * 0.20, 66, -22, Color.web("#FDA4AF"), 0.08);
            }
            case "graphite" -> {
                nebula(g, w * 0.25, h * 0.75, w * 0.26, Color.web("#9CA3AF"), 0.05, 81);
                galaxy(g, w * 0.82, h * 0.20, 70, -20, Color.web("#D1D5DB"), 0.06);
            }
            case "void" -> galaxy(g, w * 0.80, h * 0.22, 64, -18, Color.web("#9CA3AF"), 0.05);
            default -> { /* custom image → no scenery */ }
        }
    }

    // ── painting helpers (all soft, low-alpha, clean) ────────────────────

    /** A distant spiral galaxy: tilted soft disc + bright core + faint arm arcs + plane stars. */
    private void galaxy(GraphicsContext g, double cx, double cy, double size, double angleDeg,
                        Color tint, double alpha) {
        g.save();
        g.translate(cx, cy);
        g.rotate(angleDeg);
        g.scale(1, 0.38);                                  // squash circle → galaxy disc

        g.setFill(new RadialGradient(0, 0, 0, 0, size, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, tint.deriveColor(0, 1, 1, alpha)),
                new Stop(0.55, tint.deriveColor(0, 1, 1, alpha * 0.40)),
                new Stop(1.0, Color.TRANSPARENT)));
        g.fillOval(-size, -size, size * 2, size * 2);

        double core = size * 0.30;
        g.setFill(new RadialGradient(0, 0, 0, 0, core, false, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.web("#FFFFFF", alpha * 2.2)),
                new Stop(1.0, Color.TRANSPARENT)));
        g.fillOval(-core, -core, core * 2, core * 2);

        // Two faint spiral-arm hints.
        g.setStroke(tint.deriveColor(0, 1, 1, alpha * 0.8));
        g.setLineWidth(size * 0.05);
        g.strokeArc(-size * 0.78, -size * 0.78, size * 1.56, size * 1.56, 20, 130, javafx.scene.shape.ArcType.OPEN);
        g.strokeArc(-size * 0.78, -size * 0.78, size * 1.56, size * 1.56, 200, 130, javafx.scene.shape.ArcType.OPEN);

        // A sprinkle of stars in the galactic plane.
        Random rng = new Random((long) (cx * 31 + cy * 17));
        g.setFill(Color.web("#FFFFFF", Math.min(1, alpha * 3)));
        for (int i = 0; i < 26; i++) {
            double a = rng.nextDouble() * Math.PI * 2;
            double d = Math.pow(rng.nextDouble(), 0.6) * size * 0.9;
            double r = 0.5 + rng.nextDouble() * 0.8;
            g.fillOval(Math.cos(a) * d - r, Math.sin(a) * d - r, r * 2, r * 2);
        }
        g.restore();
    }

    /** Soft nebula: several overlapping colour clouds with a hint of inner depth. */
    private void nebula(GraphicsContext g, double cx, double cy, double size,
                        Color tint, double alpha, long seed) {
        Random rng = new Random(seed);
        for (int i = 0; i < 6; i++) {
            double ox = (rng.nextDouble() - 0.5) * size * 1.1;
            double oy = (rng.nextDouble() - 0.5) * size * 0.7;
            double r = size * (0.30 + rng.nextDouble() * 0.45);
            double a = alpha * (0.55 + rng.nextDouble() * 0.45);
            Color c = i % 3 == 2 ? tint.deriveColor(18, 1, 1.15, 1) : tint;   // slight hue shimmer
            g.setFill(new RadialGradient(0, 0, cx + ox, cy + oy, r, false, CycleMethod.NO_CYCLE,
                    new Stop(0, c.deriveColor(0, 1, 1, a)),
                    new Stop(1, Color.TRANSPARENT)));
            g.fillOval(cx + ox - r, cy + oy - r, r * 2, r * 2);
        }
        // A few embedded bright specks.
        g.setFill(Color.web("#FFFFFF", Math.min(1, alpha * 2.6)));
        for (int i = 0; i < 9; i++) {
            double x = cx + (rng.nextDouble() - 0.5) * size * 1.2;
            double y = cy + (rng.nextDouble() - 0.5) * size * 0.8;
            double r = 0.5 + rng.nextDouble() * 0.9;
            g.fillOval(x - r, y - r, r * 2, r * 2);
        }
    }

    /** A wide, flat band of soft glow (horizon haze). */
    private void haze(GraphicsContext g, double cx, double cy, double width, double height,
                      Color tint, double alpha) {
        g.save();
        g.translate(cx, cy);
        g.scale(1, Math.max(0.02, height / width));        // squash circle → wide flat band
        double r = width / 2;
        g.setFill(new RadialGradient(0, 0, 0, 0, r, false, CycleMethod.NO_CYCLE,
                new Stop(0, tint.deriveColor(0, 1, 1, alpha)),
                new Stop(1, Color.TRANSPARENT)));
        g.fillOval(-r, -r, r * 2, r * 2);
        g.restore();
    }

    /** A tall, soft aurora curtain (rotated vertical streak). */
    private void curtain(GraphicsContext g, double cx, double cy, double height, double angleDeg,
                         Color tint, double alpha) {
        g.save();
        g.translate(cx, cy);
        g.rotate(angleDeg);
        g.scale(0.16, 1);                                  // squash circle → tall streak
        double r = height / 2;
        g.setFill(new RadialGradient(0, 0, 0, 0, r, false, CycleMethod.NO_CYCLE,
                new Stop(0, tint.deriveColor(0, 1, 1, alpha)),
                new Stop(1, Color.TRANSPARENT)));
        g.fillOval(-r, -r, r * 2, r * 2);
        g.restore();
    }

    /** A loose cluster of faint distant stars. */
    private void cluster(GraphicsContext g, double cx, double cy, double spread, int count, long seed) {
        Random rng = new Random(seed);
        for (int i = 0; i < count; i++) {
            double x = cx + rng.nextGaussian() * spread * 0.5;
            double y = cy + rng.nextGaussian() * spread * 0.35;
            double r = 0.4 + rng.nextDouble() * 0.8;
            g.setFill(Color.web("#FFFFFF", 0.10 + rng.nextDouble() * 0.22));
            g.fillOval(x - r, y - r, r * 2, r * 2);
        }
    }
}
