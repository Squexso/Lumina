package com.luminamc.ui.panels;

import com.luminamc.config.LuminaPaths;
import com.luminamc.crash.CrashReporter;
import com.luminamc.instance.Instance;
import com.luminamc.ui.AppContext;
import com.luminamc.ui.FxUi;
import com.luminamc.ui.LaunchService;
import com.luminamc.ui.components.CrystalLogo;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.paint.CycleMethod;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.stream.Stream;

/**
 * Home view — every instance shown as a card in a responsive grid, with a
 * "Create New Instance" action and a bottom status bar (disk usage + launch
 * progress), matching the cosmic LuminaMC design.
 */
public final class InstanceGridPanel extends BorderPane {

    private final AppContext ctx;
    private final Consumer<Instance> onOpen;     // open instance detail view
    private final Runnable onCreate;             // create new instance
    private final Consumer<Instance> onChanged;  // instance deleted/edited → refresh

    private final FlowPane grid = new FlowPane(18, 18);

    // Bottom status bar.
    private final Label spaceLabel = new Label("Calculating disk usage…");
    private final Label playtimeLabel = new Label();
    private final Label progressLabel = new Label("Ready to play");
    private final ProgressBar globalProgress = new ProgressBar(0);

    public InstanceGridPanel(AppContext ctx, Consumer<Instance> onOpen,
                             Runnable onCreate, Consumer<Instance> onChanged) {
        this.ctx = ctx;
        this.onOpen = onOpen;
        this.onCreate = onCreate;
        this.onChanged = onChanged;

        getStyleClass().add("grid-root");
        setTop(header());
        setCenter(scrollGrid());
        setBottom(statusBar());

        rebuild();
        computeDiskUsageAsync();
    }

    // ── header ───────────────────────────────────────────────────────────

