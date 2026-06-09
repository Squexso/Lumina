package com.luminamc.game;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Produces the in-game cape texture (Minecraft cape UV layout) for any equipped cape
 * by <em>recolouring</em> the one proven template ({@code lumina_cape_template.png}).
 *
 * <p>The template is a vertical violet gradient that is uniform left-to-right, with the
 * faceted Lumina crystal painted on top. Recolouring therefore needs no knowledge of the
 * (fiddly) cape UV regions: for every pixel we compare it to that row's pure-gradient
 * colour — matching pixels are fabric (replaced with the new cape's gradient), deviating
 * pixels are the crystal/sheen and are kept as-is (the crystal stays the Lumina emblem on
 * every cape). The layout is byte-for-byte the same as the template that already renders
 * correctly in-game, so the UVs can't break.
 */
public final class GameCapeTexture {

    private GameCapeTexture() {}

    private static BufferedImage template;

    /** Recolours the template to {@code topHex → bottomHex} and writes a PNG to {@code out}. */
    public static void write(String topHex, String bottomHex, Path out) throws Exception {
        BufferedImage tpl = template();
        int w = tpl.getWidth(), h = tpl.getHeight();
        int[] top = rgb(topHex), bot = rgb(bottomHex);

        // A clean smooth top→bottom gradient (no template sheen/noise) + the crystal on top.
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            double f = h <= 1 ? 0 : y / (double) (h - 1);
            int newGrad = 0xFF000000
                    | (lerp(top[0], bot[0], f) << 16)
                    | (lerp(top[1], bot[1], f) << 8)
                    |  lerp(top[2], bot[2], f);
            for (int x = 0; x < w; x++) img.setRGB(x, y, newGrad);
        }
        // Draw a clear, high-contrast Lumina crystal over the cape's back face so the logo
        // reads on EVERY cape colour (the recoloured template crystal washes out, especially
        // on violet capes). Drawn ~2x tall to compensate the cape model's vertical squish.
        drawCrystal(img);

        Files.createDirectories(out.getParent());
        ImageIO.write(img, "png", out.toFile());
    }

    // The exact Lumina crystal tones (mirrors CrystalLogo).
    private static final java.awt.Color C_DEEP   = new java.awt.Color(0x5B, 0x21, 0xB6);
    private static final java.awt.Color C_MID    = new java.awt.Color(0x7C, 0x3A, 0xED);
    private static final java.awt.Color C_LIGHT  = new java.awt.Color(0xA7, 0x8B, 0xFA);
    private static final java.awt.Color C_HILITE = new java.awt.Color(0xDD, 0xD6, 0xFE);

    /**
     * Paints the Lumina crystal (the {@link com.luminamc.ui.components.CrystalLogo} main shard)
     * directly onto the cape's back face, big and CRISP (no anti-aliasing / no smoothing) so it
     * stays sharp and clearly readable on the low-res cape texture.
     */
    private static void drawCrystal(BufferedImage cape) {
        java.awt.Graphics2D g = cape.createGraphics();   // no rendering hints → crisp, hard-edged pixels
        // small lower-left + tiny right crystals (the "cluster" of the Lumina logo)
        g.setColor(C_DEEP);   g.fill(cp(24, 46, 36, 58, 30, 96, 18, 92));
        g.setColor(C_LIGHT);  g.fill(cp(24, 46, 30, 96, 30, 64));
        g.setColor(C_MID);    g.fill(cp(70, 54, 82, 64, 76, 94, 66, 86));
        g.setColor(new java.awt.Color(0x2E, 0x0A, 0x55));
        g.draw(cp(24, 46, 36, 58, 30, 96, 18, 92));
        g.draw(cp(70, 54, 82, 64, 76, 94, 66, 86));
        // main shard + facets
        g.setColor(C_MID);    g.fill(cp(50, 2, 74, 34, 60, 92, 40, 92, 26, 34));
        g.setColor(C_DEEP);   g.fill(cp(50, 2, 26, 34, 40, 92, 50, 46));           // left facet (dark)
        g.setColor(C_LIGHT);  g.fill(cp(50, 2, 74, 34, 60, 92, 50, 46));           // right facet (light)
        g.setColor(C_HILITE); g.fill(cp(50, 2, 44, 30, 50, 46, 53, 28));           // glint
        g.setColor(new java.awt.Color(0x2E, 0x0A, 0x55));                           // bold dark outline
        g.draw(cp(50, 2, 74, 34, 60, 92, 40, 92, 26, 34));
        g.dispose();
    }

    /** Maps full CrystalLogo cluster coords (x 18..82, y 2..96) to fill the cape back (x 1..11, y 2..34). */
    private static java.awt.Polygon cp(double... c) {
        int n = c.length / 2;
        int[] xs = new int[n], ys = new int[n];
        for (int i = 0; i < n; i++) {
            xs[i] = (int) Math.round(1 + (c[2 * i] - 18) * 10.0 / 64);
            ys[i] = (int) Math.round(2 + (c[2 * i + 1] - 2) * 32.0 / 94);
        }
        return new java.awt.Polygon(xs, ys, n);
    }

    private static int lerp(int a, int b, double f) {
        return Math.max(0, Math.min(255, (int) Math.round(a + (b - a) * f)));
    }

    /** True if {@code a} is within a small distance of {@code b} (i.e. plain fabric, not the crystal). */

    private static int[] rgb(String hex) {
        int v = Integer.parseInt(hex.replace("#", ""), 16);
        return new int[]{(v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF};
    }

    private static synchronized BufferedImage template() throws Exception {
        if (template == null) {
            try (InputStream in = GameCapeTexture.class.getResourceAsStream("/lumina_cape_template.png")) {
                if (in == null) throw new IllegalStateException("lumina_cape_template.png missing from resources");
                template = ImageIO.read(in);
            }
        }
        return template;
    }
}
