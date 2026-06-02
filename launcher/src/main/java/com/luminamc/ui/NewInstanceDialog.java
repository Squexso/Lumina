package com.luminamc.ui;

import com.luminamc.download.FabricMeta;
import com.luminamc.download.ForgeLikeMeta;
import com.luminamc.download.VersionManifest;
import com.luminamc.instance.Instance;
import com.luminamc.instance.ModLoader;
import javafx.application.Platform;
import javafx.collections.FXCollections;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.util.function.Consumer;

/** Modal for creating a new instance: name, version, loader and loader build. */
public final class NewInstanceDialog {

    private final AppContext ctx;
    private final Stage stage = new Stage(javafx.stage.StageStyle.TRANSPARENT);

    private final TextField name = new TextField();
    private final ComboBox<String> version = new ComboBox<>();
    private final CheckBox releasesOnly = new CheckBox("Releases only");
    private final ComboBox<ModLoader> loader = new ComboBox<>();
    private final ComboBox<String> loaderVersion = new ComboBox<>();
    private final Label status = new Label();
    private final Button create = new Button("Create");

    private VersionManifest manifest;

    public NewInstanceDialog(AppContext ctx) {
        this.ctx = ctx;
    }

    public void show(Window owner, Consumer<Instance> onCreated) {
        name.setPromptText("My instance");
        releasesOnly.setSelected(true);
        loader.setItems(FXCollections.observableArrayList(ModLoader.values()));
        loader.getSelectionModel().select(ModLoader.FABRIC);
        loaderVersion.setDisable(false);
        version.setPromptText("Loading versions…");
        loaderVersion.setPromptText("Select a version first");
        create.getStyleClass().add("accent-button");
        create.setDisable(true);

        GridPane form = new GridPane();
        form.setHgap(12);
        form.setVgap(12);
        form.add(label("Name"), 0, 0);            form.add(name, 1, 0);
        form.add(label("Version"), 0, 1);
        HBox versionRow = new HBox(10, version, releasesOnly);
        versionRow.setAlignment(Pos.CENTER_LEFT);
        form.add(versionRow, 1, 1);
        form.add(label("Mod loader"), 0, 2);      form.add(loader, 1, 2);
        form.add(label("Loader build"), 0, 3);    form.add(loaderVersion, 1, 3);
        version.setMaxWidth(Double.MAX_VALUE);
        loader.setMaxWidth(Double.MAX_VALUE);
        loaderVersion.setMaxWidth(Double.MAX_VALUE);
        GridPane.setHgrow(name, Priority.ALWAYS);

        status.getStyleClass().add("muted");

        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("ghost-button");
        cancel.setOnAction(e -> stage.close());
        HBox buttons = new HBox(10, FxUi.hgrow(), cancel, create);

        // Frameless Lumina header (title + close), instead of the OS title bar.
        Label titleLabel = new Label("✦  New instance");
        titleLabel.getStyleClass().add("dialog-title");
        Button x = new Button("✕");
        x.getStyleClass().add("icon-button");
        x.setOnAction(e -> stage.close());
        HBox header = new HBox(10, titleLabel, FxUi.hgrow(), x);
        header.setAlignment(Pos.CENTER_LEFT);

        VBox root = new VBox(16, header, form, status, buttons);
        root.getStyleClass().add("lumina-dialog");
        root.setPadding(new Insets(22, 24, 20, 24));
        root.setPrefWidth(460);

        // Drag the frameless window by its body.
        final double[] drag = new double[2];
        root.setOnMousePressed(e -> { drag[0] = e.getScreenX() - stage.getX(); drag[1] = e.getScreenY() - stage.getY(); });
        root.setOnMouseDragged(e -> { stage.setX(e.getScreenX() - drag[0]); stage.setY(e.getScreenY() - drag[1]); });

        wireEvents(onCreated);

        Scene scene = new Scene(root);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        Theme.apply(scene);
        Theme.applyAccent(scene, ctx.config.accentColor);
        stage.setScene(scene);
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setResizable(false);
        stage.setOnShown(e -> {
            if (owner != null) {
                stage.setX(owner.getX() + (owner.getWidth() - root.getWidth()) / 2);
                stage.setY(owner.getY() + (owner.getHeight() - root.getHeight()) / 2);
            }
        });
        stage.show();

        loadVersions();
    }

