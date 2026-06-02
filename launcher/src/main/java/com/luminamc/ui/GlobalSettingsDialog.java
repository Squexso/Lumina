package com.luminamc.ui;

import com.luminamc.javart.JavaRuntime;
import com.luminamc.update.SelfUpdater;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

/** Global launcher settings: default Java/RAM, downloads, accent, keybinds, updates. */
public final class GlobalSettingsDialog {

    private final AppContext ctx;
    private final Stage stage = new Stage(javafx.stage.StageStyle.TRANSPARENT);

    public GlobalSettingsDialog(AppContext ctx) {
        this.ctx = ctx;
    }

    /** Shows the settings as a frameless modal window (no white OS chrome). */
    public void show(Window owner) {
        ScrollPane scroll = buildScroll(true);
        scroll.getStyleClass().add("account-scroll");   // solid bg inside the card
        VBox.setVgrow(scroll, Priority.ALWAYS);

        Label titleLabel = new Label("✦  Settings");
        titleLabel.getStyleClass().add("dialog-title");
        Button x = new Button("✕");
        x.getStyleClass().add("icon-button");
        x.setOnAction(e -> stage.close());
        HBox header = new HBox(10, titleLabel, FxUi.hgrow(), x);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(2, 2, 6, 4));

        VBox outer = new VBox(0, header, scroll);
        outer.getStyleClass().add("lumina-dialog");
        outer.setPadding(new Insets(14, 14, 12, 14));

        final double[] drag = new double[2];
        header.setOnMousePressed(e -> { drag[0] = e.getScreenX() - stage.getX(); drag[1] = e.getScreenY() - stage.getY(); });
        header.setOnMouseDragged(e -> { stage.setX(e.getScreenX() - drag[0]); stage.setY(e.getScreenY() - drag[1]); });

