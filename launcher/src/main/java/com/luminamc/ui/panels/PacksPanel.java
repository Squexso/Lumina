package com.luminamc.ui.panels;

import com.luminamc.config.LuminaPaths;
import com.luminamc.instance.Instance;
import com.luminamc.instance.Pack;
import com.luminamc.ui.AppContext;
import com.luminamc.ui.ContentBrowserDialog;
import com.luminamc.ui.FxUi;
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
import java.util.function.Function;

/**
 * Shared tab body for managing a folder of packs — used for the
 * <em>Resource Packs</em>, <em>Texture Packs</em> and <em>Shaders</em> tabs.
 * Shows the files already present in the instance's pack folder and lets you add
 * them (button, drag &amp; drop, or the online "Find…" browser), open the folder,
 * or remove individual packs.
 */
public final class PacksPanel extends VBox {

    /** Distinguishes the pack folders, their wording, and the matching online browser. */
    public enum Kind {
        RESOURCE("Resource Packs", "🎨",
                LuminaPaths::instanceResourcePacks, true,
                "Resource packs change textures, models, sounds and the UI. Drag & drop .zip packs "
                        + "(or unpacked pack folders) here, then enable them in-game under "
                        + "Options → Resource Packs.",
                "No resource packs yet",
                "Drag .zip packs here, or click “Add file…” — or “Find resource packs…”.",
                "🎨  Find resource packs…", ContentBrowserDialog.Kind.RESOURCE),
        SHADER("Shaders", "✨",
                LuminaPaths::instanceShaderPacks, true,
                "Shader packs (.zip or unpacked folders) for Iris / OptiFine. Drag & drop them here, "
                        + "then select one in-game under Options → Video Settings → Shaders.",
                "No shaders yet",
                "Drag .zip shader packs here, or click “Add file…” — or “Find shaders…”.",
                "✨  Find shaders…", ContentBrowserDialog.Kind.SHADER);

        final String title;
        final String icon;
        final Function<String, Path> dir;
        final boolean allowFolders;
        final String hint;
        final String emptyTitle;
        final String emptySub;
        final String findLabel;
        final ContentBrowserDialog.Kind browser;

        Kind(String title, String icon, Function<String, Path> dir, boolean allowFolders,
             String hint, String emptyTitle, String emptySub,
             String findLabel, ContentBrowserDialog.Kind browser) {
            this.title = title;
            this.icon = icon;
            this.dir = dir;
            this.allowFolders = allowFolders;
            this.hint = hint;
            this.emptyTitle = emptyTitle;
            this.emptySub = emptySub;
            this.findLabel = findLabel;
            this.browser = browser;
        }
    }

    private final AppContext ctx;
    private final Instance inst;
    private final Kind kind;
    private final ListView<Pack> list = new ListView<>();
    private final Label countLabel = new Label();

    public PacksPanel(AppContext ctx, Instance inst, Kind kind) {
        this.ctx = ctx;
        this.inst = inst;
        this.kind = kind;
        setSpacing(14);
        setPadding(new Insets(24));

        countLabel.getStyleClass().add("badge");

        Button openFolder = new Button("🗀  Open folder");
        openFolder.getStyleClass().add("ghost-button");
        openFolder.setOnAction(e -> openPacksFolder());

        Button addFile = new Button("＋  Add file…");
        addFile.getStyleClass().add("ghost-button");
        addFile.setOnAction(e -> choosePacks());

        Button find = new Button(kind.findLabel);
        find.getStyleClass().add("accent-button");
        find.setOnAction(e -> openBrowser());

        HBox header = new HBox(12, FxUi.h1(kind.title), countLabel, FxUi.hgrow(), openFolder, addFile, find);
        header.setAlignment(Pos.CENTER_LEFT);

        Label hint = FxUi.muted(kind.hint);
        list.getStyleClass().add("mod-list");
        list.setCellFactory(v -> new PackCell());
        list.setPlaceholder(emptyState());
        VBox.setVgrow(list, Priority.ALWAYS);

        getChildren().addAll(header, hint, list);
        setupDragAndDrop();
        refresh();
    }

