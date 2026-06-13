package com.luminamc.ui.panels;

import com.luminamc.crash.CrashAnalyzer;
import com.luminamc.crash.CrashReporter;
import com.luminamc.game.InstanceImporter;
import com.luminamc.instance.Instance;
import com.luminamc.ui.AppContext;
import com.luminamc.ui.FxUi;
import com.luminamc.ui.LaunchService;
import com.luminamc.ui.Sidebar;
import com.luminamc.ui.components.LaunchButton;
import com.luminamc.ui.components.LogView;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Path;
import java.util.List;

/** Home tab: news, the animated launch button, live progress/log and screenshots. */
public final class OverviewPanel extends VBox {

    private final AppContext ctx;
    private final Instance inst;
    private final Sidebar sidebar;
    private final Runnable onRequestLogin;

    private final LaunchButton launchButton = new LaunchButton();
    private final Button stopButton = new Button("⛔  Stop game");
    private final ProgressBar progress = new ProgressBar(0);
    private final Label phaseLabel = new Label();
    private final LogView log = new LogView();
    private final VBox logCard = new VBox(8);
    private final VBox crashCard = new VBox(8);

    /** The running game process, if any — set in started(), used by the Stop button. */
    private volatile Process gameProcess;

    public OverviewPanel(AppContext ctx, Instance inst, Sidebar sidebar, Runnable onRequestLogin) {
        this.ctx = ctx;
        this.inst = inst;
        this.sidebar = sidebar;
        this.onRequestLogin = onRequestLogin;

        setSpacing(18);
        setPadding(new Insets(24));

        getChildren().addAll(header(), importArea(), newsCard(), launchArea(), crashCard, screenshots());
        crashCard.setVisible(false);
        crashCard.setManaged(false);
    }

    private VBox header() {
        Label title = FxUi.h1(inst.name);
        Label badge = new Label(inst.isNextGenScheme() ? "Next-gen (" + inst.mcVersion + ")"
                : "Classic (" + inst.mcVersion + ")");
        badge.getStyleClass().add("badge");
        HBox titleRow = new HBox(12, title, badge);
        titleRow.setAlignment(Pos.CENTER_LEFT);
        Label sub = FxUi.muted("Minecraft " + inst.mcVersion + "  ·  " + inst.loader.displayName
                + (inst.loaderVersion != null ? " " + inst.loaderVersion : ""));
        return new VBox(4, titleRow, sub);
    }

    private VBox newsCard() {
        VBox card = FxUi.card(
                FxUi.sectionTitle("News & Changelog"),
                changelogEntry("LuminaMC 0.1.0", "First public build — instances, Fabric/Forge/NeoForge, "
                        + "Microsoft login and the in-game overlay (Right Shift)."),
                changelogEntry("Client features", "FPS boost, draggable HUD widgets, custom crosshair, "
                        + "toggle-sprint and fullbright — all per-instance."));
        return card;
    }

    private VBox changelogEntry(String title, String body) {
        Label t = new Label(title);
        t.getStyleClass().add("row-label");
        return new VBox(2, t, FxUi.muted(body));
    }

    private VBox launchArea() {
        launchButton.setOnAction(e -> startLaunch());

        stopButton.getStyleClass().add("danger-button");
        stopButton.setVisible(false);
        stopButton.setManaged(false);
        stopButton.setOnAction(e -> stopGame());

        progress.setMaxWidth(Double.MAX_VALUE);
        progress.setVisible(false);
        progress.setManaged(false);
        phaseLabel.getStyleClass().add("muted");

        log.setPrefHeight(260);
        logCard.getStyleClass().add("card");
        logCard.getChildren().add(log);
        VBox.setVgrow(log, Priority.ALWAYS);
        logCard.setVisible(false);
        logCard.setManaged(false);

        VBox box = new VBox(12, launchButton, stopButton, phaseLabel, progress, logCard);
        box.setAlignment(Pos.CENTER);
        VBox.setVgrow(box, Priority.ALWAYS);
        return box;
    }

