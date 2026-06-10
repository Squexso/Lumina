package com.luminamc.ui.components;

import javafx.animation.AnimationTimer;
import javafx.scene.canvas.Canvas;
import javafx.scene.canvas.GraphicsContext;
import javafx.scene.layout.Region;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

/**
 * A living star field rendered on a {@link Canvas}: stars twinkle softly (each
 * with its own phase and speed), drift very slowly across the sky, and every few
 * seconds a shooting star streaks by. Subtle by design — it sits behind the UI.
 *
 * <p>The animation only runs while the node is visible and attached to a scene,
 * so toggling it off in the appearance panel also stops the timer (no idle CPU).
 */
public final class StarField extends Region {

    private static final class Star {
        double fx, fy;      // fractional position (0..1)
        double r;           // radius in px
        double base;        // base alpha
        double phase, speed;// twinkle phase + speed (rad/s)
        double drift;       // horizontal drift (fraction of width per second)
        Color tint;
    }

    private static final class Meteor {
        double x, y, vx, vy;
        double age, life;
    }

    private final Canvas canvas = new Canvas();
    private final Star[] stars;
    private final List<Meteor> meteors = new ArrayList<>();
    private final Random rng = new Random();

    private long lastNs = 0;
    private double t = 0;             // seconds since start
    private double nextMeteorAt = 4;  // first shooting star after a few seconds
    private double frameAcc = 0;      // paint at ~30 fps — twinkle stays smooth, half the work

    private static final double FRAME = 1 / 30.0;

    private final AnimationTimer timer = new AnimationTimer() {
        @Override public void handle(long now) {
            if (lastNs == 0) { lastNs = now; return; }
            double dt = Math.min(0.05, (now - lastNs) / 1e9);
            lastNs = now;
            t += dt;
            update(dt);
            frameAcc += dt;
            if (frameAcc >= FRAME) {
                frameAcc = 0;
                draw();
            }
        }
    };

    public StarField() {
        this(230);
    }

    public StarField(int count) {
        Random seeded = new Random(736459L); // fixed seed → stable layout across runs
        stars = new Star[count];
        for (int i = 0; i < count; i++) {
            Star s = new Star();
            double r = seeded.nextDouble();
            s.r = r < 0.80 ? 0.5 + seeded.nextDouble() * 1.0    // tiny
                : r < 0.95 ? 1.4 + seeded.nextDouble() * 0.9    // medium
                           : 2.3 + seeded.nextDouble() * 1.2;   // bright
            s.fx = seeded.nextDouble();
            s.fy = seeded.nextDouble();
            s.base = 0.25 + seeded.nextDouble() * 0.6;
            s.phase = seeded.nextDouble() * Math.PI * 2;
            s.speed = 0.35 + seeded.nextDouble() * 1.3;
            s.drift = (0.0006 + seeded.nextDouble() * 0.0014) * (s.r > 1.4 ? 1.6 : 1.0);
            double c = seeded.nextDouble();
            s.tint = c < 0.76 ? Color.WHITE
                   : c < 0.90 ? Color.web("#C4B5FD")     // pale violet
                              : Color.web("#A5F3FC");    // pale cyan
            stars[i] = s;
        }

        getChildren().add(canvas);
        setMinSize(0, 0);
        setPrefSize(Region.USE_COMPUTED_SIZE, Region.USE_COMPUTED_SIZE);
        setMouseTransparent(true);

        // Run only while visible + attached (the appearance panel toggles visibility).
        Runnable sync = () -> {
            boolean run = isVisible() && getScene() != null;
            lastNs = 0;
            if (run) timer.start(); else timer.stop();
        };
        visibleProperty().addListener((o, a, b) -> sync.run());
        sceneProperty().addListener((o, a, b) -> sync.run());
    }

    @Override
    protected void layoutChildren() {
        canvas.setWidth(getWidth());
        canvas.setHeight(getHeight());
        draw();
    }

    // ── simulation ───────────────────────────────────────────────────────

    private void update(double dt) {
        for (Star s : stars) {
            s.fx += s.drift * dt;
            if (s.fx > 1.02) s.fx = -0.02;
        }

        // Spawn a shooting star every ~5–12 s.
        if (t >= nextMeteorAt) {
            nextMeteorAt = t + 5 + rng.nextDouble() * 7;
            double w = getWidth(), h = getHeight();
            if (w > 100 && h > 100) {
                Meteor m = new Meteor();
                m.x = w * (0.15 + rng.nextDouble() * 0.7);
                m.y = h * (0.04 + rng.nextDouble() * 0.3);
                double speed = 480 + rng.nextDouble() * 260;
                double angle = Math.toRadians(20 + rng.nextDouble() * 25);
                double dir = rng.nextBoolean() ? 1 : -1;     // left or right
                m.vx = dir * speed * Math.cos(angle);
                m.vy = speed * Math.sin(angle);
                m.life = 0.65 + rng.nextDouble() * 0.35;
                meteors.add(m);
            }
        }
        meteors.removeIf(m -> (m.age += dt) >= m.life);
        for (Meteor m : meteors) {
            m.x += m.vx * dt;
            m.y += m.vy * dt;
        }
    }

    // ── painting ─────────────────────────────────────────────────────────

    private void draw() {
        double w = getWidth(), h = getHeight();
        if (w <= 0 || h <= 0) return;
        GraphicsContext g = canvas.getGraphicsContext2D();
        g.clearRect(0, 0, w, h);

        for (Star s : stars) {
            double x = s.fx * w, y = s.fy * h;
            double twinkle = 0.72 + 0.28 * Math.sin(s.phase + t * s.speed);
            double a = s.base * twinkle;
            if (s.r > 2.0) {                              // bright stars get a soft halo
                g.setFill(s.tint.deriveColor(0, 1, 1, a * 0.22));
                g.fillOval(x - s.r * 2.6, y - s.r * 2.6, s.r * 5.2, s.r * 5.2);
            }
            g.setFill(s.tint.deriveColor(0, 1, 1, a));
            g.fillOval(x - s.r, y - s.r, s.r * 2, s.r * 2);
            if (s.r > 2.4) {                              // the very brightest get a clean glint cross
                double len = s.r * 3.2 * twinkle;
                g.setStroke(s.tint.deriveColor(0, 1, 1, a * 0.45));
                g.setLineWidth(0.8);
                g.strokeLine(x - len, y, x + len, y);
                g.strokeLine(x, y - len, x, y + len);
            }
        }

        // Shooting stars: a fading trail of dots behind a bright head.
        for (Meteor m : meteors) {
            double fade = 1.0 - m.age / m.life;            // 1 → 0 over its life
            double len = 90;                               // trail length in px
            double nx = m.vx, ny = m.vy;
            double norm = Math.hypot(nx, ny);
            nx /= norm; ny /= norm;
            int seg = 14;
            for (int i = 0; i < seg; i++) {
                double f = i / (double) seg;
                double a = fade * (1 - f) * 0.85;
                double r = 1.8 * (1 - f) + 0.4;
                g.setFill(Color.web(i < 3 ? "#FFFFFF" : "#C4B5FD", a));
                g.fillOval(m.x - nx * len * f - r, m.y - ny * len * f - r, r * 2, r * 2);
            }
        }
    }
}
