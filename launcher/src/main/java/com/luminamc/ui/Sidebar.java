package com.luminamc.ui;

import com.luminamc.auth.Account;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Tooltip;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.paint.CycleMethod;
import javafx.scene.paint.LinearGradient;
import javafx.scene.paint.Stop;
import javafx.scene.shape.Rectangle;

/** Left navigation: branding, the active account, nav destinations and quick-links. */
public final class Sidebar extends VBox {

    /** All the things the sidebar can ask the shell to do. */
    public interface Nav {
        void home();
        void skins();
        void shop();
        void settings();
        void newInstance();
        void account();
        void globalScreens();
        void crashLogs();
        void openInstance(com.luminamc.instance.Instance inst);
    }

    private final AppContext ctx;
    private final Nav nav;

    private final StackPane avatarHolder = new StackPane();
    private final Label accountName = new Label();
    private final Label accountState = new Label();

    private final Button homeBtn = navItem("◳", "INSTANCES", () -> select("home"));
    private final Button skinsBtn = navItem("🧥", "WARDROBE", () -> select("skins"));
    private final Button shopBtn = navItem("✦", "SHOP", () -> select("shop"));
    private final Button settingsBtn = navItem("⚙", "SETTINGS", () -> select("settings"));

    private final Label tokenBalance = new Label("0");

    public Sidebar(AppContext ctx, Nav nav) {
        this.ctx = ctx;
        this.nav = nav;

        getStyleClass().add("sidebar");
        setPrefWidth(228);
        setMinWidth(228);
        setPadding(new Insets(20, 16, 18, 16));
        setSpacing(16);

        getChildren().addAll(
                brand(),
                accountSection(),
                tokenSection(),
                navSection(),
                pinnedSection(),
                recentSection(),
                FxUi.vgrow(),
                quickLinks());

        refreshAccount();
        refreshTokens();
        populatePinned();
        populateRecent();
        setActive("home");
    }

    // ── branding ─────────────────────────────────────────────────────────

