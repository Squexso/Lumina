package com.luminamc.ui.components;

import javafx.animation.*;
import javafx.scene.control.Button;
import javafx.scene.effect.DropShadow;
import javafx.scene.paint.Color;
import javafx.util.Duration;

/**
 * The big animated play button. Idle: a soft pulsing violet glow. Hover: scales
 * up slightly. Busy: shows the current phase and stops the pulse.
 */
public final class LaunchButton extends Button {

    public enum State { READY, BUSY, RUNNING }

    private final DropShadow glow = new DropShadow();
    private Timeline pulse;
    private State state = State.READY;

    public LaunchButton() {
        getStyleClass().add("launch-button");
        setText("PLAY");
        setPrefSize(240, 56);

        glow.setColor(Color.web("#7C3AED"));
        glow.setRadius(18);
        glow.setSpread(0.2);
        setEffect(glow);

        setupPulse();
        setupHover();
    }

    private void setupPulse() {
        pulse = new Timeline(
                new KeyFrame(Duration.ZERO, new KeyValue(glow.radiusProperty(), 14)),
                new KeyFrame(Duration.seconds(1.1), new KeyValue(glow.radiusProperty(), 30)),
                new KeyFrame(Duration.seconds(2.2), new KeyValue(glow.radiusProperty(), 14)));
        pulse.setCycleCount(Animation.INDEFINITE);
        pulse.play();
    }

    private void setupHover() {
        setOnMouseEntered(e -> scale(1.04));
        setOnMouseExited(e -> scale(1.0));
    }

    private void scale(double to) {
        ScaleTransition st = new ScaleTransition(Duration.millis(120), this);
        st.setToX(to);
        st.setToY(to);
        st.play();
    }

    public void setState(State s) {
        this.state = s;
        switch (s) {
            case READY -> {
                setText("PLAY");
                setDisable(false);
                if (pulse != null) pulse.play();
            }
            case BUSY -> {
                setText("WORKING…");
                setDisable(true);
                if (pulse != null) pulse.stop();
                glow.setRadius(14);
            }
            case RUNNING -> {
                setText("RUNNING");
                setDisable(true);
                if (pulse != null) pulse.stop();
                glow.setRadius(22);
            }
        }
    }

    public State state() { return state; }

    public void setPhase(String text) {
        if (state == State.BUSY) setText(text);
    }
}
