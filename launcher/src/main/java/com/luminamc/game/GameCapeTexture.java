package com.luminamc.game;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Produces the in-game cape texture for the <b>HD Lumina cape</b> model
 * ({@code net.squxso.lumina.client.LuminaHdCape}).
 *
 * <p>That model is Minecraft's cape box rebuilt 8&times; larger — {@code addBox(80,128,8)}
 * at {@code texOffs(0,0)} on a 256&times;256 atlas — so the back face is 80&times;128 texels
 * instead of vanilla's ~10&times;16. We paint a clean vertical gradient on the two big faces
 * and the full Lumina crystal — big and anti-aliased — on the outer (back) face. The atlas
 * regions below mirror that box's unwrap exactly, so the UVs can't drift.
 */
public final class GameCapeTexture {

    private GameCapeTexture() {}

    // Box unwrap for addBox(... 80,128,8) at texOffs(0,0): depth D, width W, height H.
    private static final int TEX = 256, D = 8, W = 80, H = 128;

    /** Writes the HD cape PNG ({@code topHex → bottomHex} gradient + Lumina crystal) to {@code out}. */
    public static void write(String topHex, String bottomHex, Path out) throws Exception {
        int[] top = rgb(topHex), bot = rgb(bottomHex);
        BufferedImage img = new BufferedImage(TEX, TEX, BufferedImage.TYPE_INT_ARGB);

        // The two big faces: north (u[8,88]) is the outer/back face you see; south (u[96,176])
        // is the inner face. Gradient + crystal on both (orientation-safe).
        paintFace(img, D, D, top, bot);                 // north  v[8,136]
        paintFace(img, 2 * D + W, D, top, bot);         // south  v[8,136]

        // Thin rim faces: a flat mid-tone so the cape edges aren't transparent (→ black).
        int mid = argb(lerp(top[0], bot[0], .5), lerp(top[1], bot[1], .5), lerp(top[2], bot[2], .5));
        fill(img, D, 0, W, D, mid);          // up
        fill(img, D + W, 0, W, D, mid);      // down
        fill(img, 0, D, D, H, mid);          // west
        fill(img, D + W, D, D, H, mid);      // east

        Files.createDirectories(out.getParent());
        ImageIO.write(img, "png", out.toFile());
    }

    // The exact Lumina crystal tones (mirrors CrystalLogo).
    private static final java.awt.Color C_DEEP    = new java.awt.Color(0x5B, 0x21, 0xB6);
    private static final java.awt.Color C_MID     = new java.awt.Color(0x7C, 0x3A, 0xED);
    private static final java.awt.Color C_LIGHT   = new java.awt.Color(0xA7, 0x8B, 0xFA);
    private static final java.awt.Color C_HILITE  = new java.awt.Color(0xDD, 0xD6, 0xFE);
    private static final java.awt.Color C_OUTLINE = new java.awt.Color(0x2E, 0x0A, 0x55);

    private static void paintFace(BufferedImage img, int ox, int oy, int[] top, int[] bot) {
        for (int y = 0; y < H; y++) {
            double f = y / (double) (H - 1);
            int c = argb(lerp(top[0], bot[0], f), lerp(top[1], bot[1], f), lerp(top[2], bot[2], f));
            for (int x = 0; x < W; x++) img.setRGB(ox + x, oy + y, c);
        }
        drawCrystal(img, ox, oy);
    }

    /** Paints the full Lumina crystal cluster (anti-aliased) centred on an 80&times;128 face. */
    private static void drawCrystal(BufferedImage cape, int rx, int ry) {
        java.awt.Graphics2D g = cape.createGraphics();
        g.setRenderingHint(java.awt.RenderingHints.KEY_ANTIALIASING, java.awt.RenderingHints.VALUE_ANTIALIAS_ON);
        // Map cluster coords (x 18..82, y 2..96) into the face with a margin.
        double ox = rx + W * 0.14, oy = ry + H * 0.08, sx = (W * 0.72) / 64.0, sy = (H * 0.84) / 94.0;
        g.setColor(C_DEEP);   g.fill(poly(ox, oy, sx, sy, 24, 46, 36, 58, 30, 96, 18, 92));
        g.setColor(C_LIGHT);  g.fill(poly(ox, oy, sx, sy, 24, 46, 30, 96, 30, 64));
        g.setColor(C_MID);    g.fill(poly(ox, oy, sx, sy, 70, 54, 82, 64, 76, 94, 66, 86));
        g.setColor(C_MID);    g.fill(poly(ox, oy, sx, sy, 50, 2, 74, 34, 60, 92, 40, 92, 26, 34));
        g.setColor(C_DEEP);   g.fill(poly(ox, oy, sx, sy, 50, 2, 26, 34, 40, 92, 50, 46));   // left facet
        g.setColor(C_LIGHT);  g.fill(poly(ox, oy, sx, sy, 50, 2, 74, 34, 60, 92, 50, 46));   // right facet
        g.setColor(C_HILITE); g.fill(poly(ox, oy, sx, sy, 50, 2, 44, 30, 50, 46, 53, 28));   // glint
        g.setColor(C_OUTLINE);
        g.setStroke(new java.awt.BasicStroke(Math.max(1.4f, (float) (W * 0.022))));
        g.draw(poly(ox, oy, sx, sy, 50, 2, 74, 34, 60, 92, 40, 92, 26, 34));
        g.draw(poly(ox, oy, sx, sy, 24, 46, 36, 58, 30, 96, 18, 92));
        g.draw(poly(ox, oy, sx, sy, 70, 54, 82, 64, 76, 94, 66, 86));
        g.dispose();
    }

    private static java.awt.Polygon poly(double ox, double oy, double sx, double sy, double... c) {
        int n = c.length / 2;
        int[] xs = new int[n], ys = new int[n];
        for (int i = 0; i < n; i++) {
            xs[i] = (int) Math.round(ox + (c[2 * i] - 18) * sx);
            ys[i] = (int) Math.round(oy + (c[2 * i + 1] - 2) * sy);
        }
        return new java.awt.Polygon(xs, ys, n);
    }

    private static void fill(BufferedImage img, int x, int y, int w, int h, int c) {
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) img.setRGB(x + i, y + j, c);
    }

    private static int argb(int r, int g, int b) { return 0xFF000000 | (r << 16) | (g << 8) | b; }

    private static int lerp(int a, int b, double f) {
        return Math.max(0, Math.min(255, (int) Math.round(a + (b - a) * f)));
    }

    private static int[] rgb(String hex) {
        int v = Integer.parseInt(hex.replace("#", ""), 16);
        return new int[]{(v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF};
    }
}
