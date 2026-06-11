package com.luminamc.game;

import com.luminamc.shop.Cosmetic;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Produces the in-game cape texture for the <b>HD Lumina cape</b> model
 * ({@code net.squxso.lumina.client.LuminaHdCape}).
 *
 * <p>That model is Minecraft's cape box rebuilt 8&times; larger — {@code addBox(80,128,8)}
 * at {@code texOffs(0,0)} on a 256&times;256 atlas — so the back face is 80&times;128 texels
 * instead of vanilla's ~10&times;16. The two big faces carry the cape's full design from
 * {@link CapeArt} (the same artwork shown in the launcher preview and shop), so what you
 * buy is exactly what you wear in-game. The atlas regions mirror the box's unwrap exactly.
 */
public final class GameCapeTexture {

    private GameCapeTexture() {}

    // Box unwrap for addBox(... 80,128,8) at texOffs(0,0): depth D, width W, height H.
    private static final int TEX = 256, D = 8, W = 80, H = 128;

    /** Writes the HD cape PNG (the cape's own {@link CapeArt} design) to {@code out}. */
    public static void write(Cosmetic cape, Path out) throws Exception {
        BufferedImage img = new BufferedImage(TEX, TEX, BufferedImage.TYPE_INT_ARGB);
        BufferedImage art = CapeArt.render(cape, W, H);

        Graphics2D g = img.createGraphics();
        // North (u[8,88]) is the outer/back face you see; south (u[96,176]) the inner.
        g.drawImage(art, D, D, null);
        g.drawImage(art, 2 * D + W, D, null);
        g.dispose();

        // Thin rim faces: a flat mid-tone so the cape edges aren't transparent (→ black).
        int[] top = rgb(cape.colorTop()), bot = rgb(cape.colorBottom());
        int mid = 0xFF000000
                | (((top[0] + bot[0]) / 2) << 16)
                | (((top[1] + bot[1]) / 2) << 8)
                |  ((top[2] + bot[2]) / 2);
        fill(img, D, 0, W, D, mid);          // up
        fill(img, D + W, 0, W, D, mid);      // down
        fill(img, 0, D, D, H, mid);          // west
        fill(img, D + W, D, D, H, mid);      // east

        Files.createDirectories(out.getParent());
        ImageIO.write(img, "png", out.toFile());
    }

    private static void fill(BufferedImage img, int x, int y, int w, int h, int c) {
        for (int j = 0; j < h; j++) for (int i = 0; i < w; i++) img.setRGB(x + i, y + j, c);
    }

    private static int[] rgb(String hexStr) {
        int v = Integer.parseInt(hexStr.replace("#", ""), 16);
        return new int[]{(v >> 16) & 0xFF, (v >> 8) & 0xFF, v & 0xFF};
    }
}
