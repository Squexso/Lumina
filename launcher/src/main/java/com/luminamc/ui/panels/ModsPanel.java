package com.luminamc.ui.panels;

import com.luminamc.instance.Instance;
import com.luminamc.instance.Mod;
import com.luminamc.ui.AppContext;
import com.luminamc.ui.FxUi;
import com.luminamc.ui.components.ToggleSwitch;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.DragEvent;
import javafx.scene.input.Dragboard;
import javafx.scene.input.TransferMode;
import javafx.scene.layout.*;
import javafx.stage.FileChooser;

import java.io.File;
import java.nio.file.Path;
import java.util.List;

/** Mods tab: list, enable/disable, remove, add via button or drag & drop. */
public final class ModsPanel extends VBox {

    private final AppContext ctx;
    private final Instance inst;
    private final ListView<Mod> list = new ListView<>();
    private final Label countLabel = new Label();
    private final Label statusLabel = FxUi.muted("");
    private Button optimizeBtn;

    public ModsPanel(AppContext ctx, Instance inst) {
        this.ctx = ctx;
        this.inst = inst;
        setSpacing(14);
        setPadding(new Insets(24));

        countLabel.getStyleClass().add("badge");

        Button openFolder = new Button("🗀  Open folder");
        openFolder.getStyleClass().add("ghost-button");
        openFolder.setOnAction(e -> openModsFolder());

        Button addFile = new Button("＋  Add file…");
        addFile.getStyleClass().add("ghost-button");
        addFile.setOnAction(e -> chooseMods());

        Button find = new Button("🔎  Find mods…");
        find.getStyleClass().add("accent-button");
        find.setOnAction(e -> openBrowser());

        // One-click: auto-install the loader-correct performance mod (Sodium /
        // Embeddium / Rubidium) + dependencies. Works for Fabric, Forge, NeoForge.
        optimizeBtn = new Button("⚡  Optimize");
        optimizeBtn.getStyleClass().add("ghost-button");
        Tooltip.install(optimizeBtn, new Tooltip(
                "Auto-install the best performance mod for this loader "
                + "(Fabric → Sodium, Forge → Embeddium, NeoForge → Sodium/Embeddium) + dependencies."));
        optimizeBtn.setOnAction(e -> optimize());

        HBox header = new HBox(12, FxUi.h1("Mods"), countLabel, FxUi.hgrow(), optimizeBtn, openFolder, addFile, find);
        header.setAlignment(Pos.CENTER_LEFT);
        getChildren().add(header);

        statusLabel.setWrapText(true);
        statusLabel.setVisible(false);
        statusLabel.setManaged(false);
        getChildren().add(statusLabel);

        if (!inst.loader.supportsMods()) {
            countLabel.setVisible(false);
            openFolder.setVisible(false);
            addFile.setDisable(true);
            find.setDisable(true);
            optimizeBtn.setDisable(true);
            getChildren().add(FxUi.muted("This is a " + inst.loader.displayName
                    + " instance. Switch the loader to Fabric, Forge or NeoForge in Settings to use mods."));
            return;
        }

        Label hint = FxUi.muted("Drag & drop .jar files anywhere here to add them. "
                + "Toggle a mod off to keep it without loading it (a .disabled suffix is added on disk).");
        list.getStyleClass().add("mod-list");
        list.setCellFactory(v -> new ModCell());
        list.setPlaceholder(emptyState());
        VBox.setVgrow(list, Priority.ALWAYS);

        getChildren().addAll(hint, list);
        setupDragAndDrop();
        refresh();
    }

