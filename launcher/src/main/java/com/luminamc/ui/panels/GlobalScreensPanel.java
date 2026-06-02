package com.luminamc.ui.panels;

import com.luminamc.config.LuminaPaths;
import com.luminamc.instance.Instance;
import com.luminamc.ui.AppContext;
import com.luminamc.ui.FxUi;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;

import java.awt.Desktop;
import java.io.File;
import java.nio.file.Path;
import java.util.*;

/**
 * In-app gallery of every screenshot across all instances — thumbnails in a
 * responsive grid, newest first. Clicking a shot opens it in the system viewer.
 */
public final class GlobalScreensPanel extends BorderPane {

    private final AppContext ctx;
    private final FlowPane grid = new FlowPane(14, 14);

    public GlobalScreensPanel(AppContext ctx) {
        this.ctx = ctx;
        getStyleClass().add("grid-root");
        setTop(header());

        grid.setPadding(new Insets(8, 32, 24, 32));
        grid.setAlignment(Pos.TOP_LEFT);
        ScrollPane sp = new ScrollPane(grid);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("cosmic-scroll");
        setCenter(sp);

        loadAsync();
    }

    private HBox header() {
        Label title = new Label("Global Screenshots");
        title.getStyleClass().add("page-title");
        HBox bar = new HBox(title);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(28, 32, 12, 32));
        return bar;
    }

    /** Collects screenshots off-thread, then renders thumbnails on the FX thread. */
    private void loadAsync() {
        Label loading = FxUi.muted("Loading screenshots…");
        grid.getChildren().setAll(loading);

        new Thread(() -> {
            List<Shot> shots = new ArrayList<>();
            for (Instance inst : ctx.instances.all()) {
                for (Path p : ctx.instances.listScreenshots(inst)) {
                    File f = p.toFile();
                    shots.add(new Shot(p, inst.name, f.lastModified()));
                }
            }
            shots.sort(Comparator.comparingLong((Shot s) -> s.modified).reversed());

            Platform.runLater(() -> {
                grid.getChildren().clear();
                if (shots.isEmpty()) {
                    VBox empty = new VBox(8,
                            label("📷", 40),
                            heading("No screenshots yet"),
                            FxUi.muted("Press F2 in-game — your shots from every instance show up here."));
                    empty.setAlignment(Pos.CENTER_LEFT);
                    empty.setPadding(new Insets(30));
                    grid.getChildren().add(empty);
                    return;
                }
                for (Shot s : shots) grid.getChildren().add(thumb(s));
            });
        }, "luminamc-screens").start();
    }

    private VBox thumb(Shot s) {
        ImageView iv = new ImageView();
        iv.setFitWidth(190);
        iv.setFitHeight(107);
        iv.setPreserveRatio(false);
        iv.setSmooth(true);
        try { iv.setImage(new Image(s.path.toUri().toString(), 190, 107, false, true, true)); }
        catch (Exception ignored) {}
        iv.getStyleClass().add("screenshot");

        // Click image to open; a small delete button sits in the top-right corner.
        Button del = new Button("🗑");
        del.getStyleClass().add("shot-delete");
        del.setOnAction(e -> deleteShot(s));
        StackPane.setAlignment(del, Pos.TOP_RIGHT);
        StackPane.setMargin(del, new Insets(4));
        StackPane imgStack = new StackPane(iv, del);
        imgStack.setOnMouseClicked(e -> { if (e.getTarget() != del) open(s.path); });

        Label cap = FxUi.muted(s.instance);
        cap.getStyleClass().add("card-detail");

        VBox box = new VBox(6, imgStack, cap);
        box.getStyleClass().add("shot-card");
        box.setPadding(new Insets(8));
        return box;
    }

    private void deleteShot(Shot s) {
        boolean ok = com.luminamc.ui.LuminaDialog.confirmDanger(
                getScene() != null ? getScene().getWindow() : null,
                "Delete screenshot", "Delete this screenshot?\n" + s.path.getFileName(), "Delete");
        if (ok) {
            try { java.nio.file.Files.deleteIfExists(s.path); } catch (Exception ignored) {}
            loadAsync();
        }
    }

    private void open(Path file) {
        new Thread(() -> {
            try { if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(file.toFile()); }
            catch (Exception ignored) {}
        }, "luminamc-open-shot").start();
    }

    private static Label label(String text, int size) {
        Label l = new Label(text);
        l.setStyle("-fx-font-size: " + size + "px;");
        return l;
    }

    private static Label heading(String text) {
        Label l = new Label(text);
        l.getStyleClass().add("row-label");
        return l;
    }

    private record Shot(Path path, String instance, long modified) {}
}
