package com.luminamc.ui;

import com.luminamc.update.SelfUpdater;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Label;
import javafx.scene.control.ProgressBar;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * Drives the user-facing auto-update experience: checks the feed off-thread,
 * prompts with a themed dialog, downloads with a small progress window, then
 * relaunches once the swap is staged.
 */
public final class UpdateFlow {

    private UpdateFlow() {}

    /** Silent background check on launch — only surfaces UI if an update exists. */
    public static void checkOnStartup(Window owner, AppContext ctx) {
        Thread t = new Thread(() -> {
            var found = new SelfUpdater(ctx.config.updateFeedUrl).check();
            found.ifPresent(info -> Platform.runLater(() -> prompt(owner, ctx, info)));
        }, "luminamc-update-startup");
        t.setDaemon(true);
        t.start();
    }

    /** Explicit "Check for updates" — reports the result either way via {@code ifNone}. */
    public static void checkManually(Window owner, AppContext ctx, Runnable ifNone) {
        Thread t = new Thread(() -> {
            var found = new SelfUpdater(ctx.config.updateFeedUrl).check();
            Platform.runLater(() -> found.ifPresentOrElse(info -> prompt(owner, ctx, info), ifNone));
        }, "luminamc-update-manual");
        t.setDaemon(true);
        t.start();
    }

    // ── prompt → download → relaunch ────────────────────────────────────────

    private static void prompt(Window owner, AppContext ctx, SelfUpdater.UpdateInfo info) {
        String notes = (info.notes() == null || info.notes().isBlank()) ? "" : "\n\nWas ist neu:\n" + info.notes();
        boolean ok = LuminaDialog.confirm(owner,
                "Update verfügbar — " + info.version(),
                "Eine neue Version von LuminaMC (" + info.version() + ") ist verfügbar."
                        + notes + "\n\nJetzt herunterladen und installieren? LuminaMC startet danach automatisch neu.");
        if (ok) apply(owner, ctx, info);
    }

    private static void apply(Window owner, AppContext ctx, SelfUpdater.UpdateInfo info) {
        Stage progress = new Stage(StageStyle.TRANSPARENT);
        Label title = new Label("✦  Aktualisiere LuminaMC");
        title.getStyleClass().add("dialog-title");
        Label sub = new Label("Lädt Version " + info.version() + " herunter…");
        sub.getStyleClass().add("dialog-message");
        ProgressBar bar = new ProgressBar();
        bar.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        bar.setMaxWidth(Double.MAX_VALUE);

        VBox card = new VBox(14, title, sub, bar);
        card.getStyleClass().add("lumina-dialog");
        card.setPadding(new Insets(22, 24, 22, 24));
        card.setAlignment(Pos.CENTER_LEFT);
        card.setPrefWidth(380);

        Scene scene = new Scene(card);
        scene.setFill(Color.TRANSPARENT);
        Theme.apply(scene);
        Theme.applyAccent(scene);
        progress.setScene(scene);
        if (owner != null) progress.initOwner(owner);
        progress.initModality(Modality.APPLICATION_MODAL);
        progress.setResizable(false);
        progress.setOnShown(e -> {
            if (owner != null) {
                progress.setX(owner.getX() + (owner.getWidth() - card.getWidth()) / 2);
                progress.setY(owner.getY() + (owner.getHeight() - card.getHeight()) / 2);
            }
        });
        progress.show();

        Thread worker = new Thread(() -> {
            try {
                boolean staged = new SelfUpdater(ctx.config.updateFeedUrl).downloadAndStage(info, bytes -> {});
                Platform.runLater(() -> {
                    if (staged) {
                        sub.setText("Fertig — LuminaMC startet neu…");
                        // Hard exit after a short beat: halt() skips JVM shutdown hooks, so the
                        // system-tray (AWT) and Discord-IPC threads can never block the jar swap.
                        // The detached swap script waits for this process to vanish, then relaunches.
                        new Thread(() -> {
                            try { Thread.sleep(400); } catch (InterruptedException ignored) {}
                            Runtime.getRuntime().halt(0);
                        }, "luminamc-update-halt").start();
                    } else {
                        progress.close();
                        LuminaDialog.info(owner, "Update bereit",
                                "Im Entwicklungs-/Gradle-Modus kann sich die App nicht selbst ersetzen.\n\n"
                                        + "Lade die neue Version manuell:\n" + info.downloadUrl()
                                        + "\n\n(Im installierten Desktop-Programm läuft das Update automatisch.)");
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    progress.close();
                    LuminaDialog.error(owner, "Update fehlgeschlagen",
                            "Konnte das Update nicht installieren:\n" + ex.getMessage());
                });
            }
        }, "luminamc-update-download");
        worker.setDaemon(true);
        worker.start();
    }
}
