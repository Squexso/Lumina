package com.luminamc.ui;

import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.ColorPicker;
import javafx.scene.control.Label;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.util.ArrayList;
import java.util.List;

/**
 * The "Appearance" panel opened by clicking the sidebar logo: accent colour
 * swatches, clean background presets and the star-field toggle. Every change
 * applies to the main window instantly and is persisted right away — no Save
 * button needed, closing just dismisses the panel.
 */
public final class AppearanceDialog {

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    /** Curated accents that all sit well on the dark backgrounds. */
    private static final String[][] ACCENTS = {
            {"#7C3AED", "Violet"}, {"#6366F1", "Indigo"}, {"#3B82F6", "Blue"},
            {"#06B6D4", "Cyan"},   {"#10B981", "Emerald"}, {"#F59E0B", "Amber"},
            {"#F43F5E", "Rose"},   {"#EC4899", "Pink"}};

    private final AppContext ctx;
    private final List<StackPane> swatches = new ArrayList<>();
    private final List<VBox> tiles = new ArrayList<>();
    private Window owner;
    private Scene dialogScene;

    public AppearanceDialog(AppContext ctx) {
        this.ctx = ctx;
    }

    public void show(Window owner) {
        this.owner = owner;
        Stage stage = new Stage(StageStyle.TRANSPARENT);

        Label title = new Label("Appearance");
        title.getStyleClass().add("dialog-title");
        Button close = new Button("✕");
        close.getStyleClass().add("card-icon-btn");
        close.setOnAction(e -> stage.close());
        HBox header = new HBox(10, title, FxUi.hgrow(), close);
        header.setAlignment(Pos.CENTER_LEFT);

        Label sub = FxUi.muted("Pick an accent and a background — changes apply instantly.");

        // ── accent swatches ──
        FlowPane accentRow = new FlowPane(10, 10);
        for (String[] a : ACCENTS) accentRow.getChildren().add(swatch(a[0], a[1]));

        ColorPicker custom = new ColorPicker(safeColor(ctx.config.accentColor));
        custom.valueProperty().addListener((o, was, c) -> {
            if (c != null) applyAccent(toHex(c));
        });
        Label customLabel = FxUi.muted("Custom");
        HBox customRow = new HBox(10, customLabel, custom);
        customRow.setAlignment(Pos.CENTER_LEFT);

        // ── background tiles ──
        FlowPane bgRow = new FlowPane(12, 12);
        for (Theme.Bg bg : Theme.BACKGROUNDS) bgRow.getChildren().add(tile(bg));

        // ── star field ──
        CheckBox stars = new CheckBox("Animated star field");
        stars.setSelected(ctx.config.showStars);
        stars.setOnAction(e -> {
            ctx.config.showStars = stars.isSelected();
            ctx.config.save();
            applyStars();
        });

        // ── footer ──
        Button reset = new Button("Reset to default");
        reset.getStyleClass().add("ghost-button");
        reset.setOnAction(e -> {
            applyAccent("#7C3AED");
            applyBackground("nebula");
            ctx.config.showStars = true;
            ctx.config.save();
            applyStars();
            stars.setSelected(true);
            custom.setValue(safeColor("#7C3AED"));
        });
        Button done = new Button("Done");
        done.getStyleClass().add("accent-button");
        done.setDefaultButton(true);
        done.setOnAction(e -> stage.close());
        HBox footer = new HBox(10, reset, FxUi.hgrow(), done);
        footer.setAlignment(Pos.CENTER_RIGHT);

        VBox panel = new VBox(14,
                header, sub,
                FxUi.sectionTitle("ACCENT"), accentRow, customRow,
                FxUi.sectionTitle("BACKGROUND"), bgRow, stars,
                footer);
        panel.getStyleClass().add("lumina-dialog");
        panel.setPadding(new Insets(22));
        panel.setPrefWidth(480);

        refreshSelection();

        dialogScene = new Scene(panel, Color.TRANSPARENT);
        Theme.apply(dialogScene);
        Theme.applyAccent(dialogScene);
        dialogScene.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ESCAPE) stage.close(); });

        stage.setScene(dialogScene);
        stage.setTitle("Appearance — LuminaMC");
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setOnShown(e -> {
            if (owner != null) {
                stage.setX(owner.getX() + (owner.getWidth() - stage.getWidth()) / 2);
                stage.setY(owner.getY() + (owner.getHeight() - stage.getHeight()) / 2);
            }
        });
        stage.showAndWait();
    }

    // ── building blocks ──────────────────────────────────────────────────

    private StackPane swatch(String hex, String name) {
        Region dot = new Region();
        dot.setMinSize(26, 26);
        dot.setMaxSize(26, 26);
        dot.setStyle("-fx-background-color: " + hex + "; -fx-background-radius: 99;");

        StackPane wrap = new StackPane(dot);
        wrap.getStyleClass().add("accent-swatch");
        wrap.getProperties().put("hex", hex);
        javafx.scene.control.Tooltip.install(wrap, new javafx.scene.control.Tooltip(name));
        wrap.setOnMouseClicked(e -> applyAccent(hex));
        swatches.add(wrap);
        return wrap;
    }

    private VBox tile(Theme.Bg bg) {
        Region preview = new Region();
        preview.setMinSize(128, 68);
        preview.setMaxSize(128, 68);
        preview.getStyleClass().add("bg-tile");
        preview.setStyle("-fx-background-color: " + bg.previewGradient() + ";");

        Label name = new Label(bg.name());
        name.getStyleClass().add("bg-name");

        VBox box = new VBox(6, preview, name);
        box.setAlignment(Pos.CENTER);
        box.getProperties().put("bgId", bg.id());
        box.setOnMouseClicked(e -> applyBackground(bg.id()));
        box.setCursor(javafx.scene.Cursor.HAND);
        tiles.add(box);
        return box;
    }

    // ── apply + selection state ──────────────────────────────────────────

    private void applyAccent(String hex) {
        ctx.config.accentColor = hex;
        ctx.config.save();
        if (owner != null && owner.getScene() != null) Theme.applyAccent(owner.getScene(), hex);
        if (dialogScene != null) Theme.applyAccent(dialogScene, hex);
        refreshSelection();
    }

    private void applyBackground(String id) {
        ctx.config.backgroundTheme = id;
        ctx.config.save();
        if (owner != null && owner.getScene() != null) Theme.applyBackground(owner.getScene(), id);
        refreshSelection();
    }

    private void applyStars() {
        if (owner == null || owner.getScene() == null) return;
        var node = owner.getScene().getRoot().lookup(".star-field");
        if (node != null) node.setVisible(ctx.config.showStars);
    }

    private void refreshSelection() {
        for (StackPane s : swatches) {
            s.pseudoClassStateChanged(SELECTED, ctx.config.accentColor.equalsIgnoreCase((String) s.getProperties().get("hex")));
        }
        String active = Theme.background(ctx.config.backgroundTheme).id();
        for (VBox t : tiles) {
            boolean sel = active.equals(t.getProperties().get("bgId"));
            t.getChildren().get(0).pseudoClassStateChanged(SELECTED, sel);
        }
    }

    // ── small helpers ────────────────────────────────────────────────────

    private static Color safeColor(String hex) {
        try { return Color.web(hex); } catch (Exception e) { return Color.web("#7C3AED"); }
    }

    private static String toHex(Color c) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }
}
