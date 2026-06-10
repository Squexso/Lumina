package com.luminamc.ui;

import javafx.css.PseudoClass;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.CheckBox;
import javafx.scene.control.Label;
import javafx.scene.control.TextField;
import javafx.scene.control.Tooltip;
import javafx.scene.input.KeyCode;
import javafx.scene.input.MouseButton;
import javafx.scene.layout.FlowPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Pane;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;
import javafx.stage.FileChooser;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * The "Appearance" panel opened by clicking the sidebar logo: accent swatches plus
 * a fully custom hue/saturation picker (no OS colour dialog), clean background
 * presets, an own-image background, and the star-field toggle. Every change applies
 * to the main window instantly and is persisted right away.
 */
public final class AppearanceDialog {

    private static final PseudoClass SELECTED = PseudoClass.getPseudoClass("selected");

    /** Curated accents that all sit well on the dark backgrounds. */
    private static final String[][] ACCENTS = {
            {"#7C3AED", "Violet"}, {"#6366F1", "Indigo"}, {"#3B82F6", "Blue"},
            {"#06B6D4", "Cyan"},   {"#10B981", "Emerald"}, {"#F59E0B", "Amber"},
            {"#F43F5E", "Rose"},   {"#EC4899", "Pink"}};

    // Custom picker geometry.
    private static final double PAD_W = 200, PAD_H = 112, HUE_W = 16;

    private final AppContext ctx;
    private final List<StackPane> swatches = new ArrayList<>();
    private final List<VBox> tiles = new ArrayList<>();
    private Window owner;
    private Scene dialogScene;

    // Custom picker state (HSB) + its live-updated nodes.
    private double hue = 270, sat = 0.75, val = 0.93;
    private Region hueBase;
    private Circle padThumb;
    private Rectangle hueThumb;
    private Region previewSwatch;
    private TextField hexField;
    private StackPane customTile;

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

        // ── accent swatches + custom picker ──
        FlowPane accentRow = new FlowPane(10, 10);
        for (String[] a : ACCENTS) accentRow.getChildren().add(swatch(a[0], a[1]));

        Color current = safeColor(ctx.config.accentColor);
        hue = current.getHue(); sat = current.getSaturation(); val = current.getBrightness();