    private HBox header() {
        Label title = new Label("My Instances");
        title.getStyleClass().add("page-title");

        Button create = new Button("✛   Create New Instance");
        create.getStyleClass().add("create-button");
        create.setOnAction(e -> onCreate.run());

        HBox bar = new HBox(title, FxUi.hgrow(), create);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(28, 32, 18, 32));
        return bar;
    }

    private ScrollPane scrollGrid() {
        grid.setPadding(new Insets(4, 32, 24, 32));
        grid.setAlignment(Pos.TOP_LEFT);
        grid.setPrefWrapLength(960);

        ScrollPane sp = new ScrollPane(grid);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("cosmic-scroll");
        return sp;
    }

    // ── grid building ────────────────────────────────────────────────────

    public void rebuild() {
        grid.getChildren().clear();
        // Most-recently-played first (a freshly launched instance jumps to the
        // front); never-played fall back to newest-created.
        java.util.Comparator<Instance> order =
                java.util.Comparator.comparingLong((Instance i) -> i.lastPlayed).reversed()
                        .thenComparing(java.util.Comparator.comparingLong((Instance i) -> i.createdAt).reversed());
        ctx.instances.all().stream().sorted(order).forEach(inst -> grid.getChildren().add(card(inst)));
        if (ctx.instances.all().isEmpty()) {
            grid.getChildren().add(emptyHint());
        }
        updatePlaytime();
    }

    private VBox emptyHint() {
        Label l1 = new Label("No instances yet");
        l1.getStyleClass().add("row-label");
        Label l2 = FxUi.muted("Click “Create New Instance” to set up your first Minecraft profile.");
        VBox box = new VBox(6, l1, l2);
        box.setPadding(new Insets(40));
        return box;
    }

    private VBox card(Instance inst) {
        // ── icon tile ──
        StackPane icon = monogram(inst);

        // ── titles ──
        Label name = new Label((inst.pinned ? "📌 " : "") + inst.name);
        name.getStyleClass().add("card-title");
        Label mc = FxUi.muted("Minecraft " + inst.mcVersion);
        mc.getStyleClass().add("card-sub");
        String detail = inst.loader.displayName
                + (inst.loaderVersion != null ? " " + inst.loaderVersion : "")
                + (inst.lastPlayed > 0 ? "  ·  played" : "");
        Label det = FxUi.muted(detail);
        det.getStyleClass().add("card-detail");
        VBox titles = new VBox(2, name, mc, det);
        titles.setAlignment(Pos.CENTER_LEFT);

        // ── menu + folder buttons ──
        Button menu = new Button("⋮");
        menu.getStyleClass().add("card-icon-btn");
        menu.setOnAction(e -> showCardMenu(menu, inst));

        Button folder = new Button("🗀");
        folder.getStyleClass().add("card-icon-btn");
        folder.setOnAction(e -> openDir(LuminaPaths.instanceGameDir(inst.id)));

        VBox actions = new VBox(6, menu, folder);
        actions.setAlignment(Pos.TOP_RIGHT);

        HBox top = new HBox(12, icon, titles, FxUi.hgrow(), actions);
        top.setAlignment(Pos.TOP_LEFT);

        // ── play button ──
        Button play = new Button("PLAY");
        play.getStyleClass().add("play-button");
        play.setMaxWidth(Double.MAX_VALUE);
        play.setOnAction(e -> launch(inst, play));

        VBox card = new VBox(14, top, play);
        card.getStyleClass().add("instance-card");
        card.setPrefWidth(300);
        card.setMinWidth(280);

        // Clicking the card body (not a button) opens the detail view.
        card.setOnMouseClicked(e -> {
            if (e.getTarget() instanceof javafx.scene.Node n && isInsideButton(n)) return;
            onOpen.accept(inst);
        });
        return card;
    }

    /** A rounded gradient tile with the instance's initial — stands in for a pack icon. */
    private StackPane monogram(Instance inst) {
        String s = inst.name == null || inst.name.isBlank() ? "?"
                : inst.name.substring(0, 1).toUpperCase();
        Label letter = new Label(s);
        letter.setStyle("-fx-text-fill: white; -fx-font-size: 22px; -fx-font-weight: bold;");

        StackPane tile = new StackPane(letter);
        tile.setMinSize(52, 52);
        tile.setPrefSize(52, 52);
        tile.setMaxSize(52, 52);

        // Hue derived from the name so each instance gets a stable distinct color.
        double hue = Math.abs((inst.id != null ? inst.id : s).hashCode() % 360);
        Color c1 = Color.hsb(hue, 0.55, 0.78);
        Color c2 = Color.hsb((hue + 28) % 360, 0.65, 0.55);
        LinearGradient g = new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, c1), new Stop(1, c2));
        tile.setBackground(new Background(new BackgroundFill(g, new CornerRadii(13), Insets.EMPTY)));
        return tile;
    }

    private boolean isInsideButton(javafx.scene.Node n) {
        for (javafx.scene.Node cur = n; cur != null; cur = cur.getParent()) {
            if (cur instanceof ButtonBase) return true;
        }
        return false;
    }

    // ── context menu ─────────────────────────────────────────────────────

    private void showCardMenu(Button anchor, Instance inst) {
        ContextMenu m = new ContextMenu();
        MenuItem open = new MenuItem("Open / Edit");
        open.setOnAction(e -> onOpen.accept(inst));
        MenuItem pin = new MenuItem(inst.pinned ? "📌  Unpin from sidebar" : "📌  Pin to sidebar");
        pin.setOnAction(e -> {
            inst.pinned = !inst.pinned;
            ctx.instances.save(inst);
            rebuild();
            if (onChanged != null) onChanged.accept(inst);  // refresh sidebar
        });
        MenuItem folder = new MenuItem("Open folder");
        folder.setOnAction(e -> openDir(LuminaPaths.instanceGameDir(inst.id)));
        MenuItem del = new MenuItem("Delete instance");
        del.setStyle("-fx-text-fill: #F87171;");
        del.setOnAction(e -> confirmDelete(inst));
        m.getItems().addAll(open, pin, folder, new SeparatorMenuItem(), del);
        m.show(anchor, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    private void confirmDelete(Instance inst) {
        boolean ok = com.luminamc.ui.LuminaDialog.confirmDanger(win(), "Delete instance",
                "Permanently delete \"" + inst.name + "\" and all its files?", "Delete");
        if (ok) {
            ctx.instances.delete(inst);
            rebuild();
            computeDiskUsageAsync();
            if (onChanged != null) onChanged.accept(inst);
        }
    }

    private javafx.stage.Window win() {
        return getScene() != null ? getScene().getWindow() : null;
    }

    // ── launch ───────────────────────────────────────────────────────────

    private void launch(Instance inst, Button play) {
        play.setDisable(true);
        play.setText("LAUNCHING…");
        progressLabel.setText("Launching " + inst.name + "…");
        globalProgress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);

        // Holds the live process so the same button can stop it while running.
        Process[] proc = {null};

        new LaunchService(ctx).launchAsync(inst, new LaunchService.Callbacks() {
            @Override public void phase(String text) { progressLabel.setText(text); }
            @Override public void progress(double fraction, String detail) {
                globalProgress.setProgress(fraction < 0 ? ProgressBar.INDETERMINATE_PROGRESS : fraction);
                progressLabel.setText(detail);
            }
            @Override public void log(String line) { /* not shown in grid view */ }
            @Override public void started(Process process) {
                proc[0] = process;
                play.setDisable(false);
                play.setText("■  STOP");
                play.getStyleClass().add("stop-button");
                play.setOnAction(e -> stopProcess(proc[0], play));
                progressLabel.setText(inst.name + " is running");
                globalProgress.setProgress(1);
                rebuild(); // refresh "played" ordering next time
                if (ctx.config.closeLauncherOnLaunch && getScene() != null) {
                    getScene().getWindow().hide();
                }
            }
            @Override public void finished(CrashReporter.Report report) {
                play.setDisable(false);
                play.setText("PLAY");
                play.getStyleClass().remove("stop-button");
                play.setOnAction(e -> launch(inst, play));
                globalProgress.setProgress(0);
                if (report.crashed()) {
                    progressLabel.setText(inst.name + " crashed (exit " + report.exitCode() + ")");
                    showCrash(inst, report);
                } else {
                    progressLabel.setText("Session ended");
                }
                computeDiskUsageAsync();
            }
            @Override public void failed(Throwable error) {
                play.setDisable(false);
                play.setText("PLAY");
                play.getStyleClass().remove("stop-button");
                play.setOnAction(e -> launch(inst, play));
                globalProgress.setProgress(0);
                progressLabel.setText("Launch failed");
                String msg = error.getMessage() != null ? error.getMessage() : error.toString();
                com.luminamc.ui.LuminaDialog.error(win(), "Couldn't launch \"" + inst.name + "\"", msg);
            }
        });
    }

    /** Stops a running game from the grid's STOP button. */
    private void stopProcess(Process p, Button play) {
        if (p == null || !p.isAlive()) return;
        play.setDisable(true);
        play.setText("STOPPING…");
        progressLabel.setText("Stopping game…");
        p.destroy();
        new Thread(() -> {
            try {
                if (!p.waitFor(5, java.util.concurrent.TimeUnit.SECONDS)) p.destroyForcibly();
            } catch (InterruptedException ignored) { p.destroyForcibly(); }
        }, "luminamc-stop").start();
    }

    private void showCrash(Instance inst, CrashReporter.Report report) {
        com.luminamc.ui.LuminaDialog.error(win(),
                inst.name + " crashed (exit " + report.exitCode() + ")",
                CrashReporter.summarize(java.util.List.of(report.log().split("\n"))));
    }

    // ── status bar ───────────────────────────────────────────────────────

    private HBox statusBar() {
        spaceLabel.getStyleClass().add("muted");
        playtimeLabel.getStyleClass().add("muted");
        updatePlaytime();

        // Clean-caches button right next to the disk usage.
        Button clean = new Button("🧹 Clean");
        clean.getStyleClass().add("ghost-button");
        clean.setOnAction(e -> {
            clean.setDisable(true);
            spaceLabel.setText("Cleaning caches…");
            new Thread(() -> {
                long freed = com.luminamc.config.StorageCleaner.cleanCaches();
                Platform.runLater(() -> {
                    clean.setDisable(false);
                    progressLabel.setText("Freed " + com.luminamc.config.StorageCleaner.human(freed));
                    computeDiskUsageAsync();
                });
            }, "luminamc-clean").start();
        });

        progressLabel.getStyleClass().add("status-progress-label");
        globalProgress.getStyleClass().add("global-progress");
        globalProgress.setPrefWidth(180);

        var crystal = CrystalLogo.crystalNode(20);

        VBox progBox = new VBox(3, progressLabel, globalProgress);
        progBox.setAlignment(Pos.CENTER_RIGHT);

        HBox bar = new HBox(14, spaceLabel, clean, sep(), playtimeLabel, FxUi.hgrow(), progBox, crystal);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(14, 32, 14, 32));
        bar.getStyleClass().add("status-bar");
        return bar;
    }

    private javafx.scene.Node sep() {
        Label l = new Label("·");
        l.getStyleClass().add("muted");
        return l;
    }

    private void updatePlaytime() {
        long min = ctx.config.totalPlayMillis / 60_000;
        long h = min / 60, m = min % 60;
        playtimeLabel.setText("Playtime: " + (h > 0 ? h + "h " : "") + m + "m");
    }

    private void computeDiskUsageAsync() {
        new Thread(() -> {
            long bytes = dirSize(LuminaPaths.root());
            String text = "Total space used: " + humanSize(bytes);
            Platform.runLater(() -> spaceLabel.setText(text));
        }, "luminamc-disksize").start();
    }

    private static long dirSize(Path root) {
        if (root == null || !Files.exists(root)) return 0;
        try (Stream<Path> walk = Files.walk(root)) {
            return walk.filter(Files::isRegularFile).mapToLong(p -> {
                try { return Files.size(p); } catch (Exception e) { return 0; }
            }).sum();
        } catch (Exception e) {
            return 0;
        }
    }

    private static String humanSize(long bytes) {
        if (bytes < 1024) return bytes + " B";
        double kb = bytes / 1024.0;
        if (kb < 1024) return String.format("%.0f KB", kb);
        double mb = kb / 1024.0;
        if (mb < 1024) return String.format("%.1f MB", mb);
        return String.format("%.2f GB", mb / 1024.0);
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private void openDir(Path dir) {
        try { LuminaPaths.mkdirs(dir); } catch (Exception ignored) {}
        new Thread(() -> {
            try {
                if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(new File(dir.toString()));
            } catch (Exception ignored) {}
        }, "luminamc-open").start();
    }
}
