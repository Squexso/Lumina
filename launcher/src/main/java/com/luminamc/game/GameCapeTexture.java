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
        int gradCol = Math.min(40, w - 1);              // a column that is always pure gradient (crystal is on the left)
        int[] top = rgb(topHex), bot = rgb(bottomHex);

        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        for (int y = 0; y < h; y++) {
            int oldGrad = tpl.getRGB(gradCol, y);       // this row's pure-gradient colour
            double f = h <= 1 ? 0 : y / (double) (h - 1);
            int newGrad = 0xFF000000
                    | (lerp(top[0], bot[0], f) << 16)
                    | (lerp(top[1], bot[1], f) << 8)
                    |  lerp(top[2], bot[2], f);
            for (int x = 0; x < w; x++) {
                int p = tpl.getRGB(x, y);
                img.setRGB(x, y, near(p, oldGrad) ? newGrad : p);
            }
        }
        // Draw a clear, high-contrast Lumina crystal over the cape's back face so the logo
        // reads on EVERY cape colour (the recoloured template crystal washes out, especially
        // on violet capes). Drawn ~2x tall to compensate the cape model's vertical squish.
        drawCrystal(img);

        Files.createDirectories(out.getParent());
        ImageIO.write(img, "png", out.toFile());
    }

    /** Paints the faceted Lumina crystal on the cape's back-face region (x 1..11, y 2..34). */
    private static void drawCrystal(BufferedImage img) {
        java.awt.Graphics2D g = img.createGraphics();
        int[] px = {6, 9, 8, 6, 4, 3};
        int[] py = {5, 13, 28, 32, 28, 13};
        java.awt.Polygon gem = new java.awt.Polygon(px, py, 6);
        // soft outer glow so it stands off any background
        g.setColor(new java.awt.Color(0xC4, 0xB5, 0xFD, 90));
        g.fill(new java.awt.Polygon(new int[]{6, 10, 9, 6, 3, 2}, new int[]{4, 13, 29, 33, 29, 13}, 6));
        // faceted body: light top → deep violet bottom
        g.setPaint(new java.awt.GradientPaint(0, 5, new java.awt.Color(0xED, 0xE9, 0xFE),
                0, 32, new java.awt.Color(0x6D, 0x28, 0xD9)));
        g.fill(gem);
        // left facet highlight + centre seam for a 3D gem look
        g.setColor(new java.awt.Color(0xF8, 0xF6, 0xFF));
        g.drawLine(4, 15, 5, 26);
        g.setColor(new java.awt.Color(0x4C, 0x1D, 0x95, 150));
        g.drawLine(6, 7, 6, 30);
        // crisp dark outline
        g.setColor(new java.awt.Color(0x3B, 0x0E, 0x6B));
        g.draw(gem);
        g.dispose();
    }

    private static int lerp(int a, int b, double f) {
        return Math.max(0, Math.min(255, (int) Math.round(a + (b - a) * f)));
    }

    /** True if {@code a} is within a small distance of {@code b} (i.e. plain fabric, not the crystal). */
    private static boolean near(int a, int b) {
        int dr = ((a >> 16) & 0xFF) - ((b >> 16) & 0xFF);
        int dg = ((a >> 8) & 0xFF) - ((b >> 8) & 0xFF);
        int db = (a & 0xFF) - (b & 0xFF);
        return dr * dr + dg * dg + db * db < 30 * 30;
    }

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