        // ── background tiles (presets + own image) ──
        FlowPane bgRow = new FlowPane(12, 12);
        for (Theme.Bg bg : Theme.BACKGROUNDS) bgRow.getChildren().add(tile(bg));
        bgRow.getChildren().add(customImageTile(stage));

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
            ctx.config.backgroundImage = null;
            applyAccent("#7C3AED");
            applyBackground("nebula");
            ctx.config.showStars = true;
            ctx.config.save();
            applyStars();
            stars.setSelected(true);
            Color c = safeColor("#7C3AED");
            hue = c.getHue(); sat = c.getSaturation(); val = c.getBrightness();
            updatePickerVisuals();
            refreshCustomTile();
        });
        Button done = new Button("Done");
        done.getStyleClass().add("accent-button");
        done.setDefaultButton(true);
        done.setOnAction(e -> stage.close());
        HBox footer = new HBox(10, reset, FxUi.hgrow(), done);
        footer.setAlignment(Pos.CENTER_RIGHT);

        VBox panel = new VBox(14,
                header, sub,
                FxUi.sectionTitle("ACCENT"), accentRow, customPicker(),
                FxUi.sectionTitle("BACKGROUND"), bgRow, stars,
                footer);
        panel.getStyleClass().add("lumina-dialog");
        panel.setPadding(new Insets(22));
        panel.setPrefWidth(486);

        refreshSelection();
        updatePickerVisuals();

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

    // ── accent swatches ──────────────────────────────────────────────────

    private StackPane swatch(String hex, String name) {
        Region dot = new Region();
        dot.setMinSize(26, 26);
        dot.setMaxSize(26, 26);
        dot.setStyle("-fx-background-color: " + hex + "; -fx-background-radius: 99;");

        StackPane wrap = new StackPane(dot);
        wrap.getStyleClass().add("accent-swatch");
        wrap.getProperties().put("hex", hex);
        Tooltip.install(wrap, new Tooltip(name));
        wrap.setOnMouseClicked(e -> {
            Color c = safeColor(hex);
            hue = c.getHue(); sat = c.getSaturation(); val = c.getBrightness();
            updatePickerVisuals();
            applyAccent(hex);
        });
        swatches.add(wrap);
        return wrap;
    }

    /**
     * The in-panel colour picker: a saturation/brightness pad + hue rail + hex field.
     * Entirely self-drawn, so no OS colour dialog (and nothing white) ever opens.
     */
    private HBox customPicker() {
        // Saturation/brightness pad: hue base + white→transparent + transparent→black.
        hueBase = layer(PAD_W, PAD_H, "");
        Region white = layer(PAD_W, PAD_H,
                "-fx-background-color: linear-gradient(to right, white, transparent);");
        Region black = layer(PAD_W, PAD_H,
                "-fx-background-color: linear-gradient(to bottom, transparent, black);");

        padThumb = new Circle(7);
        padThumb.setStroke(Color.WHITE);
        padThumb.setStrokeWidth(2);
        padThumb.setMouseTransparent(true);
        Pane padOverlay = new Pane(padThumb);
        padOverlay.setMinSize(PAD_W, PAD_H);
        padOverlay.setMaxSize(PAD_W, PAD_H);

        StackPane pad = new StackPane(hueBase, white, black, padOverlay);
        pad.setMaxSize(PAD_W, PAD_H);
        pad.setCursor(Cursor.CROSSHAIR);
        Rectangle padClip = new Rectangle(PAD_W, PAD_H);
        padClip.setArcWidth(20); padClip.setArcHeight(20);
        pad.setClip(padClip);
        pad.setOnMousePressed(e -> padPick(e.getX(), e.getY(), false));
        pad.setOnMouseDragged(e -> padPick(e.getX(), e.getY(), false));
        pad.setOnMouseReleased(e -> padPick(e.getX(), e.getY(), true));

        // Hue rail.
        Region rainbow = layer(HUE_W, PAD_H,
                "-fx-background-color: linear-gradient(to bottom,"
                        + " #ff0000 0%, #ffff00 17%, #00ff00 33%, #00ffff 50%,"
                        + " #0000ff 67%, #ff00ff 83%, #ff0000 100%);");
        hueThumb = new Rectangle(HUE_W + 6, 5);
        hueThumb.setArcWidth(5); hueThumb.setArcHeight(5);
        hueThumb.setFill(Color.TRANSPARENT);
        hueThumb.setStroke(Color.WHITE);
        hueThumb.setStrokeWidth(2);
        hueThumb.setMouseTransparent(true);
        Pane hueOverlay = new Pane(hueThumb);
        hueOverlay.setMinSize(HUE_W, PAD_H);
        hueOverlay.setMaxSize(HUE_W, PAD_H);

        StackPane hueRail = new StackPane(rainbow, hueOverlay);
        hueRail.setMaxSize(HUE_W, PAD_H);
        hueRail.setCursor(Cursor.CROSSHAIR);
        hueRail.setOnMousePressed(e -> huePick(e.getY(), false));
        hueRail.setOnMouseDragged(e -> huePick(e.getY(), false));
        hueRail.setOnMouseReleased(e -> huePick(e.getY(), true));

        // Preview + hex.
        previewSwatch = new Region();
        previewSwatch.setMinSize(40, 40);
        previewSwatch.setMaxSize(40, 40);
        previewSwatch.setStyle("-fx-background-radius: 12;");

        hexField = new TextField();
        hexField.setPrefWidth(86);
        hexField.setPromptText("#RRGGBB");
        hexField.setOnAction(e -> {
            String t = hexField.getText().trim();
            if (!t.startsWith("#")) t = "#" + t;
            if (t.matches("#[0-9a-fA-F]{6}")) {
                Color c = Color.web(t);
                hue = c.getHue(); sat = c.getSaturation(); val = c.getBrightness();
                updatePickerVisuals();
                applyAccent(t.toUpperCase());
            }
        });
        Label hexHint = FxUi.muted("Press Enter to apply");
        VBox previewBox = new VBox(8, previewSwatch, hexField, hexHint);
        previewBox.setAlignment(Pos.TOP_CENTER);

        HBox row = new HBox(12, pad, hueRail, previewBox);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private Region layer(double w, double h, String style) {
        Region r = new Region();
        r.setMinSize(w, h);
        r.setMaxSize(w, h);
        r.setStyle(style);
        return r;
    }

    private void padPick(double x, double y, boolean commit) {
        sat = clamp(x / PAD_W);
        val = 1 - clamp(y / PAD_H);
        updatePickerVisuals();
        if (commit) applyAccent(toHex(Color.hsb(hue, sat, val)));
    }

    private void huePick(double y, boolean commit) {
        hue = clamp(y / PAD_H) * 360;
        updatePickerVisuals();
        if (commit) applyAccent(toHex(Color.hsb(hue, sat, val)));
    }

    private void updatePickerVisuals() {
        Color full = Color.hsb(hue, 1, 1);
        Color pick = Color.hsb(hue, sat, val);
        if (hueBase != null) hueBase.setStyle("-fx-background-color: " + toHex(full) + ";");
        if (padThumb != null) {
            padThumb.setCenterX(sat * PAD_W);
            padThumb.setCenterY((1 - val) * PAD_H);
            padThumb.setFill(pick);
        }
        if (hueThumb != null) {
            hueThumb.setX(-3);
            hueThumb.setY(clamp(hue / 360) * PAD_H - 2.5);
        }
        if (previewSwatch != null) {
            previewSwatch.setStyle("-fx-background-radius: 12; -fx-background-color: " + toHex(pick)
                    + "; -fx-border-color: rgba(255,255,255,0.25); -fx-border-radius: 12;");
        }
        if (hexField != null && !hexField.isFocused()) hexField.setText(toHex(pick));
    }

    private static double clamp(double v) { return v < 0 ? 0 : v > 1 ? 1 : v; }

    // ── background tiles ─────────────────────────────────────────────────

    private VBox tile(Theme.Bg bg) {
        Region preview = new Region();
        preview.setMinSize(128, 68);
        preview.setMaxSize(128, 68);
        preview.getStyleClass().add("bg-tile");
        preview.setStyle("-fx-background-color: " + bg.css() + ";");

        Label name = new Label(bg.name());
        name.getStyleClass().add("bg-name");

        VBox box = new VBox(6, preview, name);
        box.setAlignment(Pos.CENTER);
        box.getProperties().put("bgId", bg.id());
        box.setOnMouseClicked(e -> applyBackground(bg.id()));
        box.setCursor(Cursor.HAND);
        tiles.add(box);
        return box;
    }

    /** The "own image" tile: left-click applies (or picks) an image, right-click re-picks. */
    private VBox customImageTile(Stage stage) {
        Label icon = new Label("🖼");
        icon.setStyle("-fx-font-size: 20px;");
        customTile = new StackPane(icon);
        customTile.setMinSize(128, 68);
        customTile.setMaxSize(128, 68);
        customTile.getStyleClass().add("bg-tile");

        Label name = new Label("Own image");
        name.getStyleClass().add("bg-name");

        VBox box = new VBox(6, customTile, name);
        box.setAlignment(Pos.CENTER);
        box.getProperties().put("bgId", Theme.BG_CUSTOM);
        box.setCursor(Cursor.HAND);
        Tooltip.install(box, new Tooltip("Use your own picture as the background.\nRight-click to choose a different image."));
        box.setOnMouseClicked(e -> {
            boolean hasImage = ctx.config.backgroundImage != null
                    && Files.isRegularFile(Path.of(ctx.config.backgroundImage));
            if (e.getButton() == MouseButton.SECONDARY || !hasImage) {
                chooseImage(stage);
            } else {
                applyBackground(Theme.BG_CUSTOM);
            }
        });
        tiles.add(box);
        refreshCustomTile();
        return box;
    }

    private void chooseImage(Stage stage) {
        FileChooser fc = new FileChooser();
        fc.setTitle("Choose a background image");
        fc.getExtensionFilters().add(new FileChooser.ExtensionFilter(
                "Images", "*.png", "*.jpg", "*.jpeg", "*.gif", "*.bmp"));
        File f = fc.showOpenDialog(stage);
        if (f != null && f.isFile()) {
            ctx.config.backgroundImage = f.getAbsolutePath();
            applyBackground(Theme.BG_CUSTOM);
            refreshCustomTile();
        }
    }

    private void refreshCustomTile() {
        if (customTile == null) return;
        String path = ctx.config.backgroundImage;
        if (path != null && Files.isRegularFile(Path.of(path))) {
            String uri = Path.of(path).toUri().toString();
            customTile.setStyle("-fx-background-image: url(\"" + uri + "\");"
                    + " -fx-background-size: cover; -fx-background-position: center center;"
                    + " -fx-background-radius: 13;");
            customTile.getChildren().forEach(n -> n.setVisible(false));
        } else {
            customTile.setStyle("");
            customTile.getChildren().forEach(n -> n.setVisible(true));
        }
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
        if (owner != null && owner.getScene() != null) {
            Theme.applyBackground(owner.getScene(), id, ctx.config.backgroundImage);
        }
        refreshSelection();
    }

    private void applyStars() {
        if (owner == null || owner.getScene() == null) return;
        var node = owner.getScene().getRoot().lookup(".star-field");
        if (node != null) node.setVisible(ctx.config.showStars);
    }

    private void refreshSelection() {
        for (StackPane s : swatches) {
            s.pseudoClassStateChanged(SELECTED,
                    ctx.config.accentColor.equalsIgnoreCase((String) s.getProperties().get("hex")));
        }
        String active = Theme.BG_CUSTOM.equals(ctx.config.backgroundTheme)
                ? Theme.BG_CUSTOM : Theme.background(ctx.config.backgroundTheme).id();
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