    /** Terminates the running game. */
    private void stopGame() {
        Process p = gameProcess;
        if (p == null || !p.isAlive()) return;
        log.append("[LuminaMC] Stopping the game…");
        p.destroy();
        // Force-kill if it doesn't exit promptly.
        new Thread(() -> {
            try {
                if (!p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly();
            } catch (InterruptedException ignored) { p.destroyForcibly(); }
        }, "luminamc-stop-" + inst.id).start();
        stopButton.setDisable(true);
        stopButton.setText("Stopping…");
    }

    private VBox screenshots() {
        HBox strip = new HBox(10);
        strip.setAlignment(Pos.CENTER_LEFT);
        List<Path> shots = ctx.instances.listScreenshots(inst);
        int shown = 0;
        for (Path p : shots) {
            if (shown++ >= 6) break;
            try {
                ImageView iv = new ImageView(new Image(p.toUri().toString(), 150, 90, true, true, true));
                iv.getStyleClass().add("screenshot");
                strip.getChildren().add(iv);
            } catch (Exception ignored) {}
        }
        if (shots.isEmpty()) strip.getChildren().add(FxUi.muted("No screenshots yet."));

        Button openFolder = new Button("Open screenshots folder");
        openFolder.getStyleClass().add("ghost-button");
        openFolder.setOnAction(e -> openDir(com.luminamc.config.LuminaPaths.instanceScreenshots(inst.id)));

        HBox head = new HBox(FxUi.sectionTitle("Screenshots"), FxUi.hgrow(), openFolder);
        head.setAlignment(Pos.CENTER_LEFT);
        return FxUi.card(head, strip);
    }

    // ── launch flow ─────────────────────────────────────────────────────

    private void startLaunch() {
        if (ctx.auth.active() == null) {
            int choice = com.luminamc.ui.LuminaDialog.choose(
                    getScene() != null ? getScene().getWindow() : null,
                    "🔑", "Not signed in",
                    "No Microsoft account is signed in. Sign in now, or launch in offline mode?",
                    "Sign in", "Offline", "Cancel");
            if (choice == 0) { onRequestLogin.run(); return; }   // Sign in
            if (choice != 1) return;                              // Cancel / dismissed
            // choice == 1 → Offline: fall through and launch.
        }

        launchButton.setState(LaunchButton.State.BUSY);
        progress.setVisible(true); progress.setManaged(true);
        progress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);
        logCard.setVisible(true); logCard.setManaged(true);
        log.clear();
        log.append("[LuminaMC] Preparing to launch " + inst.name + "…");
        phaseLabel.setText("Preparing…");
        crashCard.setVisible(false); crashCard.setManaged(false);

        new LaunchService(ctx).launchAsync(inst, new LaunchService.Callbacks() {
            @Override public void phase(String text) {
                phaseLabel.setText(text);
                launchButton.setPhase(text);
            }
            @Override public void progress(double fraction, String detail) {
                progress.setProgress(fraction < 0 ? ProgressBar.INDETERMINATE_PROGRESS : fraction);
                phaseLabel.setText(detail);
            }
            @Override public void log(String line) {
                log.append(line);
            }
            @Override public void started(Process process) {
                gameProcess = process;
                launchButton.setState(LaunchButton.State.RUNNING);
                stopButton.setVisible(true); stopButton.setManaged(true);
                stopButton.setDisable(false); stopButton.setText("⛔  Stop game");
                phaseLabel.setText("Minecraft is running.");
                progress.setProgress(1);
                if (sidebar != null) sidebar.refresh();
                if (ctx.config.closeLauncherOnLaunch) {
                    getScene().getWindow().hide();
                }
            }
            @Override public void finished(CrashReporter.Report report) {
                gameProcess = null;
                launchButton.setState(LaunchButton.State.READY);
                stopButton.setVisible(false); stopButton.setManaged(false);
                progress.setVisible(false); progress.setManaged(false);
                if (report.crashed()) showCrash(report);
                else phaseLabel.setText("Session ended.");
            }
            @Override public void failed(Throwable error) {
                gameProcess = null;
                launchButton.setState(LaunchButton.State.READY);
                stopButton.setVisible(false); stopButton.setManaged(false);
                progress.setVisible(false); progress.setManaged(false);
                phaseLabel.setText("Launch failed.");
                String msg = error.getMessage() != null ? error.getMessage() : error.toString();
                logCard.setVisible(true); logCard.setManaged(true);
                log.append("[LuminaMC] ERROR: " + msg);
                com.luminamc.ui.LuminaDialog.error(
                        getScene() != null ? getScene().getWindow() : null,
                        "Couldn't launch \"" + inst.name + "\"", msg);
            }
        });
    }

    /** Import-from-client area shown above the news card. */
    private VBox importArea() {
        Label desc = FxUi.muted(
                "Moving files from another client (Luna Client, OptiFine, etc.)? " +
                "Use the import tool — it filters incompatible mods automatically.");
        Button btn = new Button("📂  Import from another client…");
        btn.getStyleClass().add("ghost-button");
        btn.setOnAction(e -> runImport());
        HBox row = new HBox(10, btn);
        row.setAlignment(Pos.CENTER_LEFT);
        return FxUi.card(FxUi.sectionTitle("Import"), desc, row);
    }

