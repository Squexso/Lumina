package com.luminamc.ui.components;

import javafx.animation.TranslateTransition;
import javafx.beans.property.BooleanProperty;
import javafx.beans.property.SimpleBooleanProperty;
import javafx.scene.layout.Region;
import javafx.scene.layout.StackPane;
import javafx.scene.shape.Rectangle;
import javafx.util.Duration;

/**
 * A compact, self-contained on/off switch with a sliding thumb — used for every
 * feature toggle in the dark UI. Looks correct without external CSS.
 */
public final class ToggleSwitch extends StackPane {

    private static final String ON_TRACK  = "#7C3AED";
    private static final String OFF_TRACK = "#3A3A45";

    private final BooleanProperty selected = new SimpleBooleanProperty(false);
    private final Region thumb = new Region();
    private final Rectangle track = new Rectangle(42, 22);

    public ToggleSwitch() {
        this(false);
    }

    public ToggleSwitch(boolean initial) {
        track.setArcWidth(22);
        track.setArcHeight(22);

        thumb.setPrefSize(16, 16);
        thumb.setMaxSize(16, 16);
        thumb.setStyle("-fx-background-color: white; -fx-background-radius: 8;"
                + " -fx-effect: dropshadow(gaussian, rgba(0,0,0,0.35), 4, 0, 0, 1);");

        getChildren().addAll(track, thumb);
        setMaxSize(42, 22);
        setPrefSize(42, 22);
        StackPane.setAlignment(thumb, javafx.geometry.Pos.CENTER_LEFT);
        setStyle("-fx-cursor: hand;");

        selected.addListener((o, was, is) -> render(true));
        setOnMouseClicked(e -> setSelected(!isSelected()));

        selected.set(initial);
        render(false);
    }

    private void render(boolean animate) {
        boolean on = selected.get();
        track.setStyle("-fx-fill: " + (on ? ON_TRACK : OFF_TRACK) + ";");
        double target = on ? 22 : 3;
        if (animate) {
            TranslateTransition tt = new TranslateTransition(Duration.millis(140), thumb);
            tt.setToX(target);
            tt.play();
        } else {
            thumb.setTranslateX(target);
        }
    }

    public BooleanProperty selectedProperty() { return selected; }
    public boolean isSelected()               { return selected.get(); }
    public void setSelected(boolean v)         { selected.set(v); }
}
