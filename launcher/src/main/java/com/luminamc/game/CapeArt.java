package com.luminamc.game;

import com.luminamc.shop.Cosmetic;

import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.LinearGradientPaint;
import java.awt.MultipleGradientPaint;
import java.awt.RadialGradientPaint;
import java.awt.RenderingHints;
import java.awt.geom.AffineTransform;
import java.awt.geom.Area;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Path2D;
import java.awt.geom.Point2D;
import java.awt.geom.RoundRectangle2D;
import java.awt.image.BufferedImage;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Paints every cape's <b>own design</b>: a rich fabric base (vertical gradient,
 * side shading, a soft glow behind the crest) plus a unique hand-drawn emblem per
 * cape — a crescent moon for Midnight, a spiral galaxy for Galaxy, a snowflake for
 * Frost, and so on for all 40 capes.
 *
 * <p>This is the single source of truth for cape artwork: the launcher's 3D preview,
 * the shop/wardrobe cards <em>and</em> the in-game texture (via {@link GameCapeTexture})
 * all render from here, so what you buy is exactly what you wear.
 *
 * <p>Drawn in a 100&times;160 design space scaled to the requested size (aspect matches
 * both the in-game 80&times;128 back face and the preview texture). Pure Java2D — safe
 * off the JavaFX thread; results are cached per cape + size.
 */
public final class CapeArt {

    private CapeArt() {}

    private static final Map<String, BufferedImage> CACHE = new ConcurrentHashMap<>();

    /** Renders the cape's full back design at {@code w×h} (cached). */
    public static BufferedImage render(Cosmetic cape, int w, int h) {
        return CACHE.computeIfAbsent(cape.id() + "@" + w + "x" + h, k -> paint(cape, w, h));
    }

    private static BufferedImage paint(Cosmetic cape, int w, int h) {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = img.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_STROKE_CONTROL, RenderingHints.VALUE_STROKE_PURE);
        g.scale(w / 100.0, h / 160.0);

        Color top = hex(cape.colorTop()), bot = hex(cape.colorBottom());
        base(g, top, bot);
        deco(g, cape.id(), top, bot);     // per-cape scenery/pattern layer (behind the emblem)
        motif(g, cape.id(), top, bot);

