package com.luminamc.ui.components;

import javafx.geometry.Insets;
import javafx.scene.Group;
import javafx.scene.effect.DropShadow;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

/**
 * A stylised cape preview: a rich fabric gradient with a satin sheen, a luminous
 * radial glow and the faceted Lumina crystal crest in the centre. Pure scene-graph,
 * so it renders crisply at any size and matches the 3D model's cape texture.
 */
public final class CapeView {

    private CapeView() {}

    /** Builds the default Lumina (violet) cape preview. */
    public static StackPane build(double height) {
        return build(height, "#C084FC", "#5B21B6");
    }

    /** Builds a cape preview {@code height} px tall with a {@code top→bottom} gradient. */
    public static StackPane build(double height, String topHex, String botHex) {
        double w = Math.round(height * 0.625);
        Color top = Color.web(topHex), bottom = Color.web(botHex);
        Color mid = top.interpolate(bottom, 0.5);

        Region fabric = new Region();
        fabric.setPrefSize(w, height);
        fabric.setMinSize(w, height);
        fabric.setMaxSize(w, height);
        LinearGradient g = new LinearGradient(0, 0, 0.1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, top.interpolate(Color.WHITE, 0.14)),
                new Stop(0.5, mid),
                new Stop(1.0, bottom.interpolate(Color.BLACK, 0.18)));
        fabric.setBackground(new Background(new BackgroundFill(g, new CornerRadii(10), Insets.EMPTY)));
        fabric.setBorder(new Border(new BorderStroke(top.interpolate(Color.WHITE, 0.3),
                BorderStrokeStyle.SOLID, new CornerRadii(10), new BorderWidths(1.4))));

        // Diagonal satin sheen.
        Region sheen = new Region();
        sheen.setPrefSize(w, height);
        sheen.setMaxSize(w, height);
        sheen.setMouseTransparent(true);
        LinearGradient s = new LinearGradient(0, 0, 1, 0.4, true, CycleMethod.NO_CYCLE,
                new Stop(0.0, Color.web("#FFFFFF", 0.16)),
                new Stop(0.42, Color.web("#FFFFFF", 0.0)),
                new Stop(1.0, Color.web("#000000", 0.18)));
        sheen.setBackground(new Background(new BackgroundFill(s, new CornerRadii(10), Insets.EMPTY)));

        // Soft radial glow behind the crest.
        Circle radial = new Circle(w * 0.45, mix(top, Color.WHITE, 0.5, 0.4));
        radial.setEffect(new GaussianBlur(Math.max(12, w * 0.3)));
        radial.setMouseTransparent(true);

        // Faceted Lumina crystal crest with a luminous halo.
        Group crystal = CrystalLogo.crystalNode(w * 0.46);
        crystal.setEffect(new DropShadow(10, Color.web("#EADCFF")));

        StackPane cape = new StackPane(fabric, sheen, radial, crystal);
        cape.setMaxSize(w, height);
        cape.setMinSize(w, height);

        Rectangle clip = new Rectangle(w, height);
        clip.setArcWidth(18);
        clip.setArcHeight(18);
        cape.setClip(clip);

        DropShadow glow = new DropShadow(20, mid);
        glow.setSpread(0.05);
        cape.setEffect(glow);
        return cape;
    }

    private static Color mix(Color a, Color b, double t, double alpha) {
        Color c = a.interpolate(b, t);
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), alpha);
    }
}
