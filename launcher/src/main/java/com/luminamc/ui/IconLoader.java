package com.luminamc.ui;

import com.luminamc.download.Http;
import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Loads remote icons (PNG / JPEG / GIF / <b>WebP</b>) into an {@link ImageView}
 * off the FX thread, with an in-memory cache. JavaFX's own {@code Image} can't
 * decode WebP — which is what Modrinth serves for most project icons — so we go
 * through {@link ImageIO} (a WebP reader is registered by the {@code webp-imageio}
 * dependency) and convert the result to a JavaFX image.
 *
 * <p>Safe to use from recycled {@code ListView} cells: each request tags the view
 * with its URL and only applies the result if the view still wants that URL.
 */
public final class IconLoader {

    private IconLoader() {}

    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();
    private static final ExecutorService EXEC = Executors.newFixedThreadPool(4, r -> {
        Thread t = new Thread(r, "luminamc-icons");
        t.setDaemon(true);
        return t;
    });
    private static final String KEY = "luminamc.iconUrl";

    static {
        // Make sure the bundled WebP ImageReader SPI is discovered.
        try { ImageIO.scanForPlugins(); } catch (Throwable ignored) {}
    }

    /** Asynchronously loads {@code url} into {@code view} (no-op for blank URLs). */
    public static void into(ImageView view, String url) {
        if (url == null || url.isBlank()) {
            view.getProperties().remove(KEY);
            view.setImage(null);
            return;
        }
        view.getProperties().put(KEY, url);

        Image cached = CACHE.get(url);
        if (cached != null) { view.setImage(cached); return; }

        view.setImage(null);
        EXEC.submit(() -> {
            Image img = load(url);
            if (img == null) return;
            CACHE.put(url, img);
            Platform.runLater(() -> {
                if (url.equals(view.getProperties().get(KEY))) view.setImage(img);
            });
        });
    }

    private static Image load(String url) {
        try {
            byte[] bytes = Http.getBytes(url);
            BufferedImage bi = ImageIO.read(new ByteArrayInputStream(bytes));
            return bi == null ? null : SwingFXUtils.toFXImage(bi, null);
        } catch (Throwable t) {
            return null; // unsupported format / network — caller keeps its placeholder
        }
    }
}
