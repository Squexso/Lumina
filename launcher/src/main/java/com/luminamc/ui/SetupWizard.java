package com.luminamc.ui;

import com.luminamc.javart.JavaRuntime;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Parent;
import javafx.scene.control.*;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;

/** First-launch wizard: choose Java, default RAM, and sign in. */
public final class SetupWizard {

    private final AppContext ctx;

    public SetupWizard(AppContext ctx) {
        this.ctx = ctx;
    }

    public Parent build(javafx.stage.Window owner, Runnable onComplete) {
        var cfg = ctx.config;

        javafx.scene.Node logo;
        javafx.scene.image.Image logoImg = Theme.logo();
        if (logoImg != null) {
            javafx.scene.image.ImageView iv = new javafx.scene.image.ImageView(logoImg);
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
            iv.setFitWidth(280);
            logo = iv;
        } else {
            logo = com.luminamc.ui.components.CrystalLogo.build(110, 40);
        }
        Label welcome = FxUi.muted("Let's get you set up. You can change all of this later in Settings.");

        // Java.
        ComboBox<String> java = new ComboBox<>();
        java.getItems().add("Auto (recommended)");
        for (JavaRuntime rt : ctx.runtimes) java.getItems().add(rt.label());
        java.getSelectionModel().select(0);
        Label javaFound = FxUi.muted(ctx.runtimes.isEmpty()
                ? "No JDK detected — install Java 21 (Temurin) for modern versions."
                : ctx.runtimes.size() + " Java runtime(s) detected.");

        // RAM.
        Slider ram = new Slider(1024, 16384, cfg.defaultRamMaxMb);
        ram.setMajorTickUnit(2048);
        Label ramVal = new Label(cfg.defaultRamMaxMb + " MB");
        ram.valueProperty().addListener((o, a, b) -> {
            cfg.defaultRamMaxMb = (int) (Math.round(b.doubleValue() / 256.0) * 256);
            ramVal.setText(cfg.defaultRamMaxMb + " MB");
        });
        HBox.setHgrow(ram, Priority.ALWAYS);
        HBox ramRow = new HBox(12, ram, ramVal);
        ramRow.setAlignment(Pos.CENTER_LEFT);

        // Login.
        Label loginState = FxUi.muted(ctx.auth.active() != null
                ? "Signed in as " + ctx.auth.active().username : "Not signed in (optional).");
        Button signIn = new Button("Add account (Microsoft or offline)");
        signIn.getStyleClass().add("ghost-button");
        signIn.setOnAction(e -> new AccountDialog(ctx).show(owner,
                () -> loginState.setText(ctx.auth.active() != null
                        ? "Signed in as " + ctx.auth.active().username : "Not signed in.")));

        Button finish = new Button("Finish setup  →");
        finish.getStyleClass().add("accent-button");
        finish.setOnAction(e -> {
            int sel = java.getSelectionModel().getSelectedIndex();
            cfg.defaultJavaPath = (sel <= 0) ? null : ctx.runtimes.get(sel - 1).executable.toString();
            cfg.setupComplete = true;
            cfg.save();
            onComplete.run();
        });

        VBox card = FxUi.card(
                FxUi.sectionTitle("Java runtime"), java, javaFound,
                new Separator(),
                FxUi.sectionTitle("Maximum memory"), ramRow,
                new Separator(),
                FxUi.sectionTitle("Account"), new HBox(12, signIn, loginState));

        VBox root = new VBox(20, logo, welcome, card, new HBox(FxUi.hgrow(), finish));
        root.getStyleClass().add("wizard-root");
        root.setPadding(new Insets(48));
        root.setMaxWidth(620);
        root.setAlignment(Pos.TOP_CENTER);

        VBox center = new VBox(root);
        center.setAlignment(Pos.CENTER);
        center.getStyleClass().add("app-root");
        return center;
    }
}