        g.dispose();
        return img;
    }

    // ── fabric base ──────────────────────────────────────────────────────

    private static void base(Graphics2D g, Color top, Color bot) {
        Color mid = mix(top, bot, 0.5);
        g.setPaint(new LinearGradientPaint(new Point2D.Double(6, 0), new Point2D.Double(0, 160),
                new float[]{0f, 0.5f, 1f},
                new Color[]{mix(top, Color.WHITE, 0.10), mid, mix(bot, Color.BLACK, 0.15)}));
        g.fillRect(0, 0, 100, 160);

        // Gentle side shading so the fabric reads as curved, not flat.
        g.setPaint(new LinearGradientPaint(new Point2D.Double(0, 0), new Point2D.Double(100, 0),
                new float[]{0f, 0.12f, 0.88f, 1f},
                new Color[]{a(Color.BLACK, 56), a(Color.BLACK, 0), a(Color.BLACK, 0), a(Color.BLACK, 64)}));
        g.fillRect(0, 0, 100, 160);

        // Soft light behind the emblem area.
        glowDot(g, 50, 58, 40, a(Color.WHITE, 26));
    }

    // ── per-cape scenery / pattern layer ─────────────────────────────────
    // Painted between the fabric base and the emblem: star fields, wave hems,
    // falling petals, rain, embers, trims … so every cape is a little scene with
    // its own extra colours, not just a gradient with one icon.

    private static void deco(Graphics2D g, String id, Color top, Color bot) {
        Color L  = mix(top, Color.WHITE, 0.55);
        Color L2 = mix(top, Color.WHITE, 0.25);
        Color D  = mix(bot, Color.BLACK, 0.35);
        switch (id) {
            case "lumina_cape" -> { beam(g, a(L, 46)); sprinkle(g, 11, 14, a(Color.WHITE, 150), true); }
            case "emerald_cape" -> { lattice(g, a(L, 30)); sprinkle(g, 12, 6, a(Color.WHITE, 120), true); }
            case "crimson_cape" -> { sash(g, a(mix(top, Color.WHITE, 0.35), 70)); hem(g, a(D, 130), 14); }
            case "ocean_cape" -> { waveHem(g, L, L2, D); bubbles(g, 13, 6, a(Color.WHITE, 110)); }
            case "sunset_cape" -> { skyBands(g, a(new Color(0xFDBA74), 90), a(new Color(0xF472B6), 80),
                    a(new Color(0x6D28D9), 90)); hills(g, a(mix(D, Color.BLACK, 0.2), 200)); }
            case "void_cape" -> { sprinkle(g, 14, 20, a(new Color(0xC4B5FD), 120), true); hem(g, a(Color.BLACK, 90), 18); }
            case "aurora_cape" -> { sprinkle(g, 15, 12, a(Color.WHITE, 110), true);
                    ribbon(g, 24, a(new Color(0x5EEAD4), 70)); ribbon(g, 72, a(new Color(0xA78BFA), 70)); }
            case "galaxy_cape" -> { sprinkle(g, 16, 26, a(Color.WHITE, 140), true);
                    glowDot(g, 18, 120, 26, a(new Color(0x7C3AED), 60));
                    glowDot(g, 84, 28, 22, a(new Color(0x22D3EE), 45)); constellation(g, 17, a(Color.WHITE, 120)); }
            case "phoenix_cape" -> { embers(g, 18, 12, new Color(0xFDE68A), new Color(0xFB923C));
                    glowDot(g, 50, 140, 34, a(new Color(0xF97316), 70)); }
            case "midnight_cape" -> { sprinkle(g, 19, 22, a(Color.WHITE, 130), true); hills(g, a(D, 220)); }
            case "rose_cape" -> { petalsFall(g, 20, 8, a(mix(L, Color.WHITE, 0.2), 170)); hem(g, a(D, 110), 12); }
            case "amber_cape" -> { sash(g, a(L, 55)); bubbles(g, 21, 4, a(mix(L, Color.WHITE, 0.3), 130)); }
            case "frost_cape" -> { sprinkle(g, 22, 16, a(Color.WHITE, 150), true); shardsHem(g, a(mix(L, Color.WHITE, 0.3), 170)); }
            case "venom_cape" -> { dripsTop(g, 23, a(L, 200)); sprinkle(g, 24, 5, a(L, 110), true); }
            case "celestial_cape" -> { constellation(g, 25, a(Color.WHITE, 140)); sprinkle(g, 26, 16, a(Color.WHITE, 130), true); }
            case "slate_cape" -> { pinstripes(g, 30, a(Color.WHITE, 60)); hem(g, a(D, 90), 10); }
            case "moss_cape" -> { leavesFall(g, 27, 8, a(L, 160)); hem(g, a(D, 120), 12); }
            case "sand_cape" -> { dunesHem(g, a(L2, 140), a(D, 130)); sprinkle(g, 28, 6, a(Color.WHITE, 80), true); }
            case "coal_cape" -> { sprinkle(g, 29, 18, a(mix(L2, Color.WHITE, 0.2), 60), false); hem(g, a(Color.BLACK, 90), 14); }
            case "lavender_cape" -> { petalsFall(g, 31, 10, a(mix(L, Color.WHITE, 0.15), 160)); }
            case "mint_cape" -> { pinstripes(g, 26, a(Color.WHITE, 70)); leavesFall(g, 32, 4, a(L, 140)); }
            case "honey_cape" -> { hexLattice(g, a(mix(L, Color.WHITE, 0.1), 46)); }
            case "berry_cape" -> { berriesFall(g, 33, a(L, 170), a(mix(L, Color.WHITE, 0.3), 200)); }
            case "flame_cape" -> { flameHem(g, mix(L, Color.WHITE, 0.15), L2); embers(g, 34, 8, mix(L, Color.WHITE, 0.4), L); }
            case "sky_cape" -> { skyBands(g, a(Color.WHITE, 40), a(new Color(0xBAE6FD), 60), a(new Color(0x0284C7), 60));
                    birds(g, a(Color.WHITE, 200)); }
            case "sakura_cape" -> { petalsFall(g, 35, 14, a(mix(L, Color.WHITE, 0.2), 190)); branch(g, a(D, 220)); }
            case "storm_cape" -> { rain(g, 36, a(mix(L, Color.WHITE, 0.3), 130)); hem(g, a(Color.BLACK, 80), 12); }
            case "tropic_cape" -> { fronds(g, a(mix(L, D, 0.25), 190)); dot(g, 78, 28, 6, a(new Color(0xFDE68A), 220)); }
            case "dawn_cape" -> { skyBands(g, a(new Color(0xFDE68A), 80), a(new Color(0xFB7185), 70),
                    a(new Color(0x4C1D95), 80)); birds(g, a(Color.WHITE, 180)); }
            case "royal_cape" -> { trim(g, a(new Color(0xFCD34D), 220)); diamondsField(g, 37, a(new Color(0xFCD34D), 70)); }
            case "bloodmoon_cape" -> { sprinkle(g, 38, 16, a(mix(L, Color.WHITE, 0.3), 120), true);
                    glowDot(g, 50, 60, 44, a(mix(L, Color.WHITE, 0.1), 50)); }
            case "glacier_cape" -> { shardsHem(g, a(mix(L, Color.WHITE, 0.4), 180)); sprinkle(g, 39, 10, a(Color.WHITE, 130), true); }
            case "eclipse_cape" -> { sprinkle(g, 40, 18, a(Color.WHITE, 120), true);
                    glowDot(g, 50, 60, 42, a(new Color(0xFCD34D), 40)); }
            case "dragonfire_cape" -> { scales(g, a(D, 160)); embers(g, 41, 8, new Color(0xFDE68A), new Color(0xFB923C)); }
            case "nether_cape" -> { trim(g, a(mix(D, Color.BLACK, 0.2), 220)); sprinkle(g, 42, 12, a(new Color(0xC084FC), 140), true); }
            case "prism_cape" -> rainbowBeams(g);
            case "singularity_cape" -> { streakStars(g, 43, a(Color.WHITE, 150)); glowDot(g, 50, 60, 40, a(L, 55)); }
            case "solar_cape" -> { rays(g, a(new Color(0xFDE047), 110)); embers(g, 44, 6, new Color(0xFFF7CC), new Color(0xFDE047)); }
            case "abyss_cape" -> { godRays(g, a(mix(L, Color.WHITE, 0.2), 50)); bubbles(g, 45, 7, a(L, 120));
                    hem(g, a(Color.BLACK, 110), 20); }
            case "spectral_cape" -> { ribbon(g, 28, a(mix(L, Color.WHITE, 0.4), 60)); ribbon(g, 70, a(mix(L, Color.WHITE, 0.4), 50));
                    sprinkle(g, 46, 10, a(Color.WHITE, 140), true); }
            default -> { }
        }
    }

    // ── scenery helpers ──────────────────────────────────────────────────

    /** True if a point sits in the emblem zone (kept clear of scatter). */
    private static boolean inEmblem(double x, double y) {
        double dx = (x - 50) / 32.0, dy = (y - 62) / 38.0;
        return dx * dx + dy * dy < 1;
    }

    /** Scattered tiny stars/dots (skips the emblem area when asked to). */
    private static void sprinkle(Graphics2D g, long seed, int n, Color c, boolean sparkles) {
        java.util.Random r = new java.util.Random(seed);
        for (int i = 0; i < n; i++) {
            double x = 7 + r.nextDouble() * 86, y = 7 + r.nextDouble() * 146;
            if (inEmblem(x, y)) continue;
            double s = 0.8 + r.nextDouble() * 1.4;
            if (sparkles && i % 4 == 0) sparkle(g, x, y, s * 2.0, c);
            else dot(g, x, y, s, c);
        }
    }

    /** A soft vertical light beam behind the emblem. */
    private static void beam(Graphics2D g, Color c) {
        g.setPaint(new LinearGradientPaint(new Point2D.Double(0, 8), new Point2D.Double(0, 120),
                new float[]{0f, 1f}, new Color[]{c, a(c, 0)}));
        g.fillRect(36, 8, 28, 112);
    }

    /** A translucent band across the bottom hem (with a thin top edge line). */
    private static void hem(Graphics2D g, Color c, double height) {
        g.setColor(c);
        g.fillRect(0, (int) (160 - height), 100, (int) height);
        g.setColor(a(Color.WHITE, 26));
        g.fill(new java.awt.geom.Rectangle2D.Double(0, 160 - height, 100, 1.2));
    }

    /** A diagonal sash across the upper cape. */
    private static void sash(Graphics2D g, Color c) {
        g.setColor(c);
        g.fill(P(0, 26, 100, 8, 100, 20, 0, 38));
    }

    /** Layered filled waves rising from the hem. */
    private static void waveHem(Graphics2D g, Color c1, Color c2, Color c3) {
        Color[] cs = {a(c1, 120), a(c2, 150), a(c3, 190)};
        double[] ys = {118, 130, 142};
        for (int i = 0; i < 3; i++) {
            Path2D w = new Path2D.Double();
            w.moveTo(0, ys[i]);
            for (int s = 0; s < 5; s++) {
                double x0 = s * 25, mid = x0 + 12.5;
                w.quadTo(mid, ys[i] + (s % 2 == 0 ? -8 : 8), x0 + 25, ys[i]);
            }
            w.lineTo(100, 160); w.lineTo(0, 160); w.closePath();
            g.setColor(cs[i]);
            g.fill(w);
        }
    }

    private static void bubbles(Graphics2D g, long seed, int n, Color c) {
        java.util.Random r = new java.util.Random(seed);
        stroke(g, 1.2);
        g.setColor(c);
        for (int i = 0; i < n; i++) {
            double x = 10 + r.nextDouble() * 80, y = 96 + r.nextDouble() * 52, s = 1.2 + r.nextDouble() * 2.2;
            g.draw(new Ellipse2D.Double(x - s, y - s, s * 2, s * 2));
        }
    }

    /** Full-cape horizontal colour wash (kept translucent so the base shows through). */
    private static void skyBands(Graphics2D g, Color a, Color b, Color c) {
        g.setPaint(new LinearGradientPaint(new Point2D.Double(0, 0), new Point2D.Double(0, 160),
                new float[]{0f, 0.5f, 1f}, new Color[]{a, b, c}));
        g.fillRect(0, 0, 100, 160);
    }

    /** Dark rolling hills along the hem. */
    private static void hills(Graphics2D g, Color c) {
        Path2D h = new Path2D.Double();
        h.moveTo(0, 140);
        h.quadTo(25, 124, 50, 138);
        h.quadTo(75, 150, 100, 132);
        h.lineTo(100, 160); h.lineTo(0, 160); h.closePath();
        g.setColor(c);
        g.fill(h);
    }

    /** A soft vertical aurora ribbon at x. */
    private static void ribbon(Graphics2D g, double x, Color c) {
        stroke(g, 9);
        g.setColor(c);
        Path2D r = new Path2D.Double();
        r.moveTo(x, 12);
        r.quadTo(x - 9, 52, x + 2, 92);
        r.quadTo(x + 8, 122, x - 4, 150);
        g.draw(r);
    }

    /** A small connected constellation. */
    private static void constellation(Graphics2D g, long seed, Color c) {
        java.util.Random r = new java.util.Random(seed);
        double px = 0, py = 0;
        stroke(g, 0.9);
        for (int i = 0; i < 6; i++) {
            double x = 12 + r.nextDouble() * 76, y = 10 + r.nextDouble() * 50;
            if (inEmblem(x, y)) continue;
            dot(g, x, y, 1.4, c);
            if (px != 0) { g.setColor(a(c, 80)); g.draw(new java.awt.geom.Line2D.Double(px, py, x, y)); }
            px = x; py = y;
        }
    }

    /** Rising ember specks in two tones. */
    private static void embers(Graphics2D g, long seed, int n, Color bright, Color warm) {
        java.util.Random r = new java.util.Random(seed);
        for (int i = 0; i < n; i++) {
            double x = 10 + r.nextDouble() * 80, y = 90 + r.nextDouble() * 60;
            if (inEmblem(x, y)) continue;
            dot(g, x, y, 0.9 + r.nextDouble() * 1.3, a(i % 3 == 0 ? bright : warm, 120 + r.nextInt(100)));
        }
    }

    /** Falling petals scattered over the cape. */
    private static void petalsFall(Graphics2D g, long seed, int n, Color c) {
        java.util.Random r = new java.util.Random(seed);
        g.setColor(c);
        for (int i = 0; i < n; i++) {
            double x = 8 + r.nextDouble() * 84, y = 8 + r.nextDouble() * 144;
            if (inEmblem(x, y)) continue;
            g.fill(petal(x, y, 4.5 + r.nextDouble() * 2, 2.6, r.nextDouble() * 360));
        }
    }

    private static void leavesFall(Graphics2D g, long seed, int n, Color c) {
        petalsFall(g, seed, n, c);
    }

    private static void berriesFall(Graphics2D g, long seed, Color c, Color shine) {
        java.util.Random r = new java.util.Random(seed);
        for (int i = 0; i < 7; i++) {
            double x = 10 + r.nextDouble() * 80, y = 10 + r.nextDouble() * 140;
            if (inEmblem(x, y)) continue;
            dot(g, x, y, 2.6, c);
            dot(g, x - 0.8, y - 0.8, 0.8, shine);
        }
    }

    /** Slime drips running from the top edge. */
    private static void dripsTop(Graphics2D g, long seed, Color c) {
        java.util.Random r = new java.util.Random(seed);
        g.setColor(c);
        g.fillRect(0, 0, 100, 10);
        for (int i = 0; i < 6; i++) {
            double x = 8 + i * 16 + r.nextDouble() * 6, len = 10 + r.nextDouble() * 22;
            g.fill(new RoundRectangle2D.Double(x - 2, 8, 4, len, 4, 4));
            dot(g, x, 8 + len, 2.6, c);
        }
    }

    /** Two thin vertical pinstripes. */
    private static void pinstripes(Graphics2D g, double x, Color c) {
        g.setColor(c);
        g.fill(new java.awt.geom.Rectangle2D.Double(x, 6, 1.6, 148));
        g.fill(new java.awt.geom.Rectangle2D.Double(x + 5, 6, 0.9, 148));
    }

    /** A faint diamond lattice over the whole cape. */
    private static void lattice(Graphics2D g, Color c) {
        stroke(g, 0.8);
        g.setColor(c);
        for (int i = -8; i < 10; i++) {
            g.draw(new java.awt.geom.Line2D.Double(i * 14, 0, i * 14 + 70, 160));
            g.draw(new java.awt.geom.Line2D.Double(i * 14 + 70, 0, i * 14, 160));
        }
    }

    /** A faint honeycomb lattice. */
    private static void hexLattice(Graphics2D g, Color c) {
        stroke(g, 1.0);
        g.setColor(c);
        for (int row = 0; row < 8; row++) {
            for (int col = 0; col < 6; col++) {
                double cx = 8 + col * 17 + (row % 2 == 0 ? 0 : 8.5), cy = 10 + row * 20;
                if (inEmblem(cx, cy)) continue;
                g.draw(hexagon(cx, cy, 8));
            }
        }
    }

    /** Jagged ice shards rising from the hem. */
    private static void shardsHem(Graphics2D g, Color c) {
        g.setColor(c);
        for (int i = 0; i < 6; i++) {
            double x = 4 + i * 17;
            g.fill(P(x, 160, x + 7, 138 - (i % 2) * 8, x + 14, 160));
        }
    }

    /** Dune bands along the hem. */
    private static void dunesHem(Graphics2D g, Color c1, Color c2) {
        Path2D d1 = new Path2D.Double();
        d1.moveTo(0, 132); d1.quadTo(30, 120, 60, 132); d1.quadTo(82, 140, 100, 130);
        d1.lineTo(100, 160); d1.lineTo(0, 160); d1.closePath();
        g.setColor(c1); g.fill(d1);
        Path2D d2 = new Path2D.Double();
        d2.moveTo(0, 148); d2.quadTo(40, 138, 100, 150);
        d2.lineTo(100, 160); d2.lineTo(0, 160); d2.closePath();
        g.setColor(c2); g.fill(d2);
    }

    /** Flame tongues licking up from the hem. */
    private static void flameHem(Graphics2D g, Color bright, Color warm) {
        for (int i = 0; i < 5; i++) {
            double x = 10 + i * 20;
            Path2D f = new Path2D.Double();
            f.moveTo(x - 8, 160);
            f.quadTo(x - 4, 134 - (i % 2) * 10, x, 124 - (i % 2) * 8);
            f.quadTo(x + 4, 136, x + 8, 160);
            f.closePath();
            g.setColor(a(warm, 180)); g.fill(f);
            Path2D in = new Path2D.Double();
            in.moveTo(x - 4, 160);
            in.quadTo(x, 142, x, 138);
            in.quadTo(x + 2, 146, x + 4, 160);
            in.closePath();
            g.setColor(a(bright, 200)); g.fill(in);
        }
    }

    /** A couple of distant birds. */
    private static void birds(Graphics2D g, Color c) {
        stroke(g, 1.6);
        g.setColor(c);
        double[][] pos = {{26, 26}, {38, 20}, {70, 30}};
        for (double[] b : pos) {
            Path2D bird = new Path2D.Double();
            bird.moveTo(b[0] - 4, b[1]); bird.quadTo(b[0] - 1, b[1] - 3.4, b[0], b[1]);
            bird.moveTo(b[0], b[1]);     bird.quadTo(b[0] + 3, b[1] - 3.4, b[0] + 4, b[1]);
            g.draw(bird);
        }
    }

    /** A blossom branch reaching in from the top-left corner. */
    private static void branch(Graphics2D g, Color c) {
        stroke(g, 2.4);
        g.setColor(c);
        Path2D b = new Path2D.Double();
        b.moveTo(-2, 10);
        b.quadTo(18, 16, 34, 12);
        g.draw(b);
        stroke(g, 1.6);
        g.draw(new java.awt.geom.Line2D.Double(16, 14, 22, 22));
        g.draw(new java.awt.geom.Line2D.Double(28, 13, 33, 20));
    }

    /** Diagonal rain streaks over the upper cape. */
    private static void rain(Graphics2D g, long seed, Color c) {
        java.util.Random r = new java.util.Random(seed);
        stroke(g, 1.1);
        g.setColor(c);
        for (int i = 0; i < 16; i++) {
            double x = 6 + r.nextDouble() * 88, y = 6 + r.nextDouble() * 120, len = 5 + r.nextDouble() * 5;
            if (inEmblem(x, y)) continue;
            g.draw(new java.awt.geom.Line2D.Double(x, y, x - len * 0.35, y + len));
        }
    }

    /** Palm fronds reaching in from the bottom corners. */
    private static void fronds(Graphics2D g, Color c) {
        stroke(g, 2.6);
        g.setColor(c);
        for (int side = 0; side < 2; side++) {
            double bx = side == 0 ? 2 : 98, dir = side == 0 ? 1 : -1;
            for (int i = 0; i < 3; i++) {
                Path2D f = new Path2D.Double();
                f.moveTo(bx, 158);
                f.quadTo(bx + dir * (10 + i * 8), 138 - i * 8, bx + dir * (22 + i * 10), 128 - i * 12);
                g.draw(f);
            }
        }
    }

    /** A thin gold trim border with corner studs. */
    private static void trim(Graphics2D g, Color c) {
        stroke(g, 2.2);
        g.setColor(c);
        g.draw(new RoundRectangle2D.Double(4, 4, 92, 152, 10, 10));
        dot(g, 9, 9, 1.8, c);
        dot(g, 91, 9, 1.8, c);
        dot(g, 9, 151, 1.8, c);
        dot(g, 91, 151, 1.8, c);
    }

    /** A loose field of tiny diamonds. */
    private static void diamondsField(Graphics2D g, long seed, Color c) {
        java.util.Random r = new java.util.Random(seed);
        g.setColor(c);
        for (int i = 0; i < 10; i++) {
            double x = 12 + r.nextDouble() * 76, y = 12 + r.nextDouble() * 136;
            if (inEmblem(x, y)) continue;
            g.fill(P(x, y - 2.4, x + 1.8, y, x, y + 2.4, x - 1.8, y));
        }
    }

    /** Overlapping scale arcs along the lower cape. */
    private static void scales(Graphics2D g, Color c) {
        stroke(g, 1.4);
        g.setColor(c);
        for (int row = 0; row < 4; row++) {
            double y = 112 + row * 11;
            for (int col = 0; col < 7; col++) {
                double x = (row % 2 == 0 ? 4 : 11) + col * 14;
                g.draw(new java.awt.geom.Arc2D.Double(x, y, 14, 12, 180, 180, java.awt.geom.Arc2D.OPEN));
            }
        }
    }

    /** Translucent rainbow beams sweeping diagonally across the cape. */
    private static void rainbowBeams(Graphics2D g) {
        Color[] cs = {new Color(0xF87171), new Color(0xFB923C), new Color(0xFDE047),
                new Color(0x4ADE80), new Color(0x60A5FA), new Color(0xA78BFA)};
        AffineTransform t = g.getTransform();
        g.rotate(Math.toRadians(-24), 50, 80);
        for (int i = 0; i < cs.length; i++) {
            g.setColor(a(cs[i], 46));
            g.fill(new java.awt.geom.Rectangle2D.Double(-30, 28 + i * 16, 160, 11));
        }
        g.setTransform(t);
    }

    /** Stars with little streaks pulled toward the centre (gravity!). */
    private static void streakStars(Graphics2D g, long seed, Color c) {
        java.util.Random r = new java.util.Random(seed);
        stroke(g, 1.0);
        for (int i = 0; i < 16; i++) {
            double x = 8 + r.nextDouble() * 84, y = 8 + r.nextDouble() * 144;
            if (inEmblem(x, y)) continue;
            double dx = 50 - x, dy = 60 - y, len = Math.hypot(dx, dy);
            dot(g, x, y, 1.0 + r.nextDouble(), c);
            g.setColor(a(c, 80));
            g.draw(new java.awt.geom.Line2D.Double(x, y, x + dx / len * 6, y + dy / len * 6));
        }
    }

    /** Long rays emanating from the emblem. */
    private static void rays(Graphics2D g, Color c) {
        stroke(g, 1.8);
        g.setColor(c);
        for (int i = 0; i < 12; i++) {
            double ang = Math.toRadians(i * 30 + 15);
            g.draw(new java.awt.geom.Line2D.Double(
                    50 + Math.cos(ang) * 26, 60 + Math.sin(ang) * 26,
                    50 + Math.cos(ang) * (38 + (i % 2) * 8), 60 + Math.sin(ang) * (38 + (i % 2) * 8)));
        }
    }

    /** Soft god-rays slanting down from the top. */
    private static void godRays(Graphics2D g, Color c) {
        for (int i = 0; i < 3; i++) {
            double x = 22 + i * 24;
            g.setPaint(new LinearGradientPaint(new Point2D.Double(0, 0), new Point2D.Double(0, 110),
                    new float[]{0f, 1f}, new Color[]{c, a(c, 0)}));
            g.fill(P(x, 0, x + 9, 0, x + 20, 110, x + 6, 110));
        }
    }

    // ── per-cape emblems ─────────────────────────────────────────────────

    private static void motif(Graphics2D g, String id, Color top, Color bot) {
        Color L  = mix(top, Color.WHITE, 0.55);     // light accent
        Color L2 = mix(top, Color.WHITE, 0.25);
        Color D  = mix(bot, Color.BLACK, 0.35);     // deep accent
        switch (id) {
            case "lumina_cape" -> crystal(g);
            case "emerald_cape" -> gemOctagon(g, L, L2, D);
            case "crimson_cape" -> chevrons(g, L, D);
            case "ocean_cape" -> waves(g, L, L2);
            case "sunset_cape" -> sunset(g, L, D);
            case "void_cape" -> voidRing(g);
            case "aurora_cape" -> auroraRibbons(g);
            case "galaxy_cape" -> galaxy(g, L);
            case "phoenix_cape" -> phoenix(g);
            case "midnight_cape" -> crescent(g, L);
            case "rose_cape" -> rose(g, L, L2, D);
            case "amber_cape" -> amberDrop(g, L, D);
            case "frost_cape" -> snowflake(g, L);
            case "venom_cape" -> drips(g, L, L2);
            case "celestial_cape" -> celestial(g, L);
            case "slate_cape" -> stripes(g, L2);
            case "moss_cape" -> leaf(g, L, D);
            case "sand_cape" -> dunes(g, L, D);
            case "coal_cape" -> coal(g, L2, D);
            case "lavender_cape" -> sprig(g, L, L2, D);
            case "mint_cape" -> twinLeaves(g, L, D);
            case "honey_cape" -> honeycomb(g, L, D);
            case "berry_cape" -> berries(g, L, L2, D);
            case "flame_cape" -> flame(g, L);
            case "sky_cape" -> skyScene(g, L);
            case "sakura_cape" -> blossom(g, L, D);
            case "storm_cape" -> storm(g, L2);
            case "tropic_cape" -> palmLeaf(g, L, D);
            case "dawn_cape" -> dawn(g, L);
            case "royal_cape" -> crown(g, D);
            case "bloodmoon_cape" -> bloodmoon(g, L, D);
            case "glacier_cape" -> iceberg(g, L);
            case "eclipse_cape" -> eclipse(g, L);
            case "dragonfire_cape" -> dragonEye(g, L, D);
            case "nether_cape" -> portal(g, L, D);
            case "prism_cape" -> prism(g);
            case "singularity_cape" -> vortex(g, L);
            case "solar_cape" -> sun(g);
            case "abyss_cape" -> abyss(g, L);
            case "spectral_cape" -> wisp(g, L);
            default -> crystal(g);
        }
    }

    // ── individual emblems (centred around 50,60 in the 100×160 space) ───

    /** The faceted Lumina crystal cluster (signature cape). */
    private static void crystal(Graphics2D g) {
        Color deep = new Color(0x5B21B6), mid = new Color(0x7C3AED),
              light = new Color(0xA78BFA), hi = new Color(0xDDD6FE), out = new Color(0x2E0A55);
        // Cluster coords from the logo (x18..82, y2..96) mapped into x28..72, y32..92.
        java.util.function.DoubleUnaryOperator X = v -> 28 + (v - 18) * 44.0 / 64;
        java.util.function.DoubleUnaryOperator Y = v -> 32 + (v - 2) * 60.0 / 94;
        java.util.function.Function<double[], Path2D> p = c -> {
            Path2D path = new Path2D.Double();
            path.moveTo(X.applyAsDouble(c[0]), Y.applyAsDouble(c[1]));
            for (int i = 2; i < c.length; i += 2) path.lineTo(X.applyAsDouble(c[i]), Y.applyAsDouble(c[i + 1]));
            path.closePath();
            return path;
        };
        glowDot(g, 50, 60, 30, a(light, 70));
        g.setColor(deep);  g.fill(p.apply(new double[]{24, 46, 36, 58, 30, 96, 18, 92}));
        g.setColor(light); g.fill(p.apply(new double[]{24, 46, 30, 96, 30, 64}));
        g.setColor(mid);   g.fill(p.apply(new double[]{70, 54, 82, 64, 76, 94, 66, 86}));
        g.setColor(mid);   g.fill(p.apply(new double[]{50, 2, 74, 34, 60, 92, 40, 92, 26, 34}));
        g.setColor(deep);  g.fill(p.apply(new double[]{50, 2, 26, 34, 40, 92, 50, 46}));
        g.setColor(light); g.fill(p.apply(new double[]{50, 2, 74, 34, 60, 92, 50, 46}));
        g.setColor(hi);    g.fill(p.apply(new double[]{50, 2, 44, 30, 50, 46, 53, 28}));
        g.setColor(out);
        stroke(g, 1.6);
        g.draw(p.apply(new double[]{50, 2, 74, 34, 60, 92, 40, 92, 26, 34}));
    }

    private static void gemOctagon(Graphics2D g, Color L, Color L2, Color D) {
        Path2D outer = P(40, 40, 60, 40, 70, 50, 70, 72, 60, 82, 40, 82, 30, 72, 30, 50);
        Path2D inner = P(44, 48, 56, 48, 62, 54, 62, 68, 56, 74, 44, 74, 38, 68, 38, 54);
        glowDot(g, 50, 61, 28, a(L, 60));
        g.setColor(L2); g.fill(outer);
        g.setColor(L);  g.fill(inner);
        g.setColor(D);  stroke(g, 1.8); g.draw(outer); g.draw(inner);
        sparkle(g, 44, 52, 4, Color.WHITE);
    }

    private static void chevrons(Graphics2D g, Color L, Color D) {
        stroke(g, 6);
        for (int i = 0; i < 3; i++) {
            double y = 44 + i * 16;
            Path2D v = new Path2D.Double();
            v.moveTo(30, y); v.lineTo(50, y + 12); v.lineTo(70, y);
            g.setColor(i == 1 ? mix(L, Color.WHITE, 0.3) : a(L, 220));
            g.draw(v);
        }
        g.setColor(a(D, 120));
    }

    private static void waves(Graphics2D g, Color L, Color L2) {
        stroke(g, 4);
        for (int i = 0; i < 3; i++) {
            double y = 50 + i * 13;
            Path2D w = new Path2D.Double();
            w.moveTo(24, y);
            w.quadTo(33, y - 9, 42, y);
            w.quadTo(51, y + 9, 60, y);
            w.quadTo(69, y - 9, 78, y);
            g.setColor(i == 0 ? Color.WHITE : i == 1 ? L : L2);
            g.draw(w);
        }
        dot(g, 70, 42, 1.8, a(Color.WHITE, 200));
        dot(g, 28, 80, 1.4, a(Color.WHITE, 160));
    }

    private static void sunset(Graphics2D g, Color L, Color D) {
        Color sun = mix(L, Color.WHITE, 0.35);
        glowDot(g, 50, 66, 26, a(sun, 90));
        g.setColor(sun);
        g.fill(new java.awt.geom.Arc2D.Double(36, 52, 28, 28, 0, 180, java.awt.geom.Arc2D.CHORD));
        stroke(g, 2.4);
        g.setColor(a(D, 200));
        g.draw(new java.awt.geom.Line2D.Double(26, 66.5, 74, 66.5));
        g.setColor(a(sun, 200));
        for (int i = 0; i < 5; i++) {                       // rays
            double ang = Math.toRadians(20 + i * 35);
            g.draw(new java.awt.geom.Line2D.Double(
                    50 + Math.cos(ang) * 18, 66 - Math.sin(ang) * 18,
                    50 + Math.cos(ang) * 24, 66 - Math.sin(ang) * 24));
        }
        stroke(g, 2);                                        // reflection dashes
        for (int i = 0; i < 3; i++) {
            double y = 72 + i * 5, half = 10 - i * 3;
            g.draw(new java.awt.geom.Line2D.Double(50 - half, y, 50 + half, y));
        }
    }

    private static void voidRing(Graphics2D g) {
        glowDot(g, 50, 60, 30, a(new Color(0xA78BFA), 60));
        g.setColor(new Color(0x05050A));
        g.fill(new Ellipse2D.Double(33, 43, 34, 34));
        stroke(g, 2.6);
        g.setColor(new Color(0xC4B5FD));
        g.draw(new Ellipse2D.Double(31, 41, 38, 38));
        sparkle(g, 70, 44, 3, a(Color.WHITE, 220));
        sparkle(g, 32, 76, 2.2, a(Color.WHITE, 160));
    }

    private static void auroraRibbons(Graphics2D g) {
        Color[] cs = {new Color(0x5EEAD4), new Color(0xA78BFA), new Color(0x6EE7B7)};
        stroke(g, 7);
        for (int i = 0; i < 3; i++) {
            double x = 36 + i * 14;
            Path2D r = new Path2D.Double();
            r.moveTo(x, 36);
            r.quadTo(x - 8, 56, x, 72);
            r.quadTo(x + 7, 84, x - 3, 96);
            g.setColor(a(cs[i], 165));
            g.draw(r);
        }
    }

    private static void galaxy(Graphics2D g, Color L) {
        AffineTransform t = g.getTransform();
        g.rotate(Math.toRadians(-18), 50, 60);
        g.setPaint(new RadialGradientPaint(new Point2D.Double(50, 60), 28,
                new float[]{0f, 0.5f, 1f},
                new Color[]{a(Color.WHITE, 130), a(L, 90), a(L, 0)},
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fill(new Ellipse2D.Double(22, 49, 56, 22));
        g.setColor(a(Color.WHITE, 200));
        g.fill(new Ellipse2D.Double(45, 55, 10, 10));
        stroke(g, 1.8);
        g.setColor(a(L, 170));
        g.draw(new java.awt.geom.Arc2D.Double(26, 50, 48, 20, 30, 130, java.awt.geom.Arc2D.OPEN));
        g.draw(new java.awt.geom.Arc2D.Double(26, 50, 48, 20, 210, 130, java.awt.geom.Arc2D.OPEN));
        g.setTransform(t);
        java.util.Random rng = new java.util.Random(9);
        for (int i = 0; i < 14; i++) {
            dot(g, 28 + rng.nextDouble() * 44, 44 + rng.nextDouble() * 34,
                    0.7 + rng.nextDouble(), a(Color.WHITE, 90 + rng.nextInt(120)));
        }
    }

    private static void phoenix(Graphics2D g) {
        Color gold = new Color(0xFDE68A), fire = new Color(0xFB923C);
        glowDot(g, 50, 60, 30, a(fire, 70));
        // Two upswept wings.
        Path2D wing = new Path2D.Double();
        wing.moveTo(50, 64);
        wing.quadTo(30, 58, 24, 40);
        wing.quadTo(36, 50, 44, 50);
        wing.quadTo(38, 44, 36, 36);
        wing.quadTo(46, 48, 50, 56);
        wing.closePath();
        g.setColor(gold); g.fill(wing);
        AffineTransform t = g.getTransform();
        g.translate(100, 0); g.scale(-1, 1);
        g.fill(wing);
        g.setTransform(t);
        // Body + tail flame.
        g.setColor(fire);
        g.fill(new Ellipse2D.Double(46, 52, 8, 14));
        Path2D tail = new Path2D.Double();
        tail.moveTo(50, 66);
        tail.quadTo(46, 80, 52, 92);
        tail.quadTo(52, 80, 56, 74);
        tail.closePath();
        g.fill(tail);
        dot(g, 50, 50, 3.2, gold);                          // head
    }

    private static void crescent(Graphics2D g, Color L) {
        Color moon = mix(L, new Color(0xFDE68A), 0.3);
        glowDot(g, 48, 58, 26, a(moon, 70));
        Area c = new Area(new Ellipse2D.Double(36, 44, 28, 28));
        c.subtract(new Area(new Ellipse2D.Double(43, 41, 28, 28)));
        g.setColor(moon);
        g.fill(c);
        sparkle(g, 64, 48, 3, a(Color.WHITE, 230));
        sparkle(g, 68, 66, 2, a(Color.WHITE, 180));
        sparkle(g, 34, 76, 2.4, a(Color.WHITE, 200));
    }

    private static void rose(Graphics2D g, Color L, Color L2, Color D) {
        stroke(g, 2.6);
        g.setColor(L);
        g.draw(new java.awt.geom.Arc2D.Double(42, 48, 16, 16, 0, 300, java.awt.geom.Arc2D.OPEN));
        g.draw(new java.awt.geom.Arc2D.Double(45, 51, 10, 10, 90, 300, java.awt.geom.Arc2D.OPEN));
        g.setColor(L2);
        g.draw(new java.awt.geom.Arc2D.Double(38, 44, 24, 24, 40, 250, java.awt.geom.Arc2D.OPEN));
        // Leaves.
        g.setColor(mix(new Color(0x22C55E), D, 0.3));
        g.fill(petal(40, 74, 9, 4, -35));
        g.fill(petal(60, 74, 9, 4, 35));
        stroke(g, 2.2);
        g.draw(new java.awt.geom.Line2D.Double(50, 68, 50, 86));
    }

    private static void amberDrop(Graphics2D g, Color L, Color D) {
        glowDot(g, 50, 62, 26, a(L, 70));
        Path2D drop = new Path2D.Double();
        drop.moveTo(50, 40);
        drop.curveTo(62, 56, 66, 66, 60, 76);
        drop.curveTo(55, 84, 45, 84, 40, 76);
        drop.curveTo(34, 66, 38, 56, 50, 40);
        drop.closePath();
        g.setColor(L); g.fill(drop);
        g.setColor(a(D, 200)); stroke(g, 1.8); g.draw(drop);
        dot(g, 46, 58, 3, a(Color.WHITE, 180));
    }

    private static void snowflake(Graphics2D g, Color L) {
        Color ice = mix(L, Color.WHITE, 0.4);
        stroke(g, 2.6);
        g.setColor(ice);
        for (int i = 0; i < 6; i++) {
            AffineTransform t = g.getTransform();
            g.rotate(Math.toRadians(i * 60), 50, 60);
            g.draw(new java.awt.geom.Line2D.Double(50, 60, 50, 38));
            g.draw(new java.awt.geom.Line2D.Double(50, 46, 45, 41));
            g.draw(new java.awt.geom.Line2D.Double(50, 46, 55, 41));
            g.setTransform(t);
        }
        dot(g, 50, 60, 3, Color.WHITE);
    }

    private static void drips(Graphics2D g, Color L, Color L2) {
        g.setColor(L);
        g.fill(new RoundRectangle2D.Double(32, 40, 36, 14, 12, 12));
        double[][] dr = {{40, 54, 24}, {52, 54, 34}, {62, 54, 16}};
        for (double[] d : dr) {
            g.setColor(L2);
            g.fill(new RoundRectangle2D.Double(d[0] - 2.4, d[1], 4.8, d[2], 4.8, 4.8));
            dot(g, d[0], d[1] + d[2], 3.4, L);
        }
        dot(g, 40, 46, 2, a(Color.WHITE, 150));
    }

    private static void celestial(Graphics2D g, Color L) {
        glowDot(g, 50, 58, 28, a(L, 80));
        g.setColor(mix(L, Color.WHITE, 0.4));
        g.fill(star4(50, 58, 18, 5.5));
        stroke(g, 1.6);
        g.setColor(a(L, 150));
        AffineTransform t = g.getTransform();
        g.rotate(Math.toRadians(-22), 50, 58);
        g.draw(new Ellipse2D.Double(26, 50, 48, 16));
        g.setTransform(t);
        sparkle(g, 32, 44, 2.6, a(Color.WHITE, 220));
        sparkle(g, 70, 74, 2.6, a(Color.WHITE, 200));
        sparkle(g, 66, 40, 2, a(Color.WHITE, 170));
    }

    private static void stripes(Graphics2D g, Color L2) {
        g.setColor(a(mix(L2, Color.WHITE, 0.25), 220));
        g.fill(new RoundRectangle2D.Double(30, 54, 40, 5, 5, 5));
        g.setColor(a(L2, 190));
        g.fill(new RoundRectangle2D.Double(30, 64, 40, 5, 5, 5));
    }

    private static void leaf(Graphics2D g, Color L, Color D) {
        g.setColor(L);
        g.fill(petal(50, 60, 24, 11, 18));
        g.setColor(a(D, 220));
        stroke(g, 1.8);
        AffineTransform t = g.getTransform();
        g.rotate(Math.toRadians(18), 50, 60);
        g.draw(new java.awt.geom.Line2D.Double(50, 40, 50, 80));
        for (int i = 0; i < 3; i++) {
            double y = 48 + i * 9;
            g.draw(new java.awt.geom.Line2D.Double(50, y + 4, 43, y));
            g.draw(new java.awt.geom.Line2D.Double(50, y + 4, 57, y));
        }
        g.setTransform(t);
    }

    private static void dunes(Graphics2D g, Color L, Color D) {
        dot(g, 64, 44, 6, mix(L, Color.WHITE, 0.4));
        stroke(g, 4);
        g.setColor(L);
        Path2D d1 = new Path2D.Double();
        d1.moveTo(24, 70); d1.quadTo(42, 56, 60, 68); d1.quadTo(70, 74, 78, 70);
        g.draw(d1);
        g.setColor(a(D, 200));
        Path2D d2 = new Path2D.Double();
        d2.moveTo(26, 82); d2.quadTo(46, 72, 64, 80);
        g.draw(d2);
    }

    private static void coal(Graphics2D g, Color L2, Color D) {
        Path2D lump = P(42, 44, 62, 46, 70, 60, 62, 76, 44, 78, 32, 64, 34, 50);
        g.setColor(mix(D, Color.BLACK, 0.2)); g.fill(lump);
        g.setColor(a(L2, 130));
        g.fill(P(42, 44, 54, 46, 48, 60, 36, 54));
        g.fill(P(56, 60, 66, 62, 60, 74, 50, 70));
        sparkle(g, 46, 52, 2, a(Color.WHITE, 170));
    }

    private static void sprig(Graphics2D g, Color L, Color L2, Color D) {
        stroke(g, 2.2);
        g.setColor(mix(new Color(0x22C55E), D, 0.35));
        Path2D stem = new Path2D.Double();
        stem.moveTo(50, 88); stem.quadTo(48, 66, 50, 44);
        g.draw(stem);
        g.setColor(L);
        for (int i = 0; i < 5; i++) {
            double y = 46 + i * 8;
            g.fill(petal(45.5 - i * 0.3, y, 4.6, 2.6, -28));
            g.fill(petal(54.5 + i * 0.3, y + 4, 4.6, 2.6, 28));
        }
        g.setColor(L2);
        g.fill(petal(50, 40, 5, 3, 0));
    }

    private static void twinLeaves(Graphics2D g, Color L, Color D) {
        g.setColor(L);
        g.fill(petal(42, 60, 18, 8, -30));
        g.fill(petal(58, 60, 18, 8, 30));
        g.setColor(a(D, 200));
        stroke(g, 1.6);
        AffineTransform t = g.getTransform();
        g.rotate(Math.toRadians(-30), 42, 60);
        g.draw(new java.awt.geom.Line2D.Double(42, 46, 42, 74));
        g.setTransform(t);
        g.rotate(Math.toRadians(30), 58, 60);
        g.draw(new java.awt.geom.Line2D.Double(58, 46, 58, 74));
        g.setTransform(t);
    }

    private static void honeycomb(Graphics2D g, Color L, Color D) {
        stroke(g, 2.2);
        double r = 8;
        double[][] centers = {{50, 52}, {50 - r * 1.55, 56.5}, {50 + r * 1.55, 56.5},
                {50 - r * 0.78, 65.5}, {50 + r * 0.78, 65.5}, {50, 74.5}};
        for (int i = 0; i < centers.length; i++) {
            Path2D hexa = hexagon(centers[i][0], centers[i][1], r);
            if (i == 0) { g.setColor(a(L, 220)); g.fill(hexa); }
            g.setColor(mix(L, D, 0.25));
            g.draw(hexa);
        }
        g.setColor(L);
        g.fill(new RoundRectangle2D.Double(48, 82, 4, 9, 4, 4));
        dot(g, 50, 92, 2.6, L);
    }

    private static void berries(Graphics2D g, Color L, Color L2, Color D) {
        g.setColor(mix(new Color(0x22C55E), D, 0.3));
        g.fill(petal(56, 44, 8, 4, 40));
        g.setColor(L2);
        dot(g, 43, 60, 8, L2);
        dot(g, 58, 62, 8, mix(L2, D, 0.25));
        dot(g, 50, 73, 8, mix(L2, Color.WHITE, 0.12));
        dot(g, 40.5, 57, 2.2, a(Color.WHITE, 190));
        dot(g, 55.5, 59, 2.2, a(Color.WHITE, 170));
        dot(g, 47.5, 70, 2.2, a(Color.WHITE, 170));
    }

    private static void flame(Graphics2D g, Color L) {
        glowDot(g, 50, 64, 26, a(L, 80));
        Path2D outer = new Path2D.Double();
        outer.moveTo(50, 38);
        outer.curveTo(60, 50, 66, 60, 62, 72);
        outer.curveTo(59, 82, 41, 82, 38, 72);
        outer.curveTo(34, 60, 42, 54, 44, 46);
        outer.curveTo(46, 52, 48, 44, 50, 38);
        outer.closePath();
        g.setColor(L); g.fill(outer);
        Path2D inner = new Path2D.Double();
        inner.moveTo(50, 54);
        inner.curveTo(56, 62, 57, 68, 54, 74);
        inner.curveTo(52, 78, 48, 78, 46, 74);
        inner.curveTo(43, 68, 47, 60, 50, 54);
        inner.closePath();
        g.setColor(mix(L, Color.WHITE, 0.5)); g.fill(inner);
    }

    private static void skyScene(Graphics2D g, Color L) {
        dot(g, 62, 50, 9, mix(new Color(0xFDE68A), Color.WHITE, 0.2));
        glowDot(g, 62, 50, 16, a(new Color(0xFDE68A), 70));
        Color cloud = a(Color.WHITE, 235);
        g.setColor(cloud);
        dot(g, 40, 64, 8, cloud);
        dot(g, 50, 60, 10, cloud);
        dot(g, 60, 65, 8, cloud);
        g.fill(new RoundRectangle2D.Double(32, 62, 36, 11, 11, 11));
    }

    private static void blossom(Graphics2D g, Color L, Color D) {
        Color petalC = mix(L, Color.WHITE, 0.2);
        for (int i = 0; i < 5; i++) {
            AffineTransform t = g.getTransform();
            g.rotate(Math.toRadians(i * 72), 50, 58);
            g.setColor(petalC);
            g.fill(petal(50, 47.5, 7.5, 5, 0));
            g.setTransform(t);
        }
        dot(g, 50, 58, 3.6, mix(new Color(0xF59E0B), Color.WHITE, 0.3));
        g.setColor(a(petalC, 190));
        g.fill(petal(33, 80, 4.6, 3, 30));
        g.fill(petal(64, 86, 4.6, 3, -20));
        g.fill(petal(45, 92, 4.2, 2.8, 10));
    }

    private static void storm(Graphics2D g, Color L2) {
        Color cloud = mix(L2, Color.WHITE, 0.15);
        g.setColor(cloud);
        dot(g, 41, 50, 8, cloud);
        dot(g, 52, 46, 10, cloud);
        dot(g, 62, 51, 8, cloud);
        g.fill(new RoundRectangle2D.Double(33, 48, 38, 11, 11, 11));
        Path2D bolt = P(52, 60, 44, 74, 50, 74, 45, 90, 58, 71, 51, 71, 57, 60);
        g.setColor(new Color(0xFDE047));
        g.fill(bolt);
    }

    private static void palmLeaf(Graphics2D g, Color L, Color D) {
        stroke(g, 2.4);
        g.setColor(mix(L, D, 0.15));
        Path2D rib = new Path2D.Double();
        rib.moveTo(38, 86); rib.quadTo(50, 62, 64, 42);
        g.draw(rib);
        stroke(g, 3.2);
        g.setColor(L);
        double[][] fronds = {{44, 74, 30, 62}, {48, 66, 35, 52}, {53, 58, 42, 44},
                {58, 50, 50, 38}, {50, 70, 62, 64}, {55, 62, 68, 54}, {60, 54, 73, 47}};
        for (double[] f : fronds) {
            Path2D fr = new Path2D.Double();
            fr.moveTo(f[0], f[1]);
            fr.quadTo((f[0] + f[2]) / 2 - 2, (f[1] + f[3]) / 2 - 4, f[2], f[3]);
            g.draw(fr);
        }
    }

    private static void dawn(Graphics2D g, Color L) {
        Color sun = mix(L, Color.WHITE, 0.3);
        stroke(g, 2.4);
        g.setColor(a(mix(L, Color.BLACK, 0.2), 220));
        g.draw(new java.awt.geom.Line2D.Double(26, 70, 74, 70));
        g.setColor(sun);
        g.fill(new java.awt.geom.Arc2D.Double(38, 58, 24, 24, 0, 180, java.awt.geom.Arc2D.CHORD));
        for (int i = 0; i < 7; i++) {
            double ang = Math.toRadians(12 + i * 26);
            g.draw(new java.awt.geom.Line2D.Double(
                    50 + Math.cos(ang) * 16, 70 - Math.sin(ang) * 16,
                    50 + Math.cos(ang) * 22, 70 - Math.sin(ang) * 22));
        }
        stroke(g, 2);
        Path2D bird = new Path2D.Double();
        bird.moveTo(32, 46); bird.quadTo(35.5, 42, 39, 46);
        bird.moveTo(39, 46); bird.quadTo(42.5, 42, 46, 46);
        g.draw(bird);
    }

    private static void crown(Graphics2D g, Color D) {
        Color gold = new Color(0xFCD34D);
        glowDot(g, 50, 60, 26, a(gold, 60));
        Path2D c = P(32, 72, 32, 52, 41, 62, 50, 44, 59, 62, 68, 52, 68, 72);
        g.setColor(gold); g.fill(c);
        g.setColor(a(D, 220)); stroke(g, 1.8); g.draw(c);
        g.setColor(gold);
        g.fill(new RoundRectangle2D.Double(32, 74, 36, 6, 4, 4));
        g.setColor(a(D, 220)); g.draw(new RoundRectangle2D.Double(32, 74, 36, 6, 4, 4));
        dot(g, 41, 70, 2, new Color(0xEF4444));
        dot(g, 50, 68, 2.4, new Color(0x3B82F6));
        dot(g, 59, 70, 2, new Color(0x22C55E));
    }

    private static void bloodmoon(Graphics2D g, Color L, Color D) {
        Color moon = mix(L, Color.WHITE, 0.15);
        glowDot(g, 50, 60, 28, a(moon, 80));
        dot(g, 50, 60, 16, moon);
        Color crater = a(mix(L, D, 0.5), 190);
        dot(g, 44, 55, 3.2, crater);
        dot(g, 55, 63, 2.6, crater);
        dot(g, 48, 68, 2, crater);
        stroke(g, 3);
        g.setColor(a(D, 200));
        g.draw(new java.awt.geom.Line2D.Double(30, 74, 56, 74));
    }

    private static void iceberg(Graphics2D g, Color L) {
        Color ice = mix(L, Color.WHITE, 0.45);
        g.setColor(ice);
        g.fill(P(50, 40, 64, 68, 36, 68));
        g.setColor(a(mix(L, Color.WHITE, 0.2), 150));
        g.fill(P(46, 70, 62, 70, 54, 88));
        stroke(g, 2.2);
        g.setColor(a(Color.WHITE, 200));
        g.draw(new java.awt.geom.Line2D.Double(26, 69, 74, 69));
    }

    private static void eclipse(Graphics2D g, Color L) {
        glowDot(g, 50, 60, 30, a(new Color(0xFCD34D), 90));
        stroke(g, 2.6);
        g.setColor(mix(new Color(0xFCD34D), Color.WHITE, 0.3));
        g.draw(new Ellipse2D.Double(34, 44, 32, 32));
        g.setColor(new Color(0x0B0B12));
        g.fill(new Ellipse2D.Double(35, 45, 30, 30));
        sparkle(g, 64, 46, 3.4, Color.WHITE);
    }

    private static void dragonEye(Graphics2D g, Color L, Color D) {
        Path2D eye = new Path2D.Double();
        eye.moveTo(30, 60);
        eye.quadTo(50, 42, 70, 60);
        eye.quadTo(50, 78, 30, 60);
        eye.closePath();
        glowDot(g, 50, 60, 28, a(L, 80));
        g.setColor(mix(L, Color.WHITE, 0.2)); g.fill(eye);
        g.setColor(a(D, 230)); stroke(g, 2); g.draw(eye);
        Path2D slit = new Path2D.Double();
        slit.moveTo(50, 46);
        slit.quadTo(54, 60, 50, 74);
        slit.quadTo(46, 60, 50, 46);
        slit.closePath();
        g.setColor(mix(D, Color.BLACK, 0.3)); g.fill(slit);
        dot(g, 46, 52, 2, a(Color.WHITE, 190));
    }

    private static void portal(Graphics2D g, Color L, Color D) {
        g.setColor(mix(D, Color.BLACK, 0.35));
        stroke(g, 5);
        g.draw(new RoundRectangle2D.Double(36, 40, 28, 44, 10, 10));
        g.setPaint(new RadialGradientPaint(new Point2D.Double(50, 62), 20,
                new float[]{0f, 1f}, new Color[]{a(L, 210), a(mix(L, D, 0.6), 120)},
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fill(new RoundRectangle2D.Double(39, 43, 22, 38, 8, 8));
        stroke(g, 1.8);
        g.setColor(a(Color.WHITE, 150));
        g.draw(new java.awt.geom.Arc2D.Double(42, 50, 16, 24, 40, 200, java.awt.geom.Arc2D.OPEN));
    }

    private static void prism(Graphics2D g) {
        Color glass = a(Color.WHITE, 220);
        stroke(g, 2.4);
        g.setColor(glass);
        Path2D tri = P(50, 44, 64, 72, 36, 72);
        g.draw(tri);
        g.setColor(a(Color.WHITE, 170));
        g.draw(new java.awt.geom.Line2D.Double(24, 62, 42, 60));
        Color[] rainbow = {new Color(0xF87171), new Color(0xFB923C), new Color(0xFDE047),
                new Color(0x4ADE80), new Color(0x60A5FA)};
        stroke(g, 2.2);
        for (int i = 0; i < rainbow.length; i++) {
            g.setColor(a(rainbow[i], 210));
            g.draw(new java.awt.geom.Line2D.Double(58, 62, 78, 50 + i * 7));
        }
    }

    private static void vortex(Graphics2D g, Color L) {
        glowDot(g, 50, 60, 30, a(L, 90));
        stroke(g, 2.6);
        g.setColor(mix(L, Color.WHITE, 0.25));
        for (int armIdx = 0; armIdx < 3; armIdx++) {
            Path2D arm = new Path2D.Double();
            double start = Math.toRadians(armIdx * 120);
            arm.moveTo(50 + Math.cos(start) * 22, 60 + Math.sin(start) * 22);
            for (int s = 1; s <= 14; s++) {
                double angle = start + s * 0.32;
                double rad = 22 * (1 - s / 15.0);
                arm.lineTo(50 + Math.cos(angle) * rad, 60 + Math.sin(angle) * rad);
            }
            g.draw(arm);
        }
        dot(g, 50, 60, 3.4, Color.WHITE);
    }

    private static void sun(Graphics2D g) {
        Color core = new Color(0xFDE047), hot = new Color(0xFFF7CC);
        glowDot(g, 50, 60, 32, a(core, 110));
        dot(g, 50, 60, 13, mix(core, hot, 0.4));
        g.setColor(core);
        for (int i = 0; i < 8; i++) {
            AffineTransform t = g.getTransform();
            g.rotate(Math.toRadians(i * 45), 50, 60);
            g.fill(P(50, 40, 47, 46, 53, 46));
            g.setTransform(t);
        }
        stroke(g, 2);
        g.setColor(a(hot, 220));
        g.draw(new java.awt.geom.Arc2D.Double(58, 48, 14, 12, 220, 200, java.awt.geom.Arc2D.OPEN));
    }

    private static void abyss(Graphics2D g, Color L) {
        glowDot(g, 50, 56, 20, a(mix(L, Color.WHITE, 0.4), 150));
        dot(g, 50, 56, 3.2, Color.WHITE);
        stroke(g, 1.6);
        g.setColor(a(L, 130));
        for (int i = 0; i < 4; i++) {
            double ang = Math.toRadians(50 + i * 28);
            g.draw(new java.awt.geom.Line2D.Double(
                    50 + Math.cos(ang) * 8, 56 + Math.sin(ang) * 8,
                    50 + Math.cos(ang) * 16, 56 + Math.sin(ang) * 16));
        }
        g.setColor(a(mix(L, Color.WHITE, 0.3), 160));
        g.draw(new Ellipse2D.Double(40, 72, 4, 4));
        g.draw(new Ellipse2D.Double(57, 80, 3, 3));
        g.draw(new Ellipse2D.Double(46, 88, 2.4, 2.4));
    }

    private static void wisp(Graphics2D g, Color L) {
        Color ghost = mix(L, Color.WHITE, 0.45);
        for (int pass = 0; pass < 3; pass++) {
            stroke(g, 7 - pass * 2.2);
            g.setColor(a(ghost, 60 + pass * 60));
            Path2D s = new Path2D.Double();
            s.moveTo(38, 42);
            s.quadTo(58, 50, 50, 62);
            s.quadTo(42, 72, 56, 82);
            s.quadTo(63, 87, 66, 92);
            g.draw(s);
        }
        sparkle(g, 36, 56, 2.4, a(Color.WHITE, 200));
        sparkle(g, 66, 70, 2, a(Color.WHITE, 170));
    }

    // ── drawing helpers ──────────────────────────────────────────────────

    private static Color hex(String s) {
        try { return Color.decode(s.startsWith("#") ? s : "#" + s); }
        catch (Exception e) { return new Color(0x7C3AED); }
    }

    private static Color mix(Color x, Color y, double t) {
        return new Color(
                (int) Math.round(x.getRed() + (y.getRed() - x.getRed()) * t),
                (int) Math.round(x.getGreen() + (y.getGreen() - x.getGreen()) * t),
                (int) Math.round(x.getBlue() + (y.getBlue() - x.getBlue()) * t));
    }

    private static Color a(Color c, int alpha) {
        return new Color(c.getRed(), c.getGreen(), c.getBlue(), Math.max(0, Math.min(255, alpha)));
    }

    private static void stroke(Graphics2D g, double w) {
        g.setStroke(new BasicStroke((float) w, BasicStroke.CAP_ROUND, BasicStroke.JOIN_ROUND));
    }

    private static Path2D P(double... xy) {
        Path2D p = new Path2D.Double();
        p.moveTo(xy[0], xy[1]);
        for (int i = 2; i < xy.length; i += 2) p.lineTo(xy[i], xy[i + 1]);
        p.closePath();
        return p;
    }

    private static void dot(Graphics2D g, double x, double y, double r, Color c) {
        g.setColor(c);
        g.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
    }

    /** A soft radial glow (alpha fades to zero at the edge). */
    private static void glowDot(Graphics2D g, double x, double y, double r, Color c) {
        g.setPaint(new RadialGradientPaint(new Point2D.Double(x, y), (float) r,
                new float[]{0f, 1f}, new Color[]{c, a(c, 0)},
                MultipleGradientPaint.CycleMethod.NO_CYCLE));
        g.fill(new Ellipse2D.Double(x - r, y - r, r * 2, r * 2));
    }

    /** A pointed ellipse (leaf/petal) centred at (cx,cy), rotated by deg. */
    private static java.awt.Shape petal(double cx, double cy, double len, double wid, double deg) {
        Path2D p = new Path2D.Double();
        p.moveTo(0, -len / 2);
        p.quadTo(wid, 0, 0, len / 2);
        p.quadTo(-wid, 0, 0, -len / 2);
        p.closePath();
        AffineTransform t = AffineTransform.getTranslateInstance(cx, cy);
        t.rotate(Math.toRadians(deg));
        return t.createTransformedShape(p);
    }

    /** A 4-point star (concave diamond). */
    private static Path2D star4(double cx, double cy, double r, double waist) {
        Path2D p = new Path2D.Double();
        p.moveTo(cx, cy - r);
        p.quadTo(cx + waist * 0.3, cy - waist * 0.3, cx + r, cy);
        p.quadTo(cx + waist * 0.3, cy + waist * 0.3, cx, cy + r);
        p.quadTo(cx - waist * 0.3, cy + waist * 0.3, cx - r, cy);
        p.quadTo(cx - waist * 0.3, cy - waist * 0.3, cx, cy - r);
        p.closePath();
        return p;
    }

    /** A tiny 4-point sparkle. */
    private static void sparkle(Graphics2D g, double x, double y, double r, Color c) {
        g.setColor(c);
        g.fill(star4(x, y, r, r * 0.8));
    }

    private static Path2D hexagon(double cx, double cy, double r) {
        Path2D p = new Path2D.Double();
        for (int i = 0; i < 6; i++) {
            double ang = Math.toRadians(60 * i - 30);
            double x = cx + Math.cos(ang) * r, y = cy + Math.sin(ang) * r;
            if (i == 0) p.moveTo(x, y); else p.lineTo(x, y);
        }
        p.closePath();
        return p;
    }
}
