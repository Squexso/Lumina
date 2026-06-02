package com.luminamc.ui;

import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.layout.HBox;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

/**
 * Themed, frameless replacement for JavaFX {@link javafx.scene.control.Alert}.
 *
 * <p>Vanilla Alerts draw the OS window chrome (an ugly white title bar on
 * Windows) that clashes with the cosmic Lumina look. These dialogs are
 * transparent, rounded, dark-violet cards rendered entirely by the app — so
 * every confirmation / error matches the rest of the launcher.
 */
public final class LuminaDialog {

    private LuminaDialog() {}

    /** OK / Cancel confirmation. Returns true if the user confirmed. */
    public static boolean confirm(Window owner, String title, String message) {
        return show(owner, "?", title, message, "OK", "Cancel", false);
    }

    /** Destructive confirmation — the primary button is styled as danger. */
    public static boolean confirmDanger(Window owner, String title, String message, String okText) {
        return show(owner, "🗑", title, message, okText, "Cancel", true);
    }

    /** Single-button error dialog. */
    public static void error(Window owner, String title, String message) {
        show(owner, "⚠", title, message, "OK", null, false);
    }

    /** Single-button info dialog. */
    public static void info(Window owner, String title, String message) {
        show(owner, "✦", title, message, "OK", null, false);
    }

    /**
     * Multi-choice dialog. The first button is the accent (primary) action, the
     * rest are ghost buttons. Returns the index of the chosen button, or -1 if the
     * dialog was dismissed (Esc / close).
     */
    public static int choose(Window owner, String icon, String title, String message, String... buttons) {
        Stage stage = new Stage(StageStyle.TRANSPARENT);
        final int[] result = {-1};

        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 20px;");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("dialog-title");
        HBox header = new HBox(10, iconLabel, titleLabel);
        header.setAlignment(Pos.CENTER_LEFT);

        Label msg = new Label(message);
        msg.getStyleClass().add("dialog-message");
        msg.setWrapText(true);
        msg.setMaxWidth(420);

        HBox bar = new HBox(10);
        bar.setAlignment(Pos.CENTER_RIGHT);
        for (int i = 0; i < buttons.length; i++) {
            final int idx = i;
            Button b = new Button(buttons[i]);
            b.getStyleClass().add(i == 0 ? "accent-button" : "ghost-button");
            if (i == 0) b.setDefaultButton(true);
            if (i == buttons.length - 1) b.setCancelButton(true);
            b.setOnAction(e -> { result[0] = idx; stage.close(); });
            bar.getChildren().add(b);
        }

        VBox card = new VBox(16, header, msg, bar);
        card.getStyleClass().add("lumina-dialog");
        card.setPadding(new Insets(22, 24, 20, 24));

        final double[] drag = new double[2];
        card.setOnMousePressed(e -> { drag[0] = e.getScreenX() - stage.getX(); drag[1] = e.getScreenY() - stage.getY(); });
        card.setOnMouseDragged(e -> { stage.setX(e.getScreenX() - drag[0]); stage.setY(e.getScreenY() - drag[1]); });

        Scene scene = new Scene(card);
        scene.setFill(Color.TRANSPARENT);
        Theme.apply(scene);
        Theme.applyAccent(scene);

        stage.setScene(scene);
        if (owner != null) stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setResizable(false);
        stage.setOnShown(e -> {
            if (owner != null) {
                stage.setX(owner.getX() + (owner.getWidth() - card.getWidth()) / 2);
                stage.setY(owner.getY() + (owner.getHeight() - card.getHeight()) / 2);
            }
        });
        stage.showAndWait();
        return result[0];
    }

    // ── core ─────────────────────────────────────────────────────────────

    private static boolean show(Window owner, String icon, String title, String message,
                                String okText, String cancelText, boolean danger) {
        Stage stage = new Stage(StageStyle.TRANSPARENT);
        final boolean[] result = {false};

        Label iconLabel = new Label(icon);
        iconLabel.setStyle("-fx-font-size: 20px;");
        Label titleLabel = new Label(title);
        titleLabel.getStyleClass().add("dialog-title");
        HBox header = new HBox(10, iconLabel, titleLabel);
        header.setAlignment(Pos.CENTER_LEFT);

        Label msg = new Label(message);
        msg.getStyleClass().add("dialog-message");
        msg.setWrapText(true);
        msg.setMaxWidth(420);

        Button ok = new Button(okText);
        ok.getStyleClass().add(danger ? "danger-button" : "accent-button");
        ok.setDefaultButton(true);
        ok.setOnAction(e -> { result[0] = true; stage.close(); });

        HBox buttons = new HBox(10);
        buttons.setAlignment(Pos.CENTER_RIGHT);
        if (cancelText != null) {
            Button cancel = new Button(cancelText);
            cancel.getStyleClass().add("ghost-button");
            cancel.setCancelButton(true);
            cancel.setOnAction(e -> { result[0] = false; stage.close(); });
            buttons.getChildren().add(cancel);
        }
        buttons.getChildren().add(ok);

        VBox card = new VBox(16, header, msg, buttons);
        card.getStyleClass().add("lumina-dialog");
        card.setPadding(new Insets(22, 24, 20, 24));

        // Allow dragging the frameless dialog by its body.
        final double[] drag = new double[2];
        card.setOnMousePressed(e -> { drag[0] = e.getScreenX() - stage.getX(); drag[1] = e.getScreenY() - stage.getY(); });
        card.setOnMouseDragged(e -> { stage.setX(e.getScreenX() - drag[0]); stage.setY(e.getScreenY() - drag[1]); });

        Scene scene = new Scene(card);
        scene.setFill(Color.TRANSPARENT);
        Theme.apply(scene);
        Theme.applyAccent(scene);

        stage.setScene(scene);
        if (owner != null) stage.initOwner(owner);
        stage.initModality(Modality.APPLICATION_MODAL);
        stage.setResizable(false);

        // Centre on the owner once sized.
        stage.setOnShown(e -> {
            if (owner != null) {
                stage.setX(owner.getX() + (owner.getWidth() - card.getWidth()) / 2);
                stage.setY(owner.getY() + (owner.getHeight() - card.getHeight()) / 2);
            }
        });
        stage.showAndWait();
        return result[0];
    }
}
