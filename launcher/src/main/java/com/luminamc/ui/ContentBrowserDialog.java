package com.luminamc.ui;

import com.luminamc.config.LuminaPaths;
import com.luminamc.download.ModrinthApi;
import com.luminamc.game.ModrinthInstaller;
import com.luminamc.instance.Instance;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.function.Function;

/**
 * In-launcher Modrinth browser, shared by every content tab (Mods, Resource
 * Packs, Texture Packs, Shaders). Searches a project type filtered to the
 * instance's Minecraft version — and, for mods, its loader — then installs the
 * chosen project with one click. For mods, required dependencies (Fabric API,
 * libraries, …) are detected and pulled in automatically via
 * {@link ModrinthInstaller}. Styled as a frameless Lumina card to match the rest
 * of the launcher.
 */
public final class ContentBrowserDialog {

    /** What is being browsed: the Modrinth project type + where installs land. */
    public enum Kind {
        MOD("🔎  Find mods", "mod", true, LuminaPaths::instanceMods,
                "sodium, jei, world map",
                "Search Modrinth for mods compatible with this instance."),
        RESOURCE("🎨  Find resource packs", "resourcepack", false, LuminaPaths::instanceResourcePacks,
                "faithful, realistic, 32x",
                "Search Modrinth for resource packs for this Minecraft version."),
        SHADER("✨  Find shaders", "shader", false, LuminaPaths::instanceShaderPacks,
                "complementary, BSL, SEUS",
                "Search Modrinth for shader packs for this Minecraft version.");

        final String title;
        final String projectType;
        final boolean useLoader;
        final Function<String, Path> dir;
        final String examples;
        final String placeholder;

        Kind(String title, String projectType, boolean useLoader, Function<String, Path> dir,
             String examples, String placeholder) {
            this.title = title;
            this.projectType = projectType;
            this.useLoader = useLoader;
            this.dir = dir;
            this.examples = examples;
            this.placeholder = placeholder;
        }
    }

    private final AppContext ctx;
    private final Instance inst;
    private final Kind kind;
    private final String loaderId;   // null for packs/shaders, or vanilla instances
    private final String filterText; // badge + status wording

    /** Hits fetched per request (Modrinth allows up to 100). */
    private static final int PAGE = 40;

    private final Stage stage = new Stage(StageStyle.TRANSPARENT);
    private final TextField search = new TextField();
    private final ListView<ModrinthApi.Project> results = new ListView<>();
    private final ObservableList<ModrinthApi.Project> items = FXCollections.observableArrayList();
    private final Label status = new Label();
    private final ProgressIndicator spinner = new ProgressIndicator();

    private final ModrinthApi api = new ModrinthApi();
    private final ModrinthInstaller installer = new ModrinthInstaller();

    private Runnable onChanged = () -> {};
    private volatile boolean busy = false;        // install in progress
    private volatile boolean loadingPage = false; // a search/page fetch in progress

    // Pagination state — lets the user scroll through the entire catalogue.
    private String currentQuery = "";
    private int totalHits = 0;
    private boolean scrollAttached = false;
    private int scrollTries = 0;

    public ContentBrowserDialog(AppContext ctx, Instance inst, Kind kind) {
        this.ctx = ctx;
        this.inst = inst;
        this.kind = kind;
        this.loaderId = kind.useLoader ? ModrinthApi.loaderId(inst.loader) : null;
        this.filterText = kind.useLoader
                ? inst.loader.displayName + " · " + inst.mcVersion
                : inst.mcVersion;
    }

