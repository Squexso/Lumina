package com.luminamc.ui;

import com.luminamc.config.LuminaPaths;
import javafx.scene.Scene;
import javafx.scene.image.Image;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/** Applies the dark/violet stylesheet and provides shared theme constants. */
public final class Theme {

    private Theme() {}

    public static final String ACCENT = "#7C3AED";

    /** The accent most recently applied via {@link #applyAccent}, so self-built
     *  dialogs/popups can match the user's chosen colour without plumbing it. */
    private static volatile String currentAccent = ACCENT;

    /** The accent colour currently in effect (hex). */
    public static String accent() { return currentAccent; }

    public static void apply(Scene scene) {
        var url = Theme.class.getResource("/css/lumina.css");
        if (url != null && !scene.getStylesheets().contains(url.toExternalForm())) {
            scene.getStylesheets().add(url.toExternalForm());
        }
        if (!scene.getRoot().getStyleClass().contains("app-root")) {
            scene.getRoot().getStyleClass().add("app-root");
        }
    }

    /**
     * Recolours the interactive accent elements (buttons, chips, sliders,
     * progress, active nav) to {@code hex}, by writing a small override stylesheet
     * and attaching it to the scene. Re-applying replaces the previous override.
     */
    /** Applies the remembered accent — convenient for dialogs that don't carry config. */
    public static void applyAccent(Scene scene) { applyAccent(scene, currentAccent); }

    public static void applyAccent(Scene scene, String hex) {
        if (scene == null || hex == null || hex.isBlank()) return;
        try {
            String h = hex.trim();
            currentAccent = h;
            String css = String.join("\n",
                    ".accent-button,.create-button{-fx-background-color:" + h + ";}",
                    ".accent-button:hover,.create-button:hover{-fx-background-color:derive(" + h + ",18%);}",
                    ".chip:selected{-fx-background-color:" + h + ";-fx-border-color:transparent;}",
                    ".nav-item:active{-fx-background-color:" + h + "33;-fx-border-color:" + h + ";}",
                    ".play-button:hover{-fx-background-color:" + h + ";-fx-text-fill:white;}",
                    ".progress-bar>.bar,.global-progress>.bar{-fx-background-color:" + h + ";}",
                    ".slider .thumb{-fx-background-color:" + h + ";}",
                    ".instance-card:hover{-fx-border-color:" + h + ";}");
            Path f = LuminaPaths.root().resolve("accent.css");
            Files.writeString(f, css);
            String uri = f.toUri().toString();
            scene.getStylesheets().removeIf(s -> s.contains("accent.css"));
            scene.getStylesheets().add(uri);
        } catch (Exception ignored) {}
    }

    /**
     * Loads the launcher logo. Prefers a user-supplied {@code ~/.luminamc/logo.png}
     * (drop a file there to rebrand without rebuilding), then falls back to the
     * bundled resource.
     */
    public static Image logo() {
        for (String name : new String[]{"logo.png", "logo.jpg", "logo.jpeg"}) {
            Path override = LuminaPaths.root().resolve(name);
            if (Files.exists(override)) {
                try {
                    return new Image(override.toUri().toString());
                } catch (Exception ignored) {}
            }
        }
        try (InputStream in = Theme.class.getResourceAsStream("/assets/logo.png")) {
            return in != null ? new Image(in) : null;
        } catch (Exception e) {
            return null;
        }
    }
}
