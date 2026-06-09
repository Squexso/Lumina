package com.luminamc;

import com.luminamc.ui.AppContext;
import com.luminamc.ui.MainWindow;
import com.luminamc.ui.SetupWizard;
import com.luminamc.ui.Theme;
import javafx.application.Application;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Parent;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.stage.Stage;
import javafx.stage.StageStyle;

/** JavaFX application entry point — frameless window with a custom Lumina title bar. */
public final class LuminaApp extends Application {

    private static final int RESIZE_BORDER = 6;
    private boolean resizeRight, resizeBottom;

    @Override
    public void start(Stage stage) {
        AppContext ctx = new AppContext();
        stage.initStyle(StageStyle.UNDECORATED);   // no OS chrome — we draw our own

        StackPane content = new StackPane();
        VBox.setVgrow(content, Priority.ALWAYS);

        VBox outer = new VBox(titleBar(stage), content);
        outer.getStyleClass().add("app-root");

        Scene scene = new Scene(outer, 1100, 720);
        Theme.apply(scene);
        Theme.applyAccent(scene, ctx.config.accentColor);

        stage.setScene(scene);
        stage.setTitle("LuminaMC");
        stage.setMinWidth(940);
        stage.setMinHeight(620);

        Image logo = Theme.logo();
        if (logo != null) {
            stage.getIcons().add(logo);
        } else {
            var icons = com.luminamc.ui.components.AppIcon.images();
            stage.getIcons().addAll(icons);
            // Export the rendered crystal once (PNG + .ico) for packaging / reuse.
            try {
                java.nio.file.Path icoPath = com.luminamc.config.LuminaPaths.root().resolve("lumina.ico");
                if (!icons.isEmpty() && !java.nio.file.Files.exists(icoPath)) {
                    java.awt.image.BufferedImage bi = javafx.embed.swing.SwingFXUtils.fromFXImage(icons.get(0), null);
                    javax.imageio.ImageIO.write(bi, "png",
                            com.luminamc.config.LuminaPaths.root().resolve("lumina-icon.png").toFile());
                    com.luminamc.ui.components.AppIcon.writeIco(bi, icoPath);
                }
            } catch (Exception ignored) {}
        }

        makeResizable(stage, scene);

        if (!ctx.config.setupComplete) {
            Parent wizard = new SetupWizard(ctx).build(stage,
                    () -> content.getChildren().setAll(new MainWindow(ctx).getRoot()));
            content.getChildren().setAll(wizard);
        } else {
            content.getChildren().setAll(new MainWindow(ctx).getRoot());
        }

        stage.show();

        // Minimise-to-tray: keep running in the background when the window is minimised.
        if (!Boolean.getBoolean("luminamc.smoketest")) {
            com.luminamc.ui.TraySupport.install(stage);
            // On first run of the packaged app, drop a Desktop shortcut.
            new Thread(() -> com.luminamc.ui.DesktopShortcut.ensureOnFirstRun(ctx.config),
                    "luminamc-desktop-shortcut").start();
        }

        // Discord Rich Presence — shows the launcher status (logo + text) while open.
        if (ctx.config.discordRichPresence && !Boolean.getBoolean("luminamc.smoketest")) {
            new com.luminamc.discord.DiscordPresence(ctx.config.discordClientId)
                    .button(ctx.config.discordInviteUrl)
                    .start(ctx.config.discordDetails, ctx.config.discordState);
        }

        // Auto-update: silently check the feed on launch; only surfaces UI if newer.
        if (ctx.config.autoUpdate && !Boolean.getBoolean("luminamc.smoketest")) {
            com.luminamc.ui.UpdateFlow.checkOnStartup(stage, ctx);
        }

        // Auto-launch (dev/testing convenience): immediately play the most-recently-played
        // instance. Enabled via -Dluminamc.autolaunch=true.
        if (Boolean.getBoolean("luminamc.autolaunch") && !Boolean.getBoolean("luminamc.smoketest")) {
            javafx.application.Platform.runLater(() -> {
                var all = ctx.instances.all();
                if (all.isEmpty()) { System.out.println("[autolaunch] no instances"); return; }
                var inst = all.get(0);   // already sorted most-recently-played first
                System.out.println("[autolaunch] launching instance " + inst.id + " (" + inst.name + ")");
                new com.luminamc.ui.LaunchService(ctx).launchAsync(inst, new com.luminamc.ui.LaunchService.Callbacks() {
                    public void phase(String t) { System.out.println("[autolaunch] " + t); }
                    public void progress(double f, String d) {}
                    public void log(String l) {}
                    public void started(Process p) { System.out.println("[autolaunch] game process started"); }
                    public void finished(com.luminamc.crash.CrashReporter.Report r) {}
                    public void failed(Throwable e) { System.out.println("[autolaunch] failed: " + e); }
                });
            });
        }

        if (Boolean.getBoolean("luminamc.smoketest")) {
            try {
                java.nio.file.Files.writeString(
                        com.luminamc.config.LuminaPaths.logs().resolve("smoketest.ok"),
                        "UI rendered at " + java.time.Instant.now());
            } catch (Exception ignored) {}
            javafx.animation.PauseTransition exit =
                    new javafx.animation.PauseTransition(javafx.util.Duration.seconds(3));
            exit.setOnFinished(e -> javafx.application.Platform.exit());
            exit.play();
        }
    }