        Scene scene = new Scene(outer, 540, 720);
        scene.setFill(Color.TRANSPARENT);
        Theme.apply(scene);
        Theme.applyAccent(scene);
        stage.setScene(scene);
        stage.setTitle("Settings — LuminaMC");
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setResizable(false);
        stage.setOnShown(e -> {
            if (owner != null) {
                stage.setX(owner.getX() + (owner.getWidth() - outer.getWidth()) / 2);
                stage.setY(owner.getY() + (owner.getHeight() - outer.getHeight()) / 2);
            }
        });
        stage.show();
    }

    /** Returns the same settings UI as an embeddable panel (for the SETTINGS tab). */
    public javafx.scene.Node asPanel() {
        return buildScroll(false);
    }

    private ScrollPane buildScroll(boolean asWindow) {
        var cfg = ctx.config;

        // Default Java.
        ComboBox<String> java = new ComboBox<>();
        java.getItems().add("Auto (recommended)");
        java.getItems().add("Custom path…");
        for (JavaRuntime rt : ctx.runtimes) java.getItems().add(rt.label() + "  →  " + rt.executable);
        TextField javaPath = new TextField(cfg.defaultJavaPath == null ? "" : cfg.defaultJavaPath);
        javaPath.setPromptText("Path to java / JDK home");
        java.getSelectionModel().select(cfg.defaultJavaPath == null ? 0 : 1);
        javaPath.textProperty().addListener((o, a, b) -> cfg.defaultJavaPath = b.isBlank() ? null : b);

        // Default RAM.
        Slider ramMin = ramSlider(cfg.defaultRamMinMb);
        Slider ramMax = ramSlider(cfg.defaultRamMaxMb);
        Label minVal = new Label(cfg.defaultRamMinMb + " MB");
        Label maxVal = new Label(cfg.defaultRamMaxMb + " MB");
        ramMin.valueProperty().addListener((o, a, b) -> { cfg.defaultRamMinMb = round(b.doubleValue()); minVal.setText(cfg.defaultRamMinMb + " MB"); });
        ramMax.valueProperty().addListener((o, a, b) -> { cfg.defaultRamMaxMb = round(b.doubleValue()); maxVal.setText(cfg.defaultRamMaxMb + " MB"); });

        // Downloads.
        Spinner<Integer> threads = new Spinner<>(1, 32, cfg.downloadThreads);
        threads.valueProperty().addListener((o, a, b) -> cfg.downloadThreads = b);

        // Accent.
        ColorPicker accent = new ColorPicker(safeColor(cfg.accentColor));
        accent.valueProperty().addListener((o, a, b) -> {
            cfg.accentColor = toHex(b);
            if (accent.getScene() != null) Theme.applyAccent(accent.getScene(), cfg.accentColor);
        });

        // Behavior.
        CheckBox closeOnLaunch = new CheckBox("Hide launcher while the game runs");
        closeOnLaunch.setSelected(cfg.closeLauncherOnLaunch);
        closeOnLaunch.selectedProperty().addListener((o, a, b) -> cfg.closeLauncherOnLaunch = b);

        // Keybinds.
        TextField overlayKey = new TextField(cfg.defaultOverlayKey);
        overlayKey.setOnKeyPressed(e -> { overlayKey.setText(e.getCode().getName().toUpperCase().replace(' ', '_')); e.consume(); });
        overlayKey.textProperty().addListener((o, a, b) -> cfg.defaultOverlayKey = b);

        // Updates.
        Label updateStatus = FxUi.muted("LuminaMC " + SelfUpdater.CURRENT_VERSION);
        Button checkUpdates = new Button("Check for updates");
        checkUpdates.getStyleClass().add("ghost-button");
        checkUpdates.setOnAction(e -> checkUpdates(updateStatus));

        CheckBox autoUpdate = new CheckBox("Check for updates automatically on launch");
        autoUpdate.setSelected(cfg.autoUpdate);
        autoUpdate.selectedProperty().addListener((o, a, b) -> cfg.autoUpdate = b);

        TextField feedUrl = new TextField(cfg.updateFeedUrl);
        feedUrl.setPromptText("http://your-server:8770/latest.json");
        feedUrl.textProperty().addListener((o, a, b) -> cfg.updateFeedUrl = b.trim());

        // Storage cleanup.
        Label storageStatus = FxUi.muted("Checking reclaimable space…");
        Button cleanBtn = new Button("🧹  Clean caches");
        cleanBtn.getStyleClass().add("ghost-button");
        cleanBtn.setOnAction(e -> {
            cleanBtn.setDisable(true);
            storageStatus.setText("Cleaning…");
            new Thread(() -> {
                long freed = com.luminamc.config.StorageCleaner.cleanCaches();
                Platform.runLater(() -> {
                    storageStatus.setText("Freed " + com.luminamc.config.StorageCleaner.human(freed)
                            + ". (Caches are re-created automatically when needed.)");
                    cleanBtn.setDisable(false);
                });
            }, "luminamc-clean").start();
        });
        new Thread(() -> {
            long r = com.luminamc.config.StorageCleaner.reclaimable();
            Platform.runLater(() -> storageStatus.setText("~" + com.luminamc.config.StorageCleaner.human(r)
                    + " of caches (installers, natives) can be safely cleared."));
        }, "luminamc-reclaim").start();

        Label savedHint = FxUi.muted("");
        Button done = new Button(asWindow ? "Done" : "Save settings");
        done.getStyleClass().add("accent-button");
        done.setOnAction(e -> {
            cfg.save();
            ctx.refreshRuntimes();
            if (asWindow) stage.close();
            else { savedHint.setText("Saved ✓"); }
        });

        VBox content = new VBox(16,
                FxUi.h1("Settings"),
                FxUi.card(FxUi.sectionTitle("Default Java runtime"), java,
                        labeled("Custom path", javaPath)),
                FxUi.card(FxUi.sectionTitle("Default memory"),
                        labeled("Minimum", sliderRow(ramMin, minVal)),
                        labeled("Maximum", sliderRow(ramMax, maxVal))),
                FxUi.card(FxUi.sectionTitle("Downloads"),
                        labeled("Parallel download threads", threads)),
                FxUi.card(FxUi.sectionTitle("Appearance & behavior"),
                        labeled("Accent color", accent), closeOnLaunch),
                FxUi.card(FxUi.sectionTitle("Global keybinds"),
                        labeled("Open in-game overlay", overlayKey),
                        FxUi.muted("Click the field and press a key. Default: Right Shift.")),
                FxUi.card(FxUi.sectionTitle("Storage"), new HBox(12, cleanBtn), storageStatus),
                FxUi.card(FxUi.sectionTitle("Updates"),
                        new HBox(12, checkUpdates, updateStatus),
                        autoUpdate,
                        labeled("Update source URL", feedUrl),
                        FxUi.muted("Where the launcher fetches new versions. Run  gradlew -p launcher serveUpdates  "
                                + "to host them, or point this at any web URL serving latest.json.")),
                new HBox(12, FxUi.hgrow(), savedHint, done));
        content.setPadding(new Insets(24));

        ScrollPane scroll = new ScrollPane(content);
        scroll.setFitToWidth(true);
        scroll.getStyleClass().add(asWindow ? "content-scroll" : "cosmic-scroll");
        return scroll;
    }

    private void checkUpdates(Label status) {
        status.setText("Checking…");
        Window owner = status.getScene() != null ? status.getScene().getWindow() : null;
        // Save first so the check uses the URL the user just typed.
        ctx.config.save();
        UpdateFlow.checkManually(owner, ctx,
                () -> status.setText("You're up to date (" + SelfUpdater.CURRENT_VERSION + ")."));
    }

    private Slider ramSlider(int v) {
        Slider s = new Slider(512, 32768, v);
        s.setMajorTickUnit(4096);
        HBox.setHgrow(s, Priority.ALWAYS);
        return s;
    }

    private HBox sliderRow(Slider s, Label v) {
        v.setMinWidth(80);
        HBox row = new HBox(12, s, v);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private VBox labeled(String label, javafx.scene.Node control) {
        Label l = new Label(label);
        l.getStyleClass().add("field-label");
        return new VBox(6, l, control);
    }

    private static int round(double mb) { return (int) (Math.round(mb / 256.0) * 256); }

    private static Color safeColor(String hex) {
        try { return Color.web(hex); } catch (Exception e) { return Color.web(Theme.ACCENT); }
    }

    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }
}
