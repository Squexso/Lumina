package com.luminamc.ui.components;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.WritableImage;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;

/**
 * Generates a premium cape back texture for the 3D model by rendering a rich
 * fabric gradient, a soft radial glow, a diagonal sheen and the faceted Lumina
 * crystal (the same vector crystal used everywhere) — then snapshotting it.
 *
 * <p>Must be called on the JavaFX application thread.
 */
public final class CapeTexture {

    private CapeTexture() {}

    private static final int W = 120, H = 220;

    public static WritableImage build(Color top, Color bottom) {
        Color mid = top.interpolate(bottom, 0.5);

        // Rich vertical fabric gradient (lifted highlight at top, deepened hem).
        Region fabric = new Region();
        fabric.setPrefSize(W, H);
        LinearGradient g = new LinearGradient(0, 0, 0.12, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, top.interpolate(Color.WHITE, 0.14)),
                new Stop(0.5, mid),
                new Stop(1.0, bottom.interpolate(Color.BLACK, 0.18)));
        fabric.setBackground(new Background(new BackgroundFill(g, CornerRadii.EMPTY, Insets.EMPTY)));

        // Soft radial glow behind the crest.
        Circle glow = new Circle(W * 0.34, mix(top, Color.WHITE, 0.55, 0.42));
        glow.setEffect(new GaussianBlur(34));

        // Diagonal satin sheen.
        Region sheen = new Region();
        sheen.setPrefSize(W, H);
        LinearGradient s = new LinearGradient(0, 0, 1, 0.35, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.color(1, 1, 1, 0.16)),
                new Stop(0.42, Color.TRANSPARENT),
                new Stop(1.0, Color.color(0, 0, 0, 0.18)));
        sheen.setBackground(new Background(new BackgroundFill(s, CornerRadii.EMPTY, Insets.EMPTY)));

        // Faceted Lumina crystal with a luminous halo.
        Group crystal = CrystalLogo.crystalNode(W * 0.5);
        crystal.setEffect(new DropShadow(16, Color.web("#EADCFF")));
        StackPane emblem = new StackPane(crystal);
        emblem.setTranslateY(-H * 0.02);

        StackPane node = new StackPane(fabric, glow, sheen, emblem);
        StackPane.setAlignment(glow, Pos.CENTER);
        node.setPrefSize(W, H);
        node.setMinSize(W, H);
        node.setMaxSize(W, H);

        new Scene(node);                 // force CSS + layout for the off-screen snapshot
        node.applyCss();
        node.layout();

        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        return node.snapshot(sp, new WritableImage(W, H));
    }

    private static Color mix(Color a, Color b, double t, double alpha) {
        Color c = a.interpolate(b, t);
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }
}
