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

    /**
     * A clean dark background preset. {@code css} is the full
     * {@code -fx-background-color} value — possibly several layered gradients
     * (percent-based, so the same string also paints the small preview tiles).
     */
    public record Bg(String id, String name, String base, String css) {}

    /** The launcher's standard radial-gradient shape in five stops. */
    private static String radial(String c0, String c1, String c2, String c3, String c4) {
        return "radial-gradient(focus-angle 0deg, focus-distance 0%, center 40% 16%, radius 105%, "
                + c0 + " 0%, " + c1 + " 30%, " + c2 + " 60%, " + c3 + " 82%, " + c4 + " 100%)";
    }

    /** A soft colour cloud for layered space backgrounds. */
    private static String cloud(String cx, String cy, String radius, String rgba) {
        return "radial-gradient(focus-angle 0deg, focus-distance 0%, center " + cx + " " + cy
                + ", radius " + radius + ", " + rgba + " 0%, transparent 100%)";
    }

    /** All selectable backgrounds — clean, dark, professional. */
    public static final java.util.List<Bg> BACKGROUNDS = java.util.List.of(
            new Bg("nebula",   "Nebula",   "#070411",
                    radial("#502c92", "#361a68", "#1d0f3a", "#0d0720", "#070411")),
            new Bg("universe", "Universe", "#03020a", String.join(", ",
                    "linear-gradient(to bottom, #0b0620 0%, #03020a 100%)",
                    cloud("22%", "28%", "52%", "rgba(124,58,237,0.38)"),
                    cloud("78%", "16%", "55%", "rgba(34,211,238,0.18)"),
                    cloud("60%", "86%", "60%", "rgba(236,72,153,0.14)"))),
            new Bg("galaxy",   "Galaxy",   "#020308", String.join(", ",
                    "linear-gradient(to bottom, #050816 0%, #020308 100%)",
                    cloud("30%", "32%", "50%", "rgba(59,130,246,0.32)"),
                    cloud("72%", "62%", "55%", "rgba(139,92,246,0.28)"),
                    cloud("52%", "8%",  "45%", "rgba(6,182,212,0.16)"))),
            new Bg("dusk",     "Dusk",     "#120816", String.join(", ",
                    "linear-gradient(to bottom, #141031 0%, #2a1850 52%, #5a2545 84%, #120816 100%)")),
            new Bg("midnight", "Midnight", "#050712",
                    radial("#2f3e8e", "#20296b", "#131a42", "#0a0e26", "#050712")),
            new Bg("ocean",    "Ocean",    "#020d10",
                    radial("#136a72", "#0d4a55", "#082e38", "#041a20", "#020d10")),
            new Bg("aurora",   "Aurora",   "#030e0a",
                    radial("#1f7a5c", "#14543f", "#0c3326", "#061d15", "#030e0a")),
            new Bg("ember",    "Ember",    "#0e040a",
                    radial("#7e2d44", "#5a1e33", "#371221", "#1e0a13", "#0e040a")),
            new Bg("graphite", "Graphite", "#0a0a0d",
                    radial("#4b4b58", "#36363f", "#222228", "#131318", "#0a0a0d")),
            new Bg("void",     "Void",     "#020203",
                    radial("#1c1c24", "#131318", "#0c0c10", "#060608", "#020203")));

    /** Resolves a preset id (unknown/null falls back to the default nebula). */
    public static Bg background(String id) {
        return BACKGROUNDS.stream().filter(b -> b.id().equals(id)).findFirst().orElse(BACKGROUNDS.get(0));
    }

    /** Marker id for "use the user's own image as the background". */
    public static final String BG_CUSTOM = "custom";

    /**
     * Applies a background to the scene by writing a small override stylesheet
     * (companion to {@link #applyAccent}). Re-applying replaces the previous one.
     * With {@code id == "custom"} and a readable image path, the image is shown
     * full-bleed (cover) under a subtle dark veil so the UI stays legible.
     */
    public static void applyBackground(Scene scene, String id, String imagePath) {
        if (scene == null) return;
        try {
            String css;
            if (BG_CUSTOM.equals(id) && imagePath != null && Files.isRegularFile(Path.of(imagePath))) {
                String uri = Path.of(imagePath).toUri().toString();
                css = String.join("\n",
                        ".cosmic-bg{-fx-background-color:#070411;"
                                + "-fx-background-image:url(\"" + uri + "\");"
                                + "-fx-background-size:cover;"
                                + "-fx-background-position:center center;"
                                + "-fx-background-repeat:no-repeat;}",
                        ".cosmic-veil{-fx-background-color:rgba(7,4,17,0.42);}",
                        ".app-root{-fx-background-color:#070411;}");
            } else {
                Bg bg = background(id);
                css = String.join("\n",
                        ".cosmic-bg{-fx-background-color:" + bg.css() + ";}",
                        ".cosmic-veil{-fx-background-color:transparent;}",
                        ".app-root{-fx-background-color:" + bg.base() + ";}");
            }
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