    /** Opens the browser; {@code onChanged} runs after any successful install. */
    public void show(Window owner, Runnable onChanged) {
        this.onChanged = onChanged == null ? () -> {} : onChanged;

        // ── header: title + filter badge + close ──────────────────────────
        Label titleLabel = new Label(kind.title);
        titleLabel.getStyleClass().add("dialog-title");
        Label filter = new Label(filterText);
        filter.getStyleClass().add("badge");
        Button close = new Button("✕");
        close.getStyleClass().add("icon-button");
        close.setOnAction(e -> stage.close());
        HBox header = new HBox(10, titleLabel, filter, FxUi.hgrow(), close);
        header.setAlignment(Pos.CENTER_LEFT);

        // ── search row ────────────────────────────────────────────────────
        search.setPromptText("🔍  Search Modrinth…  (e.g. " + kind.examples + ")");
        search.getStyleClass().add("perf-search");
        HBox.setHgrow(search, Priority.ALWAYS);
        search.setOnAction(e -> newSearch());
        Button go = new Button("Search");
        go.getStyleClass().add("accent-button");
        go.setOnAction(e -> newSearch());
        spinner.getStyleClass().add("mini-spinner");
        spinner.setPrefSize(18, 18);
        spinner.setMaxSize(18, 18);
        spinner.setVisible(false);
        HBox searchRow = new HBox(10, search, go, spinner);
        searchRow.setAlignment(Pos.CENTER_LEFT);

        // ── results list ──────────────────────────────────────────────────
        results.getStyleClass().add("mod-list");
        results.setItems(items);
        results.setCellFactory(v -> new HitCell());
        results.setPlaceholder(FxUi.muted(kind.placeholder));
        VBox.setVgrow(results, Priority.ALWAYS);

        status.getStyleClass().add("muted");
        status.setWrapText(true);

        VBox root = new VBox(14, header, searchRow, results, status);
        root.getStyleClass().add("lumina-dialog");
        root.setPadding(new Insets(20, 22, 18, 22));
        root.setPrefSize(660, 580);

        // Drag the frameless window by its header.
        final double[] drag = new double[2];
        header.setOnMousePressed(e -> { drag[0] = e.getScreenX() - stage.getX(); drag[1] = e.getScreenY() - stage.getY(); });
        header.setOnMouseDragged(e -> { stage.setX(e.getScreenX() - drag[0]); stage.setY(e.getScreenY() - drag[1]); });

        Scene scene = new Scene(root);
        scene.setFill(Color.TRANSPARENT);
        Theme.apply(scene);
        Theme.applyAccent(scene, ctx.config.accentColor);
        stage.setScene(scene);
        if (owner != null) stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setResizable(false);
        stage.setOnShown(e -> {
            if (owner != null) {
                stage.setX(owner.getX() + (owner.getWidth() - root.getPrefWidth()) / 2);
                stage.setY(owner.getY() + (owner.getHeight() - root.getPrefHeight()) / 2);
            }
            Platform.runLater(search::requestFocus);
        });
        stage.show();

        newSearch(); // initial view: most-downloaded results, then scroll for more
    }

    // ── search + pagination (scroll to load the whole catalogue) ─────────

    /** Starts a fresh search from the current query, replacing the results. */
    private void newSearch() {
        currentQuery = search.getText() == null ? "" : search.getText().trim();
        totalHits = 0;
        items.clear();
        loadingPage = false;
        loadPage(true);
    }

    /** Fetches the next page (or the first, when {@code first}) and appends it. */
    private void loadPage(boolean first) {
        if (loadingPage) return;
        if (!first && items.size() >= totalHits) return; // everything is loaded
        loadingPage = true;
        spinner.setVisible(true);
        if (first) status.setText("Searching…");

        final String q = currentQuery;
        final int offset = items.size();
        async(() -> {
            ModrinthApi.SearchResult res = api.search(kind.projectType, q, loaderId, inst.mcVersion, PAGE, offset);
            Platform.runLater(() -> {
                if (!q.equals(currentQuery)) { loadingPage = false; return; } // a newer search supersedes this
                totalHits = res.totalHits();
                items.addAll(res.hits());
                if (res.hits().isEmpty()) totalHits = items.size(); // freeze if the source stops paging
                loadingPage = false;
                spinner.setVisible(false);
                updateStatus(q);
                if (first) Platform.runLater(this::attachInfiniteScroll);
            });
        }, e -> {
            loadingPage = false;
            spinner.setVisible(false);
            status.setText("Search failed: " + e.getMessage());
        });
    }

    private void updateStatus(String q) {
        if (items.isEmpty()) {
            status.setText("Nothing found for " + filterText
                    + (q.isBlank() ? "." : " matching “" + q + "”."));
        } else if (items.size() >= totalHits) {
            status.setText("All " + totalHits + " result" + (totalHits == 1 ? "" : "s")
                    + "  ·  " + filterText);
        } else {
            status.setText("Showing " + items.size() + " of " + totalHits
                    + "  ·  " + filterText + "  ·  scroll for more");
        }
    }

    /** Auto-loads the next page when the user scrolls near the bottom of the list. */
    private void attachInfiniteScroll() {
        if (scrollAttached) return;
        ScrollBar bar = verticalScrollBar();
        if (bar == null) {
            // The list skin/scrollbar may not exist on the first pulse — retry briefly.
            if (!items.isEmpty() && scrollTries++ < 30) Platform.runLater(this::attachInfiniteScroll);
            return;
        }
        scrollAttached = true;
        bar.valueProperty().addListener((o, ov, nv) -> {
            if (bar.getMax() > 0 && nv.doubleValue() >= bar.getMax() * 0.9) loadPage(false);
        });
    }

