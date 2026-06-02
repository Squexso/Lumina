package com.luminamc.ui.components;

import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

import java.util.Random;

/**
 * A subtle animated-looking star field rendered on a {@link Canvas}.
 *
 * <p>Stars are generated once with fractional coordinates, then scaled to the
 * current size on every resize — so they reposition nicely as the window grows.
 * Sits behind the UI to give the cosmic violet look.
 */
public final class StarField extends Region {

    private record Star(double fx, double fy, double r, double alpha) {}

    private final Canvas canvas = new Canvas();
    private final Star[] stars;

    public StarField() {
        this(220);
    }

    public StarField(int count) {
        Random rng = new Random(736459L); // fixed seed → stable layout across runs
        stars = new Star[count];
        for (int i = 0; i < count; i++) {
            double r = rng.nextDouble();
            double radius = r < 0.82 ? 0.5 + rng.nextDouble() * 1.0   // tiny
                          : r < 0.96 ? 1.4 + rng.nextDouble() * 0.8   // medium
                                     : 2.2 + rng.nextDouble() * 1.1;  // bright
            double alpha = 0.18 + rng.nextDouble() * 0.7;
            stars[i] = new Star(rng.nextDouble(), rng.nextDouble(), radius, alpha);
        }

        getChildren().add(canvas);
        setMinSize(0, 0);
        setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        setMouseTransparent(true);

        widthProperty().addListener((o, a, b) -> redraw());
        heightProperty().addListener((o, a, b) -> redraw());
    }

    @Override
    protected void layoutChildren() {
        double w = getWidth(), h = getHeight();
        canvas.setWidth(w);
        canvas.setHeight(h);
        redraw();
    }

    private void redraw() {
        double w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, w, h);
        for (Star s : stars) {
            double x = s.fx * w, y = s.fy * h;
            // Brighter stars get a faint glow.
            if (s.r > 2.0) {
                g.setFill(Color.web("#C4B5FD", s.alpha * 0.25));
                g.fillOval(x - s.r * 2, y - s.r * 2, s.r * 4, s.r * 4);
            }
            g.setFill(Color.web("#FFFFFF", s.alpha));
            g.fillOval(x - s.r, y - s.r, s.r * 2, s.r * 2);
        }
    }
}