    private void runImport() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select the other client's folder (.minecraft or similar)");
        File picked = chooser.showDialog(getScene() != null ? getScene().getWindow() : null);
        if (picked == null) return;

        Path src = picked.toPath();
        new Thread(() -> {
            try {
                InstanceImporter importer = new InstanceImporter();
                InstanceImporter.ImportAnalysis analysis = importer.analyze(src, inst);

                // Build summary for the confirmation dialog.
                StringBuilder sb = new StringBuilder();
                sb.append("Found the following folders:\n\n");
                for (InstanceImporter.FolderRule rule : analysis.folderRules()) {
                    String icon = switch (rule.decision()) {
                        case COPY        -> "✓";
                        case WARN        -> "⚠";
                        case FILTER_MODS -> "🔎";
                        case SKIP        -> "✗";
                    };
                    sb.append(icon).append("  ").append(rule.folderName())
                      .append(" — ").append(rule.reason()).append('\n');
                }
                if (!analysis.modResults().isEmpty()) {
                    long kept    = analysis.modResults().stream().filter(InstanceImporter.ModAnalysis::keep).count();
                    long dropped = analysis.modResults().size() - kept;
                    sb.append("\nMods: ").append(kept).append(" compatible, ")
                      .append(dropped).append(" incompatible (will be removed).");
                }
                sb.append("\n\nProceed with the import?");

                Platform.runLater(() -> {
                    int choice = com.luminamc.ui.LuminaDialog.choose(
                            getScene() != null ? getScene().getWindow() : null,
                            "📂", "Import — " + picked.getName(),
                            sb.toString(),
                            "Import", "Cancel");
                    if (choice != 0) return;

                    logCard.setVisible(true); logCard.setManaged(true);
                    log.clear();
                    log.append("[Import] Starting import from " + src + "…");

                    new Thread(() -> {
                        try {
                            importer.execute(analysis, inst, line -> Platform.runLater(() -> log.append(line)));
                        } catch (Exception ex) {
                            Platform.runLater(() -> log.append("[Import] ERROR: " + ex.getMessage()));
                        }
                    }, "luminamc-import").start();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> com.luminamc.ui.LuminaDialog.error(
                        getScene() != null ? getScene().getWindow() : null,
                        "Import failed", ex.getMessage()));
            }
        }, "luminamc-import-analyze").start();
    }

    private void showCrash(CrashReporter.Report report) {
        crashCard.getChildren().clear();

        // Run the smart analyzer to get a human-readable diagnosis.
        List<String> logLines = java.util.Arrays.asList(report.log().split("\n"));
        CrashAnalyzer.Diagnosis diag = CrashAnalyzer.analyze(logLines);

        Label title = FxUi.sectionTitle("💥  " + diag.title());
        title.setStyle("-fx-text-fill: #F87171;");

        Label cause = new Label(diag.cause());
        cause.getStyleClass().add("muted");
        cause.setWrapText(true);

        Label fixLabel = new Label("Fix: " + diag.fix());
        fixLabel.setStyle("-fx-text-fill: #86EFAC; -fx-font-size: 13px;");
        fixLabel.setWrapText(true);

        Button openLog = new Button("Open full crash report");
        openLog.getStyleClass().add("ghost-button");
        if (report.file() != null) openLog.setOnAction(e -> openFile(report.file()));

        Button importBtn = new Button("📂  Import clean files…");
        importBtn.getStyleClass().add("ghost-button");
        importBtn.setOnAction(e -> runImport());

        HBox buttons = new HBox(10, openLog, importBtn);
        crashCard.getChildren().addAll(title, cause, fixLabel, buttons);
        crashCard.getStyleClass().setAll("card", "crash-card");
        crashCard.setVisible(true);
        crashCard.setManaged(true);
    }

    private void openDir(Path dir) {
        try { com.luminamc.config.LuminaPaths.mkdirs(dir); } catch (Exception ignored) {}
        openFile(dir);
    }

    private void openFile(Path path) {
        new Thread(() -> {
            try {
                if (Desktop.isDesktopSupported()) {
                    Desktop.getDesktop().open(new File(path.toString()));
                }
            } catch (Exception ignored) {}
        }, "luminamc-open").start();
    }
}
