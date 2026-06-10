package com.luminamc.ui.components;

import javafx.scene.Group;
import javafx.scene.Scene;
import javafx.scene.SnapshotParameters;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;

import java.util.ArrayList;
import java.util.List;

/**
 * Renders the LuminaMC window/taskbar icon: the violet crystal centred on a
 * rounded dark-violet tile, snapshotted at several resolutions so Windows can
 * pick a crisp size for the title bar, taskbar and Alt-Tab.
 */
public final class AppIcon {

    private AppIcon() {}

    private static final int[] SIZES = {256, 128, 64, 48, 32, 16};

    /** Builds the icon at every standard resolution. */
    public static List<Image> images() {
        List<Image> out = new ArrayList<>();
        for (int size : SIZES) {
            try { out.add(render(size)); } catch (Exception ignored) {}
        }
        return out;
    }

    /**
     * Writes a Windows {@code .ico} wrapping the given image (PNG-in-ICO, which
     * Vista+ and jpackage accept). Used to produce a native launcher icon.
     */
    public static void writeIco(java.awt.image.BufferedImage img, java.nio.file.Path out) throws java.io.IOException {
        writeIco(List.of(img), out);
    }

    /**
     * Writes a <b>multi-resolution</b> Windows {@code .ico} containing every given
     * image (each stored as PNG-in-ICO). Windows then picks the sharpest size for
     * the taskbar (32/24/16px), Alt-Tab, and the desktop shortcut — no blurry
     * downscaling from a single huge frame.
     */
    public static void writeIco(java.util.List<java.awt.image.BufferedImage> imgs, java.nio.file.Path out)
            throws java.io.IOException {
        java.util.List<byte[]> pngs = new ArrayList<>();
        for (java.awt.image.BufferedImage img : imgs) {
            java.io.ByteArrayOutputStream b = new java.io.ByteArrayOutputStream();
            javax.imageio.ImageIO.write(img, "png", b);
            pngs.add(b.toByteArray());
        }
        int n = pngs.size();

        java.io.ByteArrayOutputStream ico = new java.io.ByteArrayOutputStream();
        shortLE(ico, 0); shortLE(ico, 1); shortLE(ico, n);              // reserved, type=icon, count
        int offset = 6 + 16 * n;                                        // data starts after the directory
        for (int i = 0; i < n; i++) {
            java.awt.image.BufferedImage img = imgs.get(i);
            int w = img.getWidth(), h = img.getHeight();
            ico.write(w >= 256 ? 0 : w); ico.write(h >= 256 ? 0 : h);   // width, height (0 = 256)
            ico.write(0); ico.write(0);                                 // palette, reserved
            shortLE(ico, 1); shortLE(ico, 32);                          // planes, bit depth
            intLE(ico, pngs.get(i).length); intLE(ico, offset);         // size, offset
            offset += pngs.get(i).length;
        }
        for (byte[] png : pngs) ico.write(png);
        java.nio.file.Files.write(out, ico.toByteArray());
    }

    private static void shortLE(java.io.ByteArrayOutputStream o, int v) { o.write(v & 0xFF); o.write((v >> 8) & 0xFF); }
    private static void intLE(java.io.ByteArrayOutputStream o, int v) {
        o.write(v & 0xFF); o.write((v >> 8) & 0xFF); o.write((v >> 16) & 0xFF); o.write((v >> 24) & 0xFF);
    }

    private static Image bundledLogo;
    private static boolean bundledLogoTried;

    /** The bundled brand logo ({@code /assets/logo.png}) if present, else null. */
    private static Image bundledLogo() {
        if (!bundledLogoTried) {
            bundledLogoTried = true;
            try (var in = AppIcon.class.getResourceAsStream("/assets/logo.png")) {
                if (in != null) bundledLogo = new Image(in);
            } catch (Exception ignored) {}
        }
        return bundledLogo;
    }

    private static Image render(int size) {
        double arc = size * 0.26;

        // Prefer the bundled brand logo image (rounded-clipped); fall back to the vector crystal.
        Image logo = bundledLogo();
        StackPane root;
        if (logo != null) {
            javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(logo);
            iv.setFitWidth(size);
            iv.setFitHeight(size);
            iv.setSmooth(true);
            Rectangle clip = new Rectangle(size, size);
            clip.setArcWidth(arc);
            clip.setArcHeight(arc);
            iv.setClip(clip);
            root = new StackPane(iv);
            root.setPrefSize(size, size);
            root.setMaxSize(size, size);
            root.setStyle("-fx-background-color: transparent;");
            new Scene(root, size, size, Color.TRANSPARENT);
            root.applyCss();
            root.layout();
            SnapshotParameters spx = new SnapshotParameters();
            spx.setFill(Color.TRANSPARENT);
            spx.setViewport(new javafx.geometry.Rectangle2D(0, 0, size, size));
            return root.snapshot(spx, null);
        }

        Rectangle bg = new Rectangle(size, size);
        bg.setArcWidth(arc);
        bg.setArcHeight(arc);
        bg.setFill(new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#2a1556")),
                new Stop(1, Color.web("#140a24"))));

        Group crystal = CrystalLogo.crystalNode(size * 0.60);

        root = new StackPane(bg, crystal);
        root.setPrefSize(size, size);
        root.setMaxSize(size, size);
        root.setStyle("-fx-background-color: transparent;");

        // A scene is required so CSS/layout/effects resolve before snapshotting.
        new Scene(root, size, size, Color.TRANSPARENT);
        root.applyCss();
        root.layout();

        SnapshotParameters sp = new SnapshotParameters();
        sp.setFill(Color.TRANSPARENT);
        // Clamp to an exact square so effect overflow (glow) can't make the frame
        // non-square — keeps small taskbar sizes crisp and aligned.
        sp.setViewport(new javafx.geometry.Rectangle2D(0, 0, size, size));
        return root.snapshot(sp, null);
    }
}