    private ScrollBar verticalScrollBar() {
        for (Node n : results.lookupAll(".scroll-bar")) {
            if (n instanceof ScrollBar sb && sb.getOrientation() == javafx.geometry.Orientation.VERTICAL) return sb;
        }
        return null;
    }

    // ── install (with automatic dependency resolution for mods) ──────────

    private void install(ModrinthApi.Project p) {
        if (busy) return;
        busy = true;
        spinner.setVisible(true);
        results.refresh();
        status.setText("Resolving the best build of " + p.title() + "…");
        async(() -> {
            ModrinthApi.Version best = api.bestVersion(p.id(), loaderId, inst.mcVersion);
            if (best == null) best = api.bestVersion(p.id(), loaderId, null); // fall back to any version
            if (best == null) {
                finishInstall(() -> status.setText("No compatible build of " + p.title() + " for " + filterText + "."));
                return;
            }
            ModrinthInstaller.Result r = installer.install(inst, kind.dir.apply(inst.id), best, loaderId,
                    msg -> Platform.runLater(() -> status.setText(msg)));
            finishInstall(() -> {
                int total = r.installed() + r.dependencies();
                if (total == 0) {
                    status.setText("✓ " + p.title() + " is already installed.");
                } else {
                    String dep = r.dependencies() == 0 ? ""
                            : "  (+ " + r.dependencies() + " dependenc" + (r.dependencies() == 1 ? "y" : "ies") + ")";
                    status.setText("✓ Installed " + p.title() + dep + ".");
                }
                onChanged.run();
            });
        }, e -> finishInstall(() -> status.setText("Install failed: " + e.getMessage())));
    }

    /** Re-enables the UI and runs {@code ui} on the FX thread after an install. */
    private void finishInstall(Runnable ui) {
        Platform.runLater(() -> {
            busy = false;
            spinner.setVisible(false);
            results.refresh();
            ui.run();
        });
    }

    /** Runs {@code task} off the FX thread; {@code onError} runs on the FX thread on failure. */
    private void async(ThrowingRunnable task, Consumer<Exception> onError) {
        Thread t = new Thread(() -> {
            try {
                task.run();
            } catch (Exception e) {
                Platform.runLater(() -> onError.accept(e));
            }
        }, "luminamc-browser");
        t.setDaemon(true);
        t.start();
    }

    // ── result row ───────────────────────────────────────────────────────

    private final class HitCell extends ListCell<ModrinthApi.Project> {
        @Override
        protected void updateItem(ModrinthApi.Project p, boolean empty) {
            super.updateItem(p, empty);
            if (empty || p == null) { setGraphic(null); return; }

            ImageView icon = new ImageView();
            icon.setFitWidth(40);
            icon.setFitHeight(40);
            icon.setPreserveRatio(true);

            // Monogram shown only while/if no real icon is available.
            Label mono = new Label(monogram(p.title()));
            mono.getStyleClass().add("mod-icon-mono");
            mono.visibleProperty().bind(icon.imageProperty().isNull());

            StackPane iconBox = new StackPane(mono, icon);
            iconBox.getStyleClass().add("mod-icon");
            iconBox.setMinSize(44, 44);
            iconBox.setPrefSize(44, 44);
            iconBox.setMaxSize(44, 44);

            IconLoader.into(icon, p.iconUrl());

            Label title = new Label(p.title());
            title.getStyleClass().add("row-label");
            Label meta = FxUi.muted("by " + p.author() + "  ·  " + formatDownloads(p.downloads()) + " downloads");
            Label desc = FxUi.muted(p.description());
            desc.setWrapText(true);
            desc.setMaxWidth(380);
            VBox text = new VBox(2, title, meta, desc);
            HBox.setHgrow(text, Priority.ALWAYS);

            Button install = new Button(busy ? "…" : "Install");
            install.getStyleClass().add("accent-button");
            install.setDisable(busy);
            install.setMinWidth(86);
            install.setOnAction(e -> install(p));

            HBox row = new HBox(12, iconBox, text, install);
            row.setAlignment(Pos.CENTER_LEFT);
            setGraphic(row);
        }
    }

    /** First letter of the title, for the placeholder shown when a project has no icon. */
    private static String monogram(String title) {
        if (title == null || title.isBlank()) return "?";
        return title.strip().substring(0, 1).toUpperCase();
    }

    private static String formatDownloads(long n) {
        if (n >= 1_000_000) return String.format("%.1fM", n / 1_000_000.0);
        if (n >= 1_000)     return String.format("%.1fk", n / 1_000.0);
        return Long.toString(n);
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Exception; }
}
