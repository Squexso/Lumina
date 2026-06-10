package com.luminamc.tools;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * One-shot: installs a new brand logo. Center-crops the source image to a square,
 * <b>keys out its dark background</b> (so only the glowing crystal remains, on
 * transparency — it then blends into any surface instead of sitting in a box), and
 * writes it to {@code launcher/src/main/resources/assets/logo.png} — the bundled
 * logo that {@code Theme.logo()} and {@code AppIcon} pick up everywhere.
 *
 * <p>Run: {@code gradlew -p launcher installLogo -Psrc="C:\path\to\image.png"}
 * (defaults to {@code ~/Downloads/Lumina-logo.png}).
 */
public final class LogoInstall {
    public static void main(String[] args) throws Exception {
        Path src = Path.of(args.length > 0 && !args[0].isBlank()
                ? args[0]
                : System.getProperty("user.home") + "/Downloads/Lumina-logo.png");
        BufferedImage in = ImageIO.read(src.toFile());
        if (in == null) throw new IllegalStateException("Could not read image: " + src);

        // Center-crop to a square (keeps the whole crystal cluster, drops side padding).
        int w = in.getWidth(), h = in.getHeight(), side = Math.min(w, h);
        BufferedImage square = in.getSubimage((w - side) / 2, (h - side) / 2, side, side);

        BufferedImage keyed = keyOutBackground(square);
        BufferedImage logo = resize(keyed, 512);

        Path out = Path.of(System.getProperty("user.dir"), "src/main/resources/assets/logo.png");
        Files.createDirectories(out.getParent());
        ImageIO.write(logo, "png", out.toFile());

        // Debug previews: composite over a sidebar-dark and a light surface so the
        // transparent blend can be eyeballed.
        Path dbg = Path.of(System.getProperty("user.home"), ".luminamc", "logs");
        Files.createDirectories(dbg);
        ImageIO.write(over(logo, 0x1A1233), "png", dbg.resolve("logo_on_dark.png").toFile());
        ImageIO.write(over(logo, 0xCDC9D6), "png", dbg.resolve("logo_on_light.png").toFile());

        System.out.println("LOGO installed (" + src.getFileName() + " → " + side + "px square, bg keyed) -> " + out);
    }

    /** Makes the dark background transparent: alpha rises with how much brighter a
     *  pixel is than the (sampled) background, so the crystal + its glow survive and
     *  the surrounding dark fades smoothly to nothing. */
    private static BufferedImage keyOutBackground(BufferedImage img) {
        int w = img.getWidth(), h = img.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_ARGB);
        double bg = (luma(img.getRGB(0, 0)) + luma(img.getRGB(w - 1, 0))
                + luma(img.getRGB(0, h - 1)) + luma(img.getRGB(w - 1, h - 1))) / 4.0;
        double lo = bg + 10, hi = bg + 70;          // below lo → transparent, above hi → opaque
        for (int y = 0; y < h; y++) {
            for (int x = 0; x < w; x++) {
                int argb = img.getRGB(x, y);
                int r = (argb >> 16) & 0xFF, g = (argb >> 8) & 0xFF, b = argb & 0xFF;
                double l = 0.2126 * r + 0.7152 * g + 0.0722 * b;
                double a = (l - lo) / (hi - lo);
                a = a < 0 ? 0 : a > 1 ? 1 : a;
                a = a * a * (3 - 2 * a);            // smoothstep for a soft edge
                int alpha = (int) Math.round(a * 255);
                out.setRGB(x, y, (alpha << 24) | (r << 16) | (g << 8) | b);
            }
        }
        return out;
    }

    private static double luma(int argb) {
        return 0.2126 * ((argb >> 16) & 0xFF) + 0.7152 * ((argb >> 8) & 0xFF) + 0.0722 * (argb & 0xFF);
    }

    /** Flattens {@code img} onto an opaque {@code rgb} surface (for preview only). */
    private static BufferedImage over(BufferedImage img, int rgb) {
        int w = img.getWidth(), h = img.getHeight();
        BufferedImage out = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = out.createGraphics();
        g.setColor(new java.awt.Color(rgb));
        g.fillRect(0, 0, w, h);
        g.drawImage(img, 0, 0, null);
        g.dispose();
        return out;
    }

    private static BufferedImage resize(BufferedImage src, int size) {
        BufferedImage out = new BufferedImage(size, size, BufferedImage.TYPE_INT_ARGB);
        Graphics2D g = out.createGraphics();
        g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
        g.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.drawImage(src, 0, 0, size, size, null);
        g.dispose();
        return out;
    }
}