    // ── custom title bar ───────────────────────────────────────────────────

    private HBox titleBar(Stage stage) {
        Label title = new Label("✦  LuminaMC");
        title.getStyleClass().add("title-bar-text");

        Button min = winButton("🗕", () -> stage.setIconified(true), false);
        Button max = winButton("🗖", () -> stage.setMaximized(!stage.isMaximized()), false);
        Button close = winButton("✕", () -> { stage.close(); javafx.application.Platform.exit(); }, true);

        HBox bar = new HBox(title, spacer(), min, max, close);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.getStyleClass().add("title-bar");

        // Drag to move; double-click to maximize/restore.
        final double[] drag = new double[2];
        bar.setOnMousePressed(e -> { drag[0] = e.getScreenX() - stage.getX(); drag[1] = e.getScreenY() - stage.getY(); });
        bar.setOnMouseDragged(e -> {
            if (stage.isMaximized()) return;
            stage.setX(e.getScreenX() - drag[0]);
            stage.setY(e.getScreenY() - drag[1]);
        });
        bar.setOnMouseClicked(e -> { if (e.getClickCount() == 2) stage.setMaximized(!stage.isMaximized()); });
        return bar;
    }

    private Button winButton(String glyph, Runnable action, boolean isClose) {
        Button b = new Button(glyph);
        b.getStyleClass().add("win-btn");
        if (isClose) b.getStyleClass().add("win-close");
        b.setFocusTraversable(false);
        b.setOnAction(e -> action.run());
        return b;
    }

    private Region spacer() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    // ── manual edge resize (right / bottom / corner) ────────────────────────

    private void makeResizable(Stage stage, Scene scene) {
        scene.setOnMouseMoved(e -> {
            if (stage.isMaximized()) { scene.setCursor(Cursor.DEFAULT); return; }
            boolean r = e.getSceneX() >= stage.getWidth() - RESIZE_BORDER;
            boolean b = e.getSceneY() >= stage.getHeight() - RESIZE_BORDER;
            scene.setCursor(r && b ? Cursor.SE_RESIZE : r ? Cursor.E_RESIZE : b ? Cursor.S_RESIZE : Cursor.DEFAULT);
        });
        scene.setOnMousePressed(e -> {
            resizeRight  = !stage.isMaximized() && e.getSceneX() >= stage.getWidth() - RESIZE_BORDER;
            resizeBottom = !stage.isMaximized() && e.getSceneY() >= stage.getHeight() - RESIZE_BORDER;
        });
        scene.setOnMouseDragged(e -> {
            if (resizeRight)  stage.setWidth(Math.max(stage.getMinWidth(), e.getScreenX() - stage.getX()));
            if (resizeBottom) stage.setHeight(Math.max(stage.getMinHeight(), e.getScreenY() - stage.getY()));
        });
    }
}
