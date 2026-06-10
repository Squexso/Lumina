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

    // Search + sort state for the grid.
    private String query = "";
    private SortMode sortMode = SortMode.RECENT;

    private enum SortMode {
        RECENT("Last played"), NAME("Name"), CREATED("Newest"), PLAYTIME("Most played");
        final String label;
        SortMode(String label) { this.label = label; }
        @Override public String toString() { return label; }
    }

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

    private TextField searchField;

    private HBox header() {
        Label title = new Label("My Instances");
        title.getStyleClass().add("page-title");

        searchField = new TextField();
        searchField.setPromptText("🔍  Search…");
        searchField.setPrefWidth(200);
        searchField.textProperty().addListener((o, a, b) -> {
            query = b == null ? "" : b.trim().toLowerCase();
            rebuild();
        });

        ComboBox<SortMode> sort = new ComboBox<>();
        sort.getItems().setAll(SortMode.values());
        sort.setValue(sortMode);
        sort.setOnAction(e -> {
            if (sort.getValue() != null) { sortMode = sort.getValue(); rebuild(); }
        });

        Button create = new Button("✛   Create New Instance");
        create.getStyleClass().add("create-button");
        create.setOnAction(e -> onCreate.run());

        HBox bar = new HBox(12, title, FxUi.hgrow(), searchField, sort, create);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(28, 32, 18, 32));
        return bar;
    }

    /** Lets callers (e.g. a Ctrl+F shortcut) focus the search box. */
    public void focusSearch() {
        if (searchField != null) searchField.requestFocus();
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
        java.util.Comparator<Instance> recent =
                java.util.Comparator.comparingLong((Instance i) -> i.lastPlayed).reversed()
                        .thenComparing(java.util.Comparator.comparingLong((Instance i) -> i.createdAt).reversed());
        java.util.Comparator<Instance> order = switch (sortMode) {
            case NAME     -> java.util.Comparator.comparing((Instance i) -> i.name == null ? "" : i.name.toLowerCase());
            case CREATED  -> java.util.Comparator.comparingLong((Instance i) -> i.createdAt).reversed();
            case PLAYTIME -> java.util.Comparator.comparingLong((Instance i) -> i.playMillis).reversed().thenComparing(recent);
            default       -> recent;
        };
        var shown = ctx.instances.all().stream().filter(this::matches).sorted(order).toList();
        shown.forEach(inst -> grid.getChildren().add(card(inst)));
        if (ctx.instances.all().isEmpty()) {
            grid.getChildren().add(emptyHint());
        } else if (shown.isEmpty()) {
            grid.getChildren().add(noMatchHint());
        }
        updatePlaytime();
    }

    /** True if the instance matches the current search query (name / version / loader). */
    private boolean matches(Instance i) {
        if (query.isEmpty()) return true;
        String hay = ((i.name == null ? "" : i.name) + " "
                + (i.mcVersion == null ? "" : i.mcVersion) + " "
                + i.loader.displayName).toLowerCase();
        return hay.contains(query);
    }

    private VBox noMatchHint() {
        Label l1 = new Label("No matches");
        l1.getStyleClass().add("row-label");
        Label l2 = FxUi.muted("No instances match “" + query + "”.");
        VBox box = new VBox(6, l1, l2);
        box.setPadding(new Insets(40));
        return box;
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

        // ── titles (three tidy lines: name · version+loader · last-played) ──
        Label name = new Label((inst.pinned ? "📌 " : "") + inst.name);
        name.getStyleClass().add("card-title");

        Label sub = FxUi.muted("Minecraft " + inst.mcVersion + "  ·  " + inst.loader.displayName
                + (inst.loaderVersion != null ? " " + inst.loaderVersion : ""));
        sub.getStyleClass().add("card-sub");

        String status = inst.lastPlayed > 0
                ? "Played " + relativeTime(inst.lastPlayed)
                  + (inst.playMillis > 0 ? "  ·  " + humanDuration(inst.playMillis) + " total" : "")
                : "Never played";
        Label stat = FxUi.muted(status);
        stat.getStyleClass().add("card-detail");

        VBox titles = new VBox(3, name, sub, stat);
        titles.setAlignment(Pos.CENTER_LEFT);

        // ── single overflow menu (folder/pin/delete all live inside it) ──
        Button menu = new Button("⋮");
        menu.getStyleClass().add("card-icon-btn");
        menu.setOnAction(e -> showCardMenu(menu, inst));

        HBox top = new HBox(12, icon, titles, FxUi.hgrow(), menu);
        top.setAlignment(Pos.TOP_LEFT);

        // ── play button ──
        Button play = new Button("PLAY");
        play.getStyleClass().add("play-button");
        play.setMaxWidth(Double.MAX_VALUE);
        play.setOnAction(e -> launch(inst, play));

        VBox card = new VBox(16, top, play);
        card.getStyleClass().add("instance-card");
        card.setPrefWidth(300);
        card.setMinWidth(280);

        // Clicking the card body (not a button) opens the detail view.
        card.setOnMouseClicked(e -> {
            if (e.getTarget() instanceof javafx.scene.Node n && isInsideButton(n)) return;
            onOpen.accept(inst);
        });

        // Hover: subtle lift + violet glow.
        javafx.scene.effect.DropShadow lift = new javafx.scene.effect.DropShadow(0, javafx.scene.paint.Color.web("#8B5CF6"));
        card.setCursor(javafx.scene.Cursor.HAND);
        card.setOnMouseEntered(e -> {
            card.setEffect(lift);
            javafx.animation.ScaleTransition st = new javafx.animation.ScaleTransition(javafx.util.Duration.millis(140), card);
            st.setToX(1.025); st.setToY(1.025); st.play();
            new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.millis(160),
                    new javafx.animation.KeyValue(lift.radiusProperty(), 22))).play();
        });
        card.setOnMouseExited(e -> {
            javafx.animation.ScaleTransition st = new javafx.animation.ScaleTransition(javafx.util.Duration.millis(140), card);
            st.setToX(1.0); st.setToY(1.0); st.play();
            javafx.animation.Timeline g = new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.millis(160),
                    new javafx.animation.KeyValue(lift.radiusProperty(), 0)));
            g.setOnFinished(ev -> card.setEffect(null));
            g.play();
        });
        return card;
    }

    /** A rounded gradient tile with the instance's initial — stands in for a pack icon. */
    private StackPane monogram(Instance inst) {
        String s = inst.name == null || inst.name.isBlank() ? "?"
                : inst.name.substring(0, 1).toUpperCase();
        Label letter = new Label(s);
        letter.setStyle("-fx-text-fill: rgba(255,255,255,0.95); -fx-font-size: 22px; -fx-font-weight: bold;");

        StackPane tile = new StackPane(letter);
        tile.setMinSize(50, 50);
        tile.setPrefSize(50, 50);
        tile.setMaxSize(50, 50);

        // Hue derived from the name so each instance keeps a stable, distinct colour — but
        // muted (low saturation, deeper value) so the tiles read as a cohesive set rather
        // than a clashing rainbow.
        double hue = Math.abs((inst.id != null ? inst.id : s).hashCode() % 360);
        Color c1 = Color.hsb(hue, 0.40, 0.58);
        Color c2 = Color.hsb((hue + 18) % 360, 0.50, 0.38);
        LinearGradient g = new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, c1), new Stop(1, c2));
        tile.setBackground(new Background(new BackgroundFill(g, new CornerRadii(14), Insets.EMPTY)));
        tile.setBorder(new Border(new BorderStroke(Color.web("#ffffff", 0.10),
                BorderStrokeStyle.SOLID, new CornerRadii(14), new BorderWidths(1))));
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
        com.luminamc.ui.FxUi.hoverPop(clean);
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

        javafx.scene.Node crystal;
        javafx.scene.image.Image logoImg = com.luminamc.ui.Theme.logo();
        if (logoImg != null) {
            javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(logoImg);
            iv.setFitWidth(22);
            iv.setFitHeight(22);
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
            crystal = iv;
        } else {
            crystal = CrystalLogo.crystalNode(20);
        }

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

    /** "just now", "5m ago", "3h ago", "2d ago", "4w ago", "6mo ago", "1y ago". */
    private static String relativeTime(long then) {
        long diff = System.currentTimeMillis() - then;
        if (diff < 60_000) return "just now";
        long min = diff / 60_000;
        if (min < 60) return min + "m ago";
        long hrs = min / 60;
        if (hrs < 24) return hrs + "h ago";
        long days = hrs / 24;
        if (days < 7) return days + "d ago";
        if (days < 30) return (days / 7) + "w ago";
        if (days < 365) return (days / 30) + "mo ago";
        return (days / 365) + "y ago";
    }

    /** "45m", "3h 24m". */
    private static String humanDuration(long millis) {
        long min = millis / 60_000;
        long h = min / 60, m = min % 60;
        return h > 0 ? h + "h " + m + "m" : m + "m";
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