    private void wireEvents(Consumer<Instance> onCreated) {
        releasesOnly.selectedProperty().addListener((o, a, b) -> populateVersions());
        version.valueProperty().addListener((o, a, b) -> { reloadLoaderVersions(); validate(); });
        loader.valueProperty().addListener((o, a, b) -> { reloadLoaderVersions(); validate(); });
        name.textProperty().addListener((o, a, b) -> validate());
        loaderVersion.valueProperty().addListener((o, a, b) -> validate());

        create.setOnAction(e -> {
            String nm = name.getText().isBlank() ? version.getValue() + " " + loader.getValue() : name.getText().trim();
            Instance i = ctx.instances.create(nm, version.getValue(), loader.getValue());
            if (loader.getValue() != ModLoader.VANILLA) i.loaderVersion = loaderVersion.getValue();
            ctx.instances.save(i);
            stage.close();
            onCreated.accept(i);
        });
    }

    private void validate() {
        boolean ok = version.getValue() != null
                && (loader.getValue() == ModLoader.VANILLA || loaderVersion.getValue() != null);
        create.setDisable(!ok);
    }

    private void loadVersions() {
        background(() -> {
            manifest = VersionManifest.fetch();
            Platform.runLater(this::populateVersions);
        }, "Could not load versions");
    }

    private void populateVersions() {
        if (manifest == null) return;
        var items = FXCollections.<String>observableArrayList();
        for (VersionManifest.Entry e : manifest.versions) {
            if (releasesOnly.isSelected() && !e.isRelease()) continue;
            items.add(e.id);
        }
        version.setItems(items);
        version.setPromptText("Select version");
        if (manifest.latestRelease != null && items.contains(manifest.latestRelease)) {
            version.getSelectionModel().select(manifest.latestRelease);
        } else if (!items.isEmpty()) {
            version.getSelectionModel().select(0);
        }
    }

    private void reloadLoaderVersions() {
        ModLoader ml = loader.getValue();
        String mc = version.getValue();
        loaderVersion.getItems().clear();
        if (ml == null || ml == ModLoader.VANILLA || mc == null) {
            loaderVersion.setDisable(ml == ModLoader.VANILLA);
            return;
        }
        loaderVersion.setDisable(false);
        loaderVersion.setPromptText("Loading…");
        status.setText("Fetching " + ml.displayName + " builds for " + mc + "…");
        background(() -> {
            java.util.List<String> builds;
            if (ml == ModLoader.FABRIC) {
                builds = new java.util.ArrayList<>();
                for (FabricMeta.Loader l : new FabricMeta().listLoaders(mc)) builds.add(l.version);
            } else {
                builds = new ForgeLikeMeta().listVersions(ml, mc);
            }
            Platform.runLater(() -> {
                loaderVersion.setItems(FXCollections.observableArrayList(builds));
                if (!builds.isEmpty()) loaderVersion.getSelectionModel().select(0);
                loaderVersion.setPromptText(builds.isEmpty() ? "None found" : "Select build");
                status.setText(builds.isEmpty()
                        ? "No " + ml.displayName + " builds found for " + mc + "."
                        : "");
                validate();
            });
        }, "Could not load loader builds");
    }

    private void background(ThrowingRunnable task, String errorPrefix) {
        Thread t = new Thread(() -> {
            try {
                task.run();
            } catch (Exception e) {
                Platform.runLater(() -> status.setText(errorPrefix + ": " + e.getMessage()));
            }
        }, "luminamc-dialog");
        t.setDaemon(true);
        t.start();
    }

    private Label label(String s) {
        Label l = new Label(s);
        l.getStyleClass().add("field-label");
        return l;
    }

    @FunctionalInterface
    private interface ThrowingRunnable { void run() throws Exception; }
}
