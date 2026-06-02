package com.luminamc.ui.panels;

import com.luminamc.instance.Instance;
import com.luminamc.javart.JavaRuntime;
import com.luminamc.ui.AppContext;
import com.luminamc.ui.FxUi;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.DirectoryChooser;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

/** Settings tab: name, Java, RAM, JVM args, and instance deletion. */
public final class SettingsPanel extends VBox {

    private static final int RAM_MAX_MB = 32768;

    private final AppContext ctx;
    private final Instance inst;
    private final Runnable refreshSidebar;
    private final Runnable onDeleted;

    public SettingsPanel(AppContext ctx, Instance inst, Runnable refreshSidebar, Runnable onDeleted) {
        this.ctx = ctx;
        this.inst = inst;
        this.refreshSidebar = refreshSidebar;
        this.onDeleted = onDeleted;

        setSpacing(16);
        setPadding(new Insets(24));
        getChildren().addAll(FxUi.h1("Settings"), generalCard(), javaCard(), ramCard(), jvmCard(), dangerCard());
    }

    private VBox generalCard() {
        TextField name = new TextField(inst.name);
        name.textProperty().addListener((o, a, b) -> { inst.name = b; save(); refreshSidebar.run(); });

        Label version = FxUi.muted("Minecraft " + inst.mcVersion + "  ·  " + inst.loader.displayName
                + (inst.loaderVersion != null ? " " + inst.loaderVersion : ""));

        return FxUi.card(FxUi.sectionTitle("General"),
                labeled("Instance name", name),
                labeled("Version & loader", version));
    }

    private VBox javaCard() {
        ComboBox<String> combo = new ComboBox<>();
        combo.getItems().add("Auto (recommended)");
        List<String> paths = new ArrayList<>();
        for (JavaRuntime rt : ctx.runtimes) {
            combo.getItems().add(rt.label());
            paths.add(rt.executable.toString());
        }
        combo.getItems().add("Custom path…");
        combo.getSelectionModel().select(0);

        TextField customPath = new TextField(inst.javaPathOverride == null ? "" : inst.javaPathOverride);
        customPath.setPromptText("Path to java executable or JDK home");
        customPath.setDisable(true);

        Button browse = new Button("Browse…");
        browse.getStyleClass().add("ghost-button");
        browse.setDisable(true);
        browse.setOnAction(e -> {
            DirectoryChooser dc = new DirectoryChooser();
            dc.setTitle("Select JDK home");
            File dir = dc.showDialog(getScene().getWindow());
            if (dir != null) { customPath.setText(dir.getAbsolutePath()); applyJava(dir.getAbsolutePath()); }
        });

        if (inst.javaPathOverride != null && !inst.javaPathOverride.isBlank()) {
            combo.getSelectionModel().select("Custom path…");
            customPath.setDisable(false);
            browse.setDisable(false);
        }

        combo.getSelectionModel().selectedIndexProperty().addListener((o, a, idx) -> {
            int i = idx.intValue();
            boolean custom = i == combo.getItems().size() - 1;
            customPath.setDisable(!custom);
            browse.setDisable(!custom);
            if (i == 0) { applyJava(null); }                       // Auto
            else if (!custom) { applyJava(paths.get(i - 1)); }     // a detected runtime
        });
        customPath.textProperty().addListener((o, a, b) -> { if (!customPath.isDisabled()) applyJava(b); });

        HBox customRow = new HBox(8, customPath, browse);
        HBox.setHgrow(customPath, Priority.ALWAYS);

        return FxUi.card(FxUi.sectionTitle("Java runtime"),
                labeled("Runtime", combo),
                labeled("Custom", customRow),
                FxUi.muted("Auto picks the lowest installed JDK that satisfies this Minecraft version."));
    }

    private VBox ramCard() {
        Slider min = ramSlider(inst.ramMinMb);
        Slider max = ramSlider(inst.ramMaxMb);
        Label minVal = new Label(inst.ramMinMb + " MB");
        Label maxVal = new Label(inst.ramMaxMb + " MB");

        min.valueProperty().addListener((o, a, b) -> {
            int v = round(b.doubleValue());
            inst.ramMinMb = v; minVal.setText(v + " MB");
            if (v > inst.ramMaxMb) { max.setValue(v); }
            save();
        });
        max.valueProperty().addListener((o, a, b) -> {
            int v = round(b.doubleValue());
            inst.ramMaxMb = v; maxVal.setText(v + " MB");
            if (v < inst.ramMinMb) { min.setValue(v); }
            save();
        });

        return FxUi.card(FxUi.sectionTitle("Memory"),
                labeled("Minimum (-Xms)", sliderRow(min, minVal)),
                labeled("Maximum (-Xmx)", sliderRow(max, maxVal)));
    }

    private VBox jvmCard() {
        TextArea args = new TextArea(String.join(" ", inst.extraJvmArgs));
        args.getStyleClass().add("code-field");
        args.setPrefRowCount(3);
        args.setWrapText(true);
        args.setPromptText("-XX:+UseStringDeduplication -Dfoo=bar");
        args.textProperty().addListener((o, a, b) -> {
            inst.extraJvmArgs = new ArrayList<>();
            for (String tok : b.trim().split("\\s+")) if (!tok.isBlank()) inst.extraJvmArgs.add(tok);
            save();
        });
        return FxUi.card(FxUi.sectionTitle("Extra JVM arguments"), args,
                FxUi.muted("Added before the game's own JVM args. The RAM optimizer flags are managed in Performance."));
    }

    private VBox dangerCard() {
        Button delete = new Button("Delete this instance");
        delete.getStyleClass().add("danger-button");
        delete.setOnAction(e -> {
            boolean ok = com.luminamc.ui.LuminaDialog.confirmDanger(
                    getScene() != null ? getScene().getWindow() : null,
                    "Delete instance",
                    "Permanently delete \"" + inst.name + "\" and all its files "
                            + "(mods, worlds, screenshots)? This cannot be undone.",
                    "Delete");
            if (ok) {
                ctx.instances.delete(inst);
                onDeleted.run();
            }
        });
        VBox card = FxUi.card(FxUi.sectionTitle("Danger zone"), delete);
        card.getStyleClass().add("danger-card");
        return card;
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private void applyJava(String path) {
        inst.javaPathOverride = (path == null || path.isBlank()) ? null : path;
        save();
    }

    private Slider ramSlider(int value) {
        Slider s = new Slider(512, RAM_MAX_MB, value);
        s.setMajorTickUnit(4096);
        s.setBlockIncrement(512);
        s.setSnapToTicks(false);
        HBox.setHgrow(s, Priority.ALWAYS);
        return s;
    }

    private HBox sliderRow(Slider s, Label value) {
        value.setMinWidth(80);
        HBox row = new HBox(12, s, value);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static int round(double mb) {
        return (int) (Math.round(mb / 256.0) * 256);
    }

    private VBox labeled(String label, javafx.scene.Node control) {
        Label l = new Label(label);
        l.getStyleClass().add("field-label");
        VBox box = new VBox(6, l, control);
        return box;
    }

    private void save() {
        ctx.instances.save(inst);
    }
}
