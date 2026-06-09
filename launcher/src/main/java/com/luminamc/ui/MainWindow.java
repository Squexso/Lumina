package com.luminamc.ui;

import com.luminamc.config.LuminaPaths;
import com.luminamc.instance.Instance;
import com.luminamc.ui.panels.*;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Window;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Path;

/**
 * Root application shell with the cosmic look: a star-field background behind a
 * sidebar + a content area that swaps between the instance grid (home) and a
 * per-instance detail view (Overview / Mods / Resource Packs / Shaders /
 * Settings / Performance tabs).
 */
public final class MainWindow {

    private static final String[] TABS =
            {"Overview", "Mods", "Resource Packs", "Shaders", "Settings", "Performance"};

    private final AppContext ctx;
    private final StackPane root = new StackPane();
    private final StackPane centerHost = new StackPane();
    private final Sidebar sidebar;
    private final InstanceGridPanel gridPanel;

    // Detail-view state.
    private Instance current;
    private String activeTab = "Overview";

    public MainWindow(AppContext ctx) {
        this.ctx = ctx;

        sidebar = new Sidebar(ctx, new Sidebar.Nav() {
            @Override public void home()          { showHome(); }
            @Override public void skins()         { showSkins(); }
            @Override public void shop()          { showShop(); }
            @Override public void settings()      { showSettings(); }
            @Override public void newInstance()   { newInstance(); }
            @Override public void account()       { accounts(); }
            @Override public void globalScreens() { showGlobalScreens(); }
            @Override public void crashLogs()     { showCrashLogs(); }
            @Override public void openInstance(Instance inst) { MainWindow.this.openInstance(inst); }
        });

        gridPanel = new InstanceGridPanel(ctx, this::openInstance, this::newInstance,
                deleted -> sidebar.refresh());

        centerHost.getStyleClass().add("center-host");

        BorderPane shell = new BorderPane();
        shell.setLeft(sidebar);
        shell.setCenter(centerHost);
        shell.getStyleClass().add("shell");

        // Cosmic background layers: gradient + star field, behind the shell.
        Region cosmic = new Region();
        cosmic.getStyleClass().add("cosmic-bg");
        com.luminamc.ui.components.StarField stars = new com.luminamc.ui.components.StarField();

        root.getChildren().addAll(cosmic, stars, shell);
        root.getStyleClass().add("app-root");

        showHome();
    }

    public Parent getRoot() {
        return root;
    }

    // ── home (instance grid) ─────────────────────────────────────────────

    /** Swaps the centre content with a soft fade + slide-up animation. */
    private void setContent(Node node) {
        centerHost.getChildren().setAll(node);
        node.setOpacity(0);
        node.setTranslateY(10);
        javafx.animation.FadeTransition fade =
                new javafx.animation.FadeTransition(javafx.util.Duration.millis(220), node);
        fade.setFromValue(0); fade.setToValue(1);
        javafx.animation.TranslateTransition slide =
                new javafx.animation.TranslateTransition(javafx.util.Duration.millis(280), node);
        slide.setFromY(10); slide.setToY(0);
        slide.setInterpolator(javafx.animation.Interpolator.EASE_OUT);
        new javafx.animation.ParallelTransition(fade, slide).play();
    }

    private void showHome() {
        current = null;
        sidebar.setActive("home");
        sidebar.refresh();          // keep the "recent" list and account chip current
        gridPanel.rebuild();
        setContent(gridPanel);
    }

    private void showSkins() {
        sidebar.setActive("skins");
        current = null;
        setContent(new SkinsPanel(ctx));
    }

    private void showShop() {
        sidebar.setActive("shop");
        current = null;
        setContent(new ShopPanel(ctx, sidebar::refreshTokens));
    }

    private void showSettings() {
        sidebar.setActive("settings");
        current = null;
        setContent(new GlobalSettingsDialog(ctx).asPanel());
    }

    private void showGlobalScreens() {
        sidebar.setActive(null);
        setContent(new GlobalScreensPanel(ctx));
    }

    private void showCrashLogs() {
        sidebar.setActive(null);
        setContent(new CrashLogsPanel(ctx));
    }

    // ── instance detail (tabs) ───────────────────────────────────────────

    private void openInstance(Instance inst) {
        this.current = inst;
        this.activeTab = "Overview";
        ctx.config.lastInstanceId = inst.id;
        ctx.config.save();
        buildDetail();
    }

