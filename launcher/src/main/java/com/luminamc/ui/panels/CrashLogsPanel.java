package com.luminamc.ui.panels;

import com.luminamc.config.LuminaPaths;
import com.luminamc.ui.AppContext;
import com.luminamc.ui.FxUi;
import com.luminamc.ui.components.LogView;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.layout.*;

import java.awt.Desktop;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;

/**
 * In-app crash-report browser: a list of captured crash logs on the left and the
 * selected report rendered in the colour-coded {@link LogView} on the right —
 * far more readable than opening raw .log files in a text editor.
 */
public final class CrashLogsPanel extends BorderPane {

    private final ListView<Path> list = new ListView<>();
    private final LogView viewer = new LogView();
    private final Label emptyHint = FxUi.muted("Select a crash report to view it.");

    public CrashLogsPanel(AppContext ctx) {
        getStyleClass().add("grid-root");
        setTop(header());

        list.getStyleClass().add("crash-list");
        list.setPrefWidth(280);
        list.setMinWidth(240);
        list.setCellFactory(v -> new CrashCell());
        list.getSelectionModel().selectedItemProperty().addListener((o, a, b) -> showReport(b));
        list.setPlaceholder(noCrashes());

        BorderPane right = new BorderPane();
        right.setCenter(viewer);
        right.setPadding(new Insets(0, 32, 24, 16));

        HBox body = new HBox(0, withPadding(list), right);
        HBox.setHgrow(right, Priority.ALWAYS);
        setCenter(body);

        loadCrashes();
    }

    private HBox header() {
        Label title = new Label("Crash Logs");
        title.getStyleClass().add("page-title");

        Button openFolder = new Button("🗀  Open logs folder");
        openFolder.getStyleClass().add("ghost-button");
        openFolder.setOnAction(e -> openDir(LuminaPaths.logs()));

        Button refresh = new Button("⟳  Refresh");
        refresh.getStyleClass().add("ghost-button");
        refresh.setOnAction(e -> loadCrashes());

        Button delete = new Button("🗑  Delete");
        delete.getStyleClass().add("danger-button");
        delete.setOnAction(e -> deleteSelected());

        Button deleteAll = new Button("Delete all");
        deleteAll.getStyleClass().add("ghost-button");
        deleteAll.setOnAction(e -> deleteAll());

        HBox bar = new HBox(10, title, FxUi.hgrow(), delete, deleteAll, refresh, openFolder);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(28, 32, 14, 32));
        return bar;
    }

    private VBox withPadding(javafx.scene.Node n) {
        VBox box = new VBox(n);
        VBox.setVgrow(n, Priority.ALWAYS);
        box.setPadding(new Insets(0, 8, 24, 32));
        return box;
    }

    private void loadCrashes() {
        List<Path> crashes = new ArrayList<>();
        Path dir = LuminaPaths.logs();
        if (Files.isDirectory(dir)) {
            try (Stream<Path> s = Files.list(dir)) {
                s.filter(p -> {
                    String n = p.getFileName().toString();
                    return n.startsWith("crash-") && n.endsWith(".log");
                }).forEach(crashes::add);
            } catch (Exception ignored) {}
        }
        crashes.sort(Comparator.comparingLong((Path p) -> p.toFile().lastModified()).reversed());
        list.getItems().setAll(crashes);
        if (!crashes.isEmpty()) list.getSelectionModel().selectFirst();
        else { viewer.clear(); }
    }

    private void deleteSelected() {
        Path sel = list.getSelectionModel().getSelectedItem();
        if (sel == null) return;
        try { Files.deleteIfExists(sel); } catch (Exception ignored) {}
        loadCrashes();
    }

    private void deleteAll() {
        if (list.getItems().isEmpty()) return;
        boolean ok = com.luminamc.ui.LuminaDialog.confirmDanger(
                getScene() != null ? getScene().getWindow() : null,
                "Clear crash logs",
                "Delete all " + list.getItems().size() + " crash reports?", "Delete all");
        if (ok) {
            for (Path p : list.getItems()) { try { Files.deleteIfExists(p); } catch (Exception ignored) {} }
            loadCrashes();
        }
    }

    private void showReport(Path file) {
        viewer.clear();
        if (file == null) return;
        try {
            for (String line : Files.readAllLines(file)) viewer.append(line);
        } catch (Exception e) {
            viewer.append("[LuminaMC] Could not read " + file.getFileName() + ": " + e.getMessage());
        }
    }

    private VBox noCrashes() {
        Label icon = new Label("✓");
        icon.setStyle("-fx-font-size: 34px; -fx-text-fill: #4ADE80;");
        Label l1 = new Label("No crashes recorded");
        l1.getStyleClass().add("row-label");
        Label l2 = FxUi.muted("Crash reports appear here automatically when a game session ends badly.");
        VBox box = new VBox(8, icon, l1, l2);
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(30));
        return box;
    }

    private void openDir(Path dir) {
        try { LuminaPaths.mkdirs(dir); } catch (Exception ignored) {}
        new Thread(() -> {
            try { if (Desktop.isDesktopSupported()) Desktop.getDesktop().open(dir.toFile()); }
            catch (Exception ignored) {}
        }, "luminamc-open-logs").start();
    }

    /** Lists a crash report as "Instance · date" parsed from the file. */
    private static final class CrashCell extends ListCell<Path> {
        @Override protected void updateItem(Path item, boolean empty) {
            super.updateItem(item, empty);
            if (empty || item == null) { setText(null); setGraphic(null); return; }

            String fileName = item.getFileName().toString();
            String when = formatTime(item.toFile().lastModified());
            String title = firstInstanceLine(item);

            Label name = new Label(title != null ? title : fileName);
            name.getStyleClass().add("row-label");
            Label sub = new Label(when);
            sub.getStyleClass().add("card-detail");
            VBox box = new VBox(2, name, sub);
            setGraphic(box);
            setText(null);
        }

        private static String firstInstanceLine(Path file) {
            try (Stream<String> s = Files.lines(file)) {
                return s.filter(l -> l.startsWith("Instance:"))
                        .map(l -> l.substring("Instance:".length()).trim())
                        .findFirst().orElse(null);
            } catch (Exception e) { return null; }
        }

        private static String formatTime(long millis) {
            return new java.text.SimpleDateFormat("dd MMM yyyy, HH:mm").format(new java.util.Date(millis));
        }
    }
}
