package com.luminamc.ui.components;

import javafx.scene.Group;
import javafx.scene.Node;
import javafx.scene.effect.DropShadow;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Polygon;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.scene.text.Text;
import javafx.geometry.Pos;

/**
 * A vector "LUMINA" logo — a faceted violet crystal cluster above the wordmark.
 * Used as the launcher's built-in branding when no logo image is supplied; users
 * can override it by dropping their own {@code ~/.luminamc/logo.png}.
 */
public final class CrystalLogo {

    private CrystalLogo() {}

    private static final Color DEEP   = Color.web("#5B21B6");
    private static final Color MID    = Color.web("#7C3AED");
    private static final Color LIGHT  = Color.web("#A78BFA");
    private static final Color HILITE = Color.web("#DDD6FE");

    /** Builds the crystal + wordmark, scaled so the crystal is {@code gemHeight}px tall. */
    public static Node build(double gemHeight, double fontSize) {
        Group crystal = crystal(gemHeight);

        Text wordmark = new Text("LUMINA");
        wordmark.setFont(Font.font("Segoe UI", FontWeight.BOLD, fontSize));
        wordmark.setFill(new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, LIGHT), new Stop(1, MID)));
        wordmark.setEffect(new DropShadow(fontSize * 0.6, MID));

        VBox box = new VBox(gemHeight * 0.12, crystal, wordmark);
        box.setAlignment(Pos.CENTER);
        return box;
    }

    /** Just the crystal cluster (no wordmark) — handy for the window icon. */
    public static Group crystalNode(double gemHeight) {
        return crystal(gemHeight);
    }

    private static Group crystal(double gemHeight) {
        double s = gemHeight / 100.0;

        // Main upright shard, split into a darker left facet and a lighter right facet.
        Polygon outline = poly(s, 50, 2, 74, 34, 60, 92, 40, 92, 26, 34);
        outline.setFill(MID);
        Polygon left = poly(s, 50, 2, 26, 34, 40, 92, 50, 46);
        left.setFill(grad(MID, DEEP));
        Polygon right = poly(s, 50, 2, 74, 34, 60, 92, 50, 46);
        right.setFill(grad(LIGHT, MID));
        Polygon glint = poly(s, 50, 2, 44, 30, 50, 46, 53, 28);
        glint.setFill(HILITE);
        glint.setOpacity(0.85);

        // A smaller crystal nestled to the lower-left for a "cluster" feel.
        Group small = new Group(
                fill(poly(s, 24, 46, 36, 58, 30, 96, 18, 92), grad(MID, DEEP)),
                fill(poly(s, 24, 46, 30, 96, 30, 64), LIGHT.deriveColor(0, 1, 1, 0.9)));
        // And a tiny one to the right.
        Group tiny = fillGroup(poly(s, 70, 54, 82, 64, 76, 94, 66, 86), grad(LIGHT, MID));

        Group g = new Group(small, tiny, outline, left, right, glint);
        DropShadow glow = new DropShadow();
        glow.setColor(MID);
        glow.setRadius(22 * s);
        glow.setSpread(0.25);
        g.setEffect(glow);
        return g;
    }

    // ── shape helpers ───────────────────────────────────────────────────

    private static Polygon poly(double s, double... coords) {
        Polygon p = new Polygon();
        for (int i = 0; i < coords.length; i++) p.getPoints().add(coords[i] * s);
        p.setStroke(DEEP.deriveColor(0, 1, 0.8, 1));
        p.setStrokeWidth(Math.max(0.6, s));
        return p;
    }

    private static Polygon fill(Polygon p, javafx.scene.paint.Paint paint) {
        p.setFill(paint);
        return p;
    }

    private static Group fillGroup(Polygon p, javafx.scene.paint.Paint paint) {
        p.setFill(paint);
        return new Group(p);
    }

    private static LinearGradient grad(Color from, Color to) {
        return new LinearGradient(0, 0, 0, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, from), new Stop(1, to));
    }
}