    private VBox brand() {
        Label tagline = new Label("M I N E C R A F T   L A U N C H E R");
        tagline.getStyleClass().add("tagline");

        Image logoImg = Theme.logo();
        VBox box;
        if (logoImg != null) {
            ImageView iv = new ImageView(logoImg);
            iv.setPreserveRatio(true);
            iv.setSmooth(true);
            iv.setFitWidth(180);
            box = new VBox(4, iv, tagline);
        } else {
            box = new VBox(6, com.luminamc.ui.components.CrystalLogo.build(54, 24), tagline);
        }
        box.setAlignment(Pos.CENTER);
        box.setPadding(new Insets(4, 0, 6, 0));

        // The logo doubles as the appearance switcher: click → accent + background panel.
        box.setCursor(javafx.scene.Cursor.HAND);
        Tooltip.install(box, new Tooltip("Customize appearance"));
        box.setOnMouseClicked(e ->
                new AppearanceDialog(ctx).show(getScene() != null ? getScene().getWindow() : null));

        // Gentle violet glow that breathes around the logo.
        javafx.scene.effect.DropShadow glow = new javafx.scene.effect.DropShadow();
        glow.setColor(javafx.scene.paint.Color.web("#8B5CF6"));
        glow.setRadius(12);
        box.setEffect(glow);
        javafx.animation.Timeline pulse = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.ZERO,
                        new javafx.animation.KeyValue(glow.radiusProperty(), 10)),
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(2.4),
                        new javafx.animation.KeyValue(glow.radiusProperty(), 28)));
        pulse.setAutoReverse(true);
        pulse.setCycleCount(javafx.animation.Animation.INDEFINITE);
        pulse.play();

        return box;
    }

    // ── account ──────────────────────────────────────────────────────────

    private VBox accountSection() {
        Label header = new Label("SIGNED IN");
        header.getStyleClass().add("nav-header");
        Button add = new Button("+");
        add.getStyleClass().add("icon-button");
        add.setOnAction(e -> nav.account());   // manage / add / switch accounts
        Tooltip.install(add, new Tooltip("Add or switch account"));
        HBox headerRow = new HBox(header, FxUi.hgrow(), add);
        headerRow.setAlignment(Pos.CENTER_LEFT);

        avatarHolder.setMinSize(40, 40);
        avatarHolder.setPrefSize(40, 40);
        avatarHolder.setMaxSize(40, 40);

        accountName.getStyleClass().add("account-name");
        accountState.getStyleClass().add("account-state");
        VBox text = new VBox(1, accountName, accountState);
        text.setAlignment(Pos.CENTER_LEFT);

        HBox chip = new HBox(11, avatarHolder, text);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.getStyleClass().add("account-card");
        chip.setOnMouseClicked(e -> nav.account());

        return new VBox(8, headerRow, chip);
    }

    // ── token wallet ─────────────────────────────────────────────────────

    private HBox tokenSection() {
        Label coin = new Label("✦");
        coin.setStyle("-fx-text-fill: #FACC15; -fx-font-size: 15px;");
        tokenBalance.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");
        Label unit = new Label("Tokens");
        unit.setStyle("-fx-text-fill: #C4B5FD; -fx-font-size: 11px;");
        Label go = new Label("Shop ›");
        go.setStyle("-fx-text-fill: #C4B5FD; -fx-font-size: 11px;");

        HBox chip = new HBox(7, coin, tokenBalance, unit, FxUi.hgrow(), go);
        chip.setAlignment(Pos.CENTER_LEFT);
        chip.setPadding(new Insets(8, 12, 8, 12));
        chip.setStyle("-fx-background-color: linear-gradient(to right, #2E1065, #4C1D95);"
                + " -fx-background-radius: 12; -fx-border-color: #7C3AED; -fx-border-radius: 12;");
        chip.setOnMouseClicked(e -> select("shop"));
        Tooltip.install(chip, new Tooltip("Open the Lumina shop"));
        FxUi.hoverPop(chip);
        return chip;
    }

    /** Refreshes the sidebar token balance from config (call after earning/spending). */
    public void refreshTokens() {
        long bal = new com.luminamc.shop.TokenEconomy(ctx.config).balance();
        tokenBalance.setText(String.format("%,d", bal));
    }

    // ── pinned ───────────────────────────────────────────────────────────

    private final VBox pinnedBox = new VBox(4);
    private VBox pinnedSectionBox;

    private VBox pinnedSection() {
        Label header = new Label("PINNED");
        header.getStyleClass().add("nav-header");
        pinnedSectionBox = new VBox(8, header, pinnedBox);
        pinnedSectionBox.setPadding(new Insets(10, 0, 0, 0));
        return pinnedSectionBox;
    }

    /** Shows the user's pinned "main" instances; the whole section hides when none. */
    private void populatePinned() {
        pinnedBox.getChildren().clear();
        var pins = ctx.instances.all().stream().filter(i -> i.pinned).toList();
        boolean any = !pins.isEmpty();
        if (pinnedSectionBox != null) {
            pinnedSectionBox.setVisible(any);
            pinnedSectionBox.setManaged(any);
        }
        for (var inst : pins) pinnedBox.getChildren().add(recentRow(inst));
    }

    // ── recent / most-played ─────────────────────────────────────────────

    private final VBox recentBox = new VBox(4);

    private VBox recentSection() {
        Label header = new Label("RECENT");
        header.getStyleClass().add("nav-header");
        VBox box = new VBox(8, header, recentBox);
        box.setPadding(new Insets(10, 0, 0, 0));
        return box;
    }

    /** Fills the recent list with the up-to-3 most recently played instances (excluding pinned). */
    private void populateRecent() {
        recentBox.getChildren().clear();
        var top = ctx.instances.all().stream()
                .filter(i -> i.lastPlayed > 0 && !i.pinned)
                .sorted((a, b) -> Long.compare(b.lastPlayed, a.lastPlayed))
                .limit(3)
                .toList();
        if (top.isEmpty()) {
            Label hint = new Label("Play an instance to see it here");
            hint.getStyleClass().add("recent-empty");
            hint.setWrapText(true);
            recentBox.getChildren().add(hint);
            return;
        }
        for (var inst : top) recentBox.getChildren().add(recentRow(inst));
    }

    private HBox recentRow(com.luminamc.instance.Instance inst) {
        String s = inst.name == null || inst.name.isBlank() ? "?"
                : inst.name.substring(0, 1).toUpperCase();
        Label tile = new Label(s);
        tile.setMinSize(28, 28);
        tile.setMaxSize(28, 28);
        tile.setAlignment(Pos.CENTER);
        tile.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px;");
        double hue = Math.abs((inst.id != null ? inst.id : s).hashCode() % 360);
        LinearGradient g = new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.hsb(hue, 0.55, 0.78)), new Stop(1, Color.hsb((hue + 28) % 360, 0.65, 0.55)));
        tile.setBackground(new Background(new BackgroundFill(g, new CornerRadii(8), Insets.EMPTY)));

        Label name = new Label(inst.name);
        name.getStyleClass().add("recent-name");

        HBox row = new HBox(9, tile, name);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("recent-row");
        row.setOnMouseClicked(e -> nav.openInstance(inst));
        return row;
    }

    // ── navigation ───────────────────────────────────────────────────────

    private VBox navSection() {
        Label header = new Label("LIBRARY");
        header.getStyleClass().add("nav-header");
        VBox box = new VBox(4, header, homeBtn, skinsBtn, shopBtn, settingsBtn);
        box.setPadding(new Insets(4, 0, 0, 0));
        return box;
    }

    private void select(String key) {
        setActive(key);
        switch (key) {
            case "home"     -> nav.home();
            case "skins"    -> nav.skins();
            case "shop"     -> nav.shop();
            case "settings" -> nav.settings();
        }
    }

    /** Highlights the active nav item without firing its action. */
    public void setActive(String key) {
        homeBtn.pseudoClassStateChanged(ACTIVE, "home".equals(key));
        skinsBtn.pseudoClassStateChanged(ACTIVE, "skins".equals(key));
        shopBtn.pseudoClassStateChanged(ACTIVE, "shop".equals(key));
        settingsBtn.pseudoClassStateChanged(ACTIVE, "settings".equals(key));
    }

    private static final javafx.css.PseudoClass ACTIVE = javafx.css.PseudoClass.getPseudoClass("active");

    private Button navItem(String icon, String text, Runnable action) {
        Label ic = new Label(icon);
        ic.getStyleClass().add("nav-icon");
        Label tx = new Label(text);
        tx.getStyleClass().add("nav-text");
        HBox row = new HBox(12, ic, tx);
        row.setAlignment(Pos.CENTER_LEFT);

        Button b = new Button();
        b.setGraphic(row);
        b.getStyleClass().add("nav-item");
        b.setMaxWidth(Double.MAX_VALUE);
        b.setAlignment(Pos.CENTER_LEFT);
        b.setOnAction(e -> action.run());
        return b;
    }

    // ── quick-links ──────────────────────────────────────────────────────

    private VBox quickLinks() {
        Label header = new Label("QUICK-LINKS");
        header.getStyleClass().add("nav-header");

        VBox screens = quickCard("📷", "Global Screens", nav::globalScreens);
        VBox crash = quickCard("🐞", "Crash Logs", nav::crashLogs);
        HBox row = new HBox(10, screens, crash);
        HBox.setHgrow(screens, Priority.ALWAYS);
        HBox.setHgrow(crash, Priority.ALWAYS);

        Button discord = new Button("💬   Join our Discord");
        discord.setMaxWidth(Double.MAX_VALUE);
        discord.setAlignment(Pos.CENTER);
        discord.setStyle("-fx-background-color: linear-gradient(to right, #5865F2, #8B5CF6);"
                + " -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px;"
                + " -fx-background-radius: 10; -fx-padding: 9 0 9 0; -fx-cursor: hand;");
        discord.setOnAction(e -> openUrl(ctx.config.discordInviteUrl));

        Button feedback = new Button("🐛  Report a bug / Feedback");
        feedback.setMaxWidth(Double.MAX_VALUE);
        feedback.setAlignment(Pos.CENTER);
        feedback.setStyle("-fx-background-color: transparent; -fx-text-fill: #B7AED0;"
                + " -fx-font-size: 11px; -fx-cursor: hand; -fx-border-color: #3A2F52;"
                + " -fx-border-radius: 8; -fx-background-radius: 8; -fx-padding: 6 0 6 0;");
        feedback.setOnAction(e -> openUrl(ctx.config.discordInviteUrl));

        FxUi.hoverPop(discord);
        FxUi.hoverPop(feedback);
        return new VBox(8, header, row, discord, feedback);
    }

    /** Opens a URL in the user's default browser (best-effort). */
    private void openUrl(String url) {
        if (url == null || url.isBlank()) return;
        try {
            java.awt.Desktop.getDesktop().browse(java.net.URI.create(url));
        } catch (Exception ignored) {
            // no desktop browser available — silently ignore
        }
    }

    private VBox quickCard(String icon, String text, Runnable action) {
        Label ic = new Label(icon);
        ic.setStyle("-fx-font-size: 18px;");
        Label tx = new Label(text);
        tx.getStyleClass().add("quicklink-text");
        VBox box = new VBox(6, ic, tx);
        box.setAlignment(Pos.CENTER);
        box.getStyleClass().add("quicklink-card");
        box.setMaxWidth(Double.MAX_VALUE);
        box.setOnMouseClicked(e -> action.run());
        FxUi.hoverPop(box);
        return box;
    }

    // ── account rendering ────────────────────────────────────────────────

    /** Refreshes the account chip + token balance + pinned + recent lists. */
    public void refresh() { refreshAccount(); refreshTokens(); populatePinned(); populateRecent(); }

    public void refreshAccount() {
        Account a = ctx.auth.active();
        if (a == null) {
            accountName.setText("Not signed in");
            accountState.setText("Click to sign in");
            accountState.setStyle("-fx-text-fill: #8C8C9C;");
            avatarHolder.getChildren().setAll(fallbackAvatar("?"));
            return;
        }
        accountName.setText(a.username);
        boolean offline = "offline".equals(a.type);
        accountState.setText(offline ? "● Offline" : "● Ready");
        accountState.setStyle(offline ? "-fx-text-fill: #9A9AA8;" : "-fx-text-fill: #4ADE80;");

        // Default to a gradient initial, then try to load the real player head.
        avatarHolder.getChildren().setAll(fallbackAvatar(
                a.username.isEmpty() ? "?" : a.username.substring(0, 1).toUpperCase()));
        loadHeadAsync(a);
    }

    private void loadHeadAsync(Account a) {
        if (a.mcUuid == null || a.mcUuid.isBlank()) return;
        String url = "https://mc-heads.net/avatar/" + a.mcUuid.replace("-", "") + "/40";
        try {
            Image img = new Image(url, 40, 40, true, true, true); // background load
            ImageView iv = new ImageView(img);
            iv.setFitWidth(40);
            iv.setFitHeight(40);
            Rectangle clip = new Rectangle(40, 40);
            clip.setArcWidth(13);
            clip.setArcHeight(13);
            iv.setClip(clip);
            img.progressProperty().addListener((o, was, p) -> {
                if (p.doubleValue() >= 1.0 && !img.isError()) {
                    javafx.application.Platform.runLater(() -> avatarHolder.getChildren().setAll(iv));
                }
            });
            img.errorProperty().addListener((o, was, err) -> { /* keep fallback */ });
        } catch (Exception ignored) {
            // keep fallback
        }
    }

    private StackPane fallbackAvatar(String letter) {
        Label l = new Label(letter);
        l.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 17px;");
        StackPane p = new StackPane(l);
        p.setMinSize(40, 40);
        p.setPrefSize(40, 40);
        p.setMaxSize(40, 40);
        LinearGradient g = new LinearGradient(0, 0, 1, 1, true, CycleMethod.NO_CYCLE,
                new Stop(0, Color.web("#8B5CF6")), new Stop(1, Color.web("#5B21B6")));
        p.setBackground(new Background(new BackgroundFill(g, new CornerRadii(13), Insets.EMPTY)));
        return p;
    }
}
