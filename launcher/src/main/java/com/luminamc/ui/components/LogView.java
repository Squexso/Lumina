package com.luminamc.ui.components;

import javafx.collections.FXCollections;
import javafx.collections.ObservableList;
import javafx.collections.transformation.FilteredList;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * A readable, Prism-style log view: each line is colour-coded by severity,
 * shown in a monospace list with a search box and a level filter, plus
 * copy / save / open-folder actions. Auto-scrolls while you're at the bottom.
 */
public final class LogView extends BorderPane {

    /** Severity levels detected per line. */
    public enum Level { ERROR, WARN, INFO, DEBUG, OTHER }

    private static final Pattern LEVEL_TAG =
            Pattern.compile("/(FATAL|ERROR|WARN|INFO|DEBUG|TRACE)]");

    private final ObservableList<String> all = FXCollections.observableArrayList();
    private final FilteredList<String> shown = new FilteredList<>(all, s -> true);
    private final ListView<String> listView = new ListView<>(shown);

    private final TextField search = new TextField();
    private final ComboBox<String> levelFilter = new ComboBox<>();

    private boolean autoScroll = true;

    public LogView() {
        getStyleClass().add("logview");

        // ── toolbar ──
        search.setPromptText("Filter…");
        search.getStyleClass().add("log-search");
        search.setPrefWidth(180);
        search.textProperty().addListener((o, a, b) -> applyFilter());

        levelFilter.getItems().addAll("All levels", "Warnings & errors", "Errors only");
        levelFilter.getSelectionModel().selectFirst();
        levelFilter.valueProperty().addListener((o, a, b) -> applyFilter());

        Button copy = new Button("Copy");
        copy.getStyleClass().add("ghost-button");
        copy.setOnAction(e -> copyAll());

        Button clear = new Button("Clear");
        clear.getStyleClass().add("ghost-button");
        clear.setOnAction(e -> all.clear());

        Region spacer = new Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Label title = new Label("Log");
        title.getStyleClass().add("section-title");

        HBox toolbar = new HBox(8, title, spacer, search, levelFilter, copy, clear);
        toolbar.setAlignment(Pos.CENTER_LEFT);
        toolbar.setPadding(new Insets(0, 0, 8, 0));

        // ── list ──
        listView.getStyleClass().add("log-list");
        listView.setCellFactory(v -> new LogCell());
        listView.setFocusTraversable(false);

        setTop(toolbar);
        setCenter(listView);
    }

    // ── public API ───────────────────────────────────────────────────────

    public void append(String line) {
        if (line == null) return;
        all.add(line);
        if (autoScroll && !shown.isEmpty()) listView.scrollTo(shown.size() - 1);
    }

    public void clear() { all.clear(); }

    public String fullText() { return String.join("\n", all); }

    public boolean isEmpty() { return all.isEmpty(); }

    // ── filtering ────────────────────────────────────────────────────────

    private void applyFilter() {
        String q = search.getText() == null ? "" : search.getText().toLowerCase().trim();
        String lvl = levelFilter.getValue();
        shown.setPredicate(line -> {
            if (!q.isEmpty() && !line.toLowerCase().contains(q)) return false;
            Level l = levelOf(line);
            return switch (lvl == null ? "All levels" : lvl) {
                case "Errors only"        -> l == Level.ERROR;
                case "Warnings & errors"  -> l == Level.ERROR || l == Level.WARN;
                default                   -> true;
            };
        });
    }

    private void copyAll() {
        ClipboardContent c = new ClipboardContent();
        c.putString(fullText());
        Clipboard.getSystemClipboard().setContent(c);
    }

    // ── per-line severity ────────────────────────────────────────────────

    static Level levelOf(String line) {
        Matcher m = LEVEL_TAG.matcher(line);
        if (m.find()) {
            return switch (m.group(1)) {
                case "FATAL", "ERROR" -> Level.ERROR;
                case "WARN"           -> Level.WARN;
                case "INFO"           -> Level.INFO;
                default               -> Level.DEBUG;
            };
        }
        // Stack traces and uncaught exceptions.
        String t = line.stripLeading();
        if (t.startsWith("at ") || t.startsWith("Caused by:")
                || line.contains("Exception") || line.contains("\tat ")
                || t.startsWith("...")) {
            return Level.ERROR;
        }
        if (line.startsWith("[LuminaMC]")) return Level.INFO;
        return Level.OTHER;
    }

    /** Colours each line by detected severity. */
    private static final class LogCell extends ListCell<String> {
        @Override
        protected void updateItem(String item, boolean empty) {
            super.updateItem(item, empty);
            getStyleClass().removeAll("log-error", "log-warn", "log-info", "log-debug", "log-other");
            if (empty || item == null) { setText(null); return; }
            setText(item);
            switch (levelOf(item)) {
                case ERROR -> getStyleClass().add("log-error");
                case WARN  -> getStyleClass().add("log-warn");
                case INFO  -> getStyleClass().add("log-info");
                case DEBUG -> getStyleClass().add("log-debug");
                case OTHER -> getStyleClass().add("log-other");
            }
        }
    }
}