    private void buildDetail() {
        if (current == null) { showHome(); return; }

        // Header: back button + instance name + badge.
        Button back = new Button("←  Instances");
        back.getStyleClass().add("ghost-button");
        back.setOnAction(e -> showHome());

        Label title = FxUi.h1(current.name);
        Label badge = new Label(current.isNextGenScheme()
                ? "Next-gen · " + current.mcVersion : "Classic · " + current.mcVersion);
        badge.getStyleClass().add("badge");

        HBox headerRow = new HBox(14, back, title, badge);
        headerRow.setAlignment(Pos.CENTER_LEFT);
        headerRow.setPadding(new Insets(24, 28, 4, 28));

        // Tab bar.
        HBox tabBar = new HBox(6);
        tabBar.getStyleClass().add("tab-bar");
        tabBar.setPadding(new Insets(8, 28, 0, 28));
        tabBar.setAlignment(Pos.CENTER_LEFT);
        ToggleGroup group = new ToggleGroup();
        for (String tab : TABS) {
            ToggleButton tb = new ToggleButton(tab);
            tb.getStyleClass().add("tab");
            tb.setToggleGroup(group);
            tb.setSelected(tab.equals(activeTab));
            tb.setOnAction(e -> {
                if (!tb.isSelected()) { tb.setSelected(true); return; }
                activeTab = tab;
                buildDetail();
            });
            tabBar.getChildren().add(tb);
        }

        // Active panel.
        Node panel = switch (activeTab) {
            case "Mods"           -> new ModsPanel(ctx, current);
            case "Resource Packs" -> new PacksPanel(ctx, current, PacksPanel.Kind.RESOURCE);
            case "Shaders"        -> new PacksPanel(ctx, current, PacksPanel.Kind.SHADER);
            case "Settings"       -> new SettingsPanel(ctx, current, () -> {}, this::onInstanceDeleted);
            case "Performance"    -> new PerformancePanel(ctx, current);
            default               -> new OverviewPanel(ctx, current, null, this::accounts);
        };
        ScrollPane scroll = new ScrollPane(panel);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add("cosmic-scroll");
        VBox.setVgrow(scroll, Priority.ALWAYS);

        VBox detail = new VBox(headerRow, tabBar, scroll);
        VBox.setVgrow(scroll, Priority.ALWAYS);
        setContent(detail);
    }

    private void onInstanceDeleted() {
        showHome();
    }

    // ── actions ─────────────────────────────────────────────────────────

    private void newInstance() {
        Window owner = root.getScene() != null ? root.getScene().getWindow() : null;
        new NewInstanceDialog(ctx).show(owner, created -> {
            created.features.overlayKey = ctx.config.defaultOverlayKey;
            created.ramMinMb = ctx.config.defaultRamMinMb;
            created.ramMaxMb = ctx.config.defaultRamMaxMb;
            ctx.instances.save(created);
            preinstallPerformanceMod(created);
            gridPanel.rebuild();
            openInstance(created);
        });
    }

    /**
     * Pre-optimizes a freshly created non-vanilla instance in the background: drops
     * in the loader-correct performance mod (Fabric → Sodium, Forge → Embeddium,
     * NeoForge → Sodium/Embeddium) plus, on Fabric, the Fabric API — so the instance
     * is ready-optimized from the very first launch. Silent and best-effort.
     */
    private void preinstallPerformanceMod(Instance inst) {
        if (inst.loader == com.luminamc.instance.ModLoader.VANILLA) return;
        if (inst.features == null || !inst.features.performanceMod) return;
        new Thread(() -> {
            try {
                if (inst.loader == com.luminamc.instance.ModLoader.FABRIC) {
                    new com.luminamc.game.FabricApiInstaller().ensure(inst, m -> {});
                }
                new com.luminamc.game.PerformanceModInstaller().ensure(
                        inst, m -> System.out.println("[new-instance optimize] " + m));
            } catch (Exception ignored) { /* best-effort; launch will retry */ }
        }, "luminamc-new-instance-optimize").start();
    }

    private void accounts() {
        Window owner = root.getScene() != null ? root.getScene().getWindow() : null;
        new AccountDialog(ctx).show(owner, sidebar::refreshAccount);
    }

    private void globalSettings() {
        Window owner = root.getScene() != null ? root.getScene().getWindow() : null;
        new GlobalSettingsDialog(ctx).show(owner);
    }

    private void openDir(Path dir) {
        try { LuminaPaths.mkdirs(dir); } catch (Exception ignored) {}
        new Thread(() -> {
            try {
                if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(new File(dir.toString()));
            } catch (Exception ignored) {}
        }, "luminamc-open").start();
    }
}