    private Path dir() {
        return kind.dir.apply(inst.id);
    }

    /** Friendly drop-zone shown when the folder is empty. */
    private VBox emptyState() {
        Label icon = new Label(kind.icon);
        icon.setStyle("-fx-font-size: 40px; -fx-opacity: 0.7;");
        Label l1 = new Label(kind.emptyTitle);
        l1.getStyleClass().add("row-label");
        Label l2 = FxUi.muted(kind.emptySub);
        l2.setStyle("-fx-text-alignment: center;");
        VBox box = new VBox(8, icon, l1, l2);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(40));
        return box;
    }

    public void refresh() {
        var packs = ctx.instances.listPacks(dir(), kind.allowFolders);
        list.setItems(FXCollections.observableArrayList(packs));
        int n = packs.size();
        countLabel.setText(n == 0 ? "none" : n + (n == 1 ? " pack" : " packs"));
    }

    /** Opens the in-launcher Modrinth browser for this content type. */
    private void openBrowser() {
        var owner = getScene() != null ? getScene().getWindow() : null;
        new ContentBrowserDialog(ctx, inst, kind.browser).show(owner, this::refresh);
    }

    private void openPacksFolder() {
        Path dir = dir();
        try { java.nio.file.Files.createDirectories(dir); } catch (Exception ignored) {}
        new Thread(() -> {
            try {
                if (java.awt.Desktop.isDesktopSupported())
                    java.awt.Desktop.getDesktop().open(dir.toFile());
            } catch (Exception ignored) {}
        }, "luminamc-open-packs").start();
    }

    private void choosePacks() {
        FileChooser fc = new FileChooser();
        fc.setTitle("Add " + kind.title.toLowerCase() + " to " + inst.name);
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter("Pack archives (*.zip)", "*.zip"));

        // Open directly in the instance's pack folder (create it if absent).
        Path dir = dir();
        try { java.nio.file.Files.createDirectories(dir); } catch (Exception ignored) {}
        File initial = dir.toFile();
        if (!initial.isDirectory()) initial = new File(System.getProperty("user.home"));
        fc.setInitialDirectory(initial);

        List<File> files = fc.showOpenMultipleDialog(getScene().getWindow());
        if (files == null) return;
        for (File f : files) addPack(f.toPath());
        refresh();
    }

    private void addPack(Path source) {
        try {
            ctx.instances.addPack(dir(), source);
        } catch (Exception e) {
            com.luminamc.ui.LuminaDialog.error(
                    getScene() != null ? getScene().getWindow() : null,
                    "Couldn't add pack",
                    "Could not add " + source.getFileName() + ":\n" + e.getMessage());
        }
    }

    private boolean accepts(File f) {
        if (f.isDirectory()) return kind.allowFolders;
        return f.getName().toLowerCase().endsWith(".zip");
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
                    if (accepts(f)) { addPack(f.toPath()); ok = true; }
                }
                refresh();
            }
            getStyleClass().remove("drop-active");
            e.setDropCompleted(ok);
            e.consume();
        });
    }

    /** Row: pack name, size or "Folder", and a remove button. */
    private final class PackCell extends ListCell<Pack> {
        @Override
        protected void updateItem(Pack item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) { setGraphic(null); return; }

            Label name = new Label(item.displayName());
            name.getStyleClass().add("row-label");
            Label meta = FxUi.muted(item.isFolder() ? "Folder" : formatSize(item.sizeBytes()));
            VBox text = new VBox(1, name, meta);

            Button remove = new Button("✕");
            remove.getStyleClass().add("icon-button");
            remove.setOnAction(e -> {
                try { ctx.instances.removePack(item); } catch (Exception ignored) {}
                refresh();
            });

            HBox row = new HBox(12, text, FxUi.hgrow(), remove);
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
