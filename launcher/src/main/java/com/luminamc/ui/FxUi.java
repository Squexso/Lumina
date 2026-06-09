package com.luminamc.ui;

import com.luminamc.ui.components.ToggleSwitch;
import javafx.geometry.Pos;
import javafx.scene.control.Label;
import javafx.scene.layout.*;

import java.util.function.Consumer;

/** Small factory helpers for consistently-styled controls. */
public final class FxUi {

    private FxUi() {}

    public static Label h1(String s) {
        Label l = new Label(s);
        l.getStyleClass().add("h1");
        return l;
    }

    public static Label sectionTitle(String s) {
        Label l = new Label(s);
        l.getStyleClass().add("section-title");
        return l;
    }

    public static Label muted(String s) {
        Label l = new Label(s);
        l.getStyleClass().add("muted");
        l.setWrapText(true);
        return l;
    }

    public static Region hgrow() {
        Region r = new Region();
        HBox.setHgrow(r, Priority.ALWAYS);
        return r;
    }

    public static Region vgrow() {
        Region r = new Region();
        VBox.setVgrow(r, Priority.ALWAYS);
        return r;
    }

    /** Adds a subtle hover lift + violet glow to any node (buttons, cards). */
    public static void hoverPop(javafx.scene.Node node) {
        node.setCursor(javafx.scene.Cursor.HAND);
        javafx.scene.effect.DropShadow glow = new javafx.scene.effect.DropShadow(0, javafx.scene.paint.Color.web("#8B5CF6"));
        node.setOnMouseEntered(e -> {
            node.setEffect(glow);
            javafx.animation.ScaleTransition st = new javafx.animation.ScaleTransition(javafx.util.Duration.millis(120), node);
            st.setToX(1.04); st.setToY(1.04); st.play();
            new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.millis(150),
                    new javafx.animation.KeyValue(glow.radiusProperty(), 16))).play();
        });
        node.setOnMouseExited(e -> {
            javafx.animation.ScaleTransition st = new javafx.animation.ScaleTransition(javafx.util.Duration.millis(120), node);
            st.setToX(1.0); st.setToY(1.0); st.play();
            javafx.animation.Timeline g = new javafx.animation.Timeline(new javafx.animation.KeyFrame(javafx.util.Duration.millis(150),
                    new javafx.animation.KeyValue(glow.radiusProperty(), 0)));
            g.setOnFinished(ev -> node.setEffect(null));
            g.play();
        });
    }

    public static VBox card(javafx.scene.Node... children) {
        VBox box = new VBox(10, children);
        box.getStyleClass().add("card");
        return box;
    }

    /** A label + description on the left and an on/off switch on the right. */
    public static HBox toggleRow(String label, String description, boolean initial, Consumer<Boolean> onChange) {
        VBox text = new VBox(2);
        Label name = new Label(label);
        name.getStyleClass().add("row-label");
        text.getChildren().add(name);
        if (description != null && !description.isBlank()) {
            text.getChildren().add(muted(description));
        }

        ToggleSwitch sw = new ToggleSwitch(initial);
        sw.selectedProperty().addListener((o, was, is) -> onChange.accept(is));

        HBox row = new HBox(12, text, hgrow(), sw);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("toggle-row");
        return row;
    }
}
