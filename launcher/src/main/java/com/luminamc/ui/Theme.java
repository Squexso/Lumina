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
            // Translucent variants (only valid for #RRGGBB input — which is what we store).
            boolean rgb = h.length() == 7;
            String soft  = rgb ? h + "38" : h;   // ~22% — borders
            String faint = rgb ? h + "26" : h;   // ~15% — hairlines
            String css = String.join("\n",
                    ".accent-button,.create-button{-fx-background-color:" + h + ";}",
                    ".accent-button:hover,.create-button:hover{-fx-background-color:derive(" + h + ",18%);}",
                    ".chip:selected{-fx-background-color:" + h + ";-fx-border-color:transparent;}",
                    ".nav-item:active{-fx-background-color:" + h + "33;-fx-border-color:" + h + ";}",
                    ".play-button{-fx-border-color:" + soft + ";}",
                    ".play-button:hover{-fx-background-color:" + h + ";-fx-text-fill:white;-fx-border-color:transparent;}",
                    ".progress-bar>.bar,.global-progress>.bar{-fx-background-color:" + h + ";}",
                    ".slider .thumb{-fx-background-color:" + h + ";}",
                    // Tint every accent-coloured hairline too, so a colour swap leaves no
                    // stale violet edges anywhere.
                    ".sidebar{-fx-border-color:transparent " + faint + " transparent transparent;}",
                    ".instance-card{-fx-border-color:" + soft + ";}",
                    ".instance-card:hover{-fx-border-color:" + h + ";}",
                    ".status-bar{-fx-border-color:" + faint + " transparent transparent transparent;}",
                    ".tagline{-fx-text-fill:" + h + ";}");
            Path f = LuminaPaths.root().resolve("accent.css");
            Files.writeString(f, css);
            String uri = f.toUri().toString();
            scene.getStylesheets().removeIf(s -> s.contains("accent.css"));
            scene.getStylesheets().add(uri);
        } catch (Exception ignored) {}
    }

    // ── background presets ──────────────────────────────────────────────────

    /** A clean dark background preset: five stops of the launcher's radial gradient. */
    public record Bg(String id, String name, String c0, String c1, String c2, String c3, String c4) {
        /** The full-scene radial gradient (same geometry as the default nebula). */
        public String sceneGradient() {
            return "radial-gradient(focus-angle 0deg, focus-distance 0%, center 40% 16%, radius 105%, "
                    + c0 + " 0%, " + c1 + " 30%, " + c2 + " 60%, " + c3 + " 82%, " + c4 + " 100%)";
        }
        /** A compact version of the gradient for small preview tiles. */
        public String previewGradient() {
            return "radial-gradient(focus-angle 0deg, focus-distance 0%, center 50% 18%, radius 130%, "
                    + c0 + " 0%, " + c2 + " 55%, " + c4 + " 100%)";
        }
    }

    /** All selectable backgrounds — same shape/darkness, different (subtle) hues. */
    public static final java.util.List<Bg> BACKGROUNDS = java.util.List.of(
            new Bg("nebula",   "Nebula",   "#502c92", "#361a68", "#1d0f3a", "#0d0720", "#070411"),
            new Bg("midnight", "Midnight", "#2f3e8e", "#20296b", "#131a42", "#0a0e26", "#050712"),
            new Bg("ocean",    "Ocean",    "#136a72", "#0d4a55", "#082e38", "#041a20", "#020d10"),
            new Bg("aurora",   "Aurora",   "#1f7a5c", "#14543f", "#0c3326", "#061d15", "#030e0a"),
            new Bg("ember",    "Ember",    "#7e2d44", "#5a1e33", "#371221", "#1e0a13", "#0e040a"),
            new Bg("graphite", "Graphite", "#4b4b58", "#36363f", "#222228", "#131318", "#0a0a0d"));

    /** Resolves a preset id (unknown/null falls back to the default nebula). */
    public static Bg background(String id) {
        return BACKGROUNDS.stream().filter(b -> b.id().equals(id)).findFirst().orElse(BACKGROUNDS.get(0));
    }

    /**
     * Applies a background preset to the scene by writing a small override stylesheet
     * (companion to {@link #applyAccent}). Re-applying replaces the previous one.
     */
    public static void applyBackground(Scene scene, String id) {
        if (scene == null) return;
        try {
            Bg bg = background(id);
            String css = String.join("\n",
                    ".cosmic-bg{-fx-background-color:" + bg.sceneGradient() + ";}",
                    ".app-root{-fx-background-color:" + bg.c4() + ";}");
            Path f = LuminaPaths.root().resolve("background.css");
            Files.writeString(f, css);
            String uri = f.toUri().toString();
            scene.getStylesheets().removeIf(s -> s.contains("background.css"));
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
