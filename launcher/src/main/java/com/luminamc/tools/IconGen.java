package com.luminamc.tools;

import com.luminamc.ui.components.AppIcon;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;

import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Headless build tool that renders the Lumina crystal at every standard icon size
 * and writes a multi-resolution {@code .ico} for native packaging.
 *
 * <p>It is intentionally <em>not</em> an {@link javafx.application.Application}
 * subclass so it can boot the JavaFX toolkit from the plain classpath (via
 * {@link Platform#startup}) without the "JavaFX runtime components are missing"
 * launcher error.
 *
 * <p>Usage: {@code IconGen <output.ico>} — invoked by the {@code genIcon} Gradle task.
 */
public final class IconGen {

    private IconGen() {}

    public static void main(String[] args) throws Exception {
        Path out = Path.of(args.length > 0 ? args[0] : "lumina.ico");
        Files.createDirectories(out.toAbsolutePath().getParent());

        final Object lock = new Object();
        final boolean[] done = {false};
        final Exception[] error = {null};

        Platform.startup(() -> {
            try {
                List<BufferedImage> frames = new ArrayList<>();
                for (var fx : AppIcon.images()) {
                    frames.add(SwingFXUtils.fromFXImage(fx, null));
                }
                AppIcon.writeIco(frames, out);
            } catch (Exception e) {
                error[0] = e;
            } finally {
                synchronized (lock) { done[0] = true; lock.notifyAll(); }
            }
        });

        synchronized (lock) { while (!done[0]) lock.wait(); }
        Platform.exit();

        if (error[0] != null) throw error[0];
        System.out.println("Wrote multi-resolution ICO: " + out.toAbsolutePath());
    }
}