    /** Friendly drop-zone shown when no mods are installed. */
    private VBox emptyState() {
        Label icon = new Label("🧩");
        icon.setStyle("-fx-font-size: 40px; -fx-opacity: 0.7;");
        Label l1 = new Label("No mods yet");
        l1.getStyleClass().add("row-label");
        Label l2 = FxUi.muted("Drag .jar files here, or click “Add mods…”.");
        l2.setStyle("-fx-text-alignment: center;");
        VBox box = new VBox(8, icon, l1, l2);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));
        return box;
    }

    public void refresh() {
        var mods = ctx.instances.listMods(inst);
        list.setItems(FXCollections.observableArrayList(mods));
        long enabled = mods.stream().filter(Mod::isEnabled).count();
        countLabel.setText(mods.isEmpty() ? "none"
                : enabled + " of " + mods.size() + " enabled");
    }

    private void openModsFolder() {
        Path dir = com.luminamc.config.LuminaPaths.instanceMods(inst.id);
        try { java.nio.file.Files.createDirectories(dir); } catch (Exception ignored) {}
        new Thread(() -> {
            try {
                if (java.awt.Desktop.isDesktopSupported())
                    java.awt.Desktop.getDesktop().open(dir.toFile());
            } catch (Exception ignored) {}
        }, "luminamc-open-mods").start();
    }

    /**
     * One-click optimize: installs the loader-correct performance mod
     * (Sodium / Embeddium / Rubidium) and, on Fabric, the Fabric API it builds on.
     * Runs off the FX thread; progress is shown in {@link #statusLabel}.
     */
    private void optimize() {
        optimizeBtn.setDisable(true);
        showStatus("Optimizing… installing the best performance mod for " + inst.loader.displayName + "…");
        new Thread(() -> {
            try {
                if (inst.loader == com.luminamc.instance.ModLoader.FABRIC) {
                    new com.luminamc.game.FabricApiInstaller().ensure(inst, this::asyncStatus);
                }
                new com.luminamc.game.PerformanceModInstaller().ensure(inst, this::asyncStatus);
                javafx.application.Platform.runLater(() -> {
                    showStatus("✓ Done — this instance is optimized.");
                    optimizeBtn.setDisable(false);
                    refresh();
                });
            } catch (Exception ex) {
                javafx.application.Platform.runLater(() -> {
                    showStatus("Optimize failed: " + ex.getMessage());
                    optimizeBtn.setDisable(false);
                });
            }
        }, "luminamc-optimize").start();
    }

    private void asyncStatus(String msg) { javafx.application.Platform.runLater(() -> showStatus(msg)); }

    private void showStatus(String msg) {
        statusLabel.setText(msg);
        statusLabel.setVisible(true);
        statusLabel.setManaged(true);
    }

    /** Opens the in-launcher Modrinth browser, refreshing the list after installs. */
    private void openBrowser() {
        var owner = getScene() != null ? getScene().getWindow() : null;
        new com.luminamc.ui.ContentBrowserDialog(ctx, inst, com.luminamc.ui.ContentBrowserDialog.Kind.MOD)
                .show(owner, this::refresh);
    }

    private void chooseMods() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Add mods to " + inst.name);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Mod jars (*.jar)", "*.jar"));

        // Open directly in the instance's mods folder (create it if absent).
        java.nio.file.Path modsDir = com.luminamc.config.LuminaPaths.instanceMods(inst.id);
        try { java.nio.file.Files.createDirectories(modsDir); } catch (Exception ignored) {}
        File initial = modsDir.toFile();
        // Fall back up the chain if the dir doesn't exist for some reason.
        if (!initial.isDirectory()) initial = modsDir.getParent().toFile();
        if (!initial.isDirectory()) initial = new File(System.getProperty("user.home"));
        fc.setInitialDirectory(initial);

        List<File> files = fc.showOpenMultipleDialog(getScene().getWindow());
        if (files == null) return;
        for (File f : files) addJar(f.toPath());
        refresh();
    }

    private void addJar(Path source) {
        try {
            ctx.instances.addMod(inst, source);
        } catch (Exception e) {
            com.luminamc.ui.LuminaDialog.error(
                    getScene() != null ? getScene().getWindow() : null,
                    "Couldn't add mod",
                    "Could not add " + source.getFileName() + ":\n" + e.getMessage());
        }
    }

    private void setupDragAndDrop() {
        setOnDragOver((DragEvent e) -> {
            if (e.getDragboard().hasFiles()) {
                e.acceptTransferModes(TransferMode.COPY);
                getStyleClass().add("drop-active");
            }
            e.consume();
        });
        setOnDragExited(e -> getStyleClass().remove("drop-active"));
        setOnDragDropped((DragEvent e) -> {
            Dragboard db = e.getDragboard();
            boolean ok = false;
            if (db.hasFiles()) {
                for (File f : db.getFiles()) {
                    if (f.getName().toLowerCase().endsWith(".jar")) { addJar(f.toPath()); ok = true; }
                }
                refresh();
            }
            getStyleClass().remove("drop-active");
            e.setDropCompleted(ok);
            e.consume();
        });
    }

    /** Row: name, size, enable switch and a remove button. */
    private final class ModCell extends ListCell<Mod> {
        @Override
        protected void updateItem(Mod item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) { setGraphic(null); return; }

            Label name = new Label(item.displayName());
            name.getStyleClass().add("row-label");
            Label meta = FxUi.muted(formatSize(item.sizeBytes()) + (item.isEnabled() ? "" : "  · disabled"));
            VBox text = new VBox(1, name, meta);

            ToggleSwitch sw = new ToggleSwitch(item.isEnabled());
            sw.selectedProperty().addListener((o, was, is) -> {
                try { ctx.instances.setModEnabled(item, is); }
                catch (Exception ex) { /* surfaced on refresh */ }
                refresh();
            });

            Button remove = new Button("✕");
            remove.getStyleClass().add("icon-button");
            remove.setOnAction(e -> {
                try { ctx.instances.removeMod(item); } catch (Exception ignored) {}
                refresh();
            });

            HBox row = new HBox(12, text, FxUi.hgrow(), sw, remove);
            row.setAlignment(Pos.CENTER_LEFT);
            setGraphic(row);
        }
    }

    private static String formatSize(long bytes) {
        if (bytes <= 0) return "—";
        if (bytes < 1024) return bytes + " B";
        if (bytes < 1024 * 1024) return String.format("%.0f KB", bytes / 1024.0);
        return String.format("%.1f MB", bytes / (1024.0 * 1024));
    }
}
