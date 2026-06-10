package com.luminamc.tools;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * One-shot: installs a new brand logo. Center-crops the source image to a square
 * and writes it to {@code launcher/src/main/resources/assets/logo.png} — the
 * bundled logo that {@code Theme.logo()} and {@code AppIcon} pick up everywhere
 * (window/taskbar icon, sidebar, setup, tray). The {@code .ico} is regenerated
 * from it by the {@code genIcon} task (AppIcon → packaging/lumina.ico).
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

        BufferedImage logo = resize(square, 512);
        Path out = Path.of(System.getProperty("user.dir"), "src/main/resources/assets/logo.png");
        Files.createDirectories(out.getParent());
        ImageIO.write(logo, "png", out.toFile());
        System.out.println("LOGO installed (" + src.getFileName() + " → " + side + "px square) -> " + out);
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
