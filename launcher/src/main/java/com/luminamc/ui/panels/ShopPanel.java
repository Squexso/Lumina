package com.luminamc.ui.panels;

import com.luminamc.shop.Cosmetic;
import com.luminamc.shop.DailyStreak;
import com.luminamc.shop.ShopCatalog;
import com.luminamc.shop.TokenEconomy;
import com.luminamc.shop.TokenPack;
import com.luminamc.ui.AppContext;
import com.luminamc.ui.FxUi;
import com.luminamc.ui.components.CapeTexture;
import com.luminamc.ui.components.CapeView;
import com.luminamc.ui.components.ModelPreview;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Cursor;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.Image;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

/**
 * The Lumina Shop — spend Lumina Tokens (earned by playing) on cosmetics like
 * the Lumina Cape, and, soon, top up with real-money token packs via PayPal.
 */
public final class ShopPanel extends BorderPane {

    private final AppContext ctx;
    private final TokenEconomy wallet;
    private final Runnable onWalletChanged;

    private final Label balanceLabel = new Label();
    private final VBox content = new VBox(18);

    private static final String[] TABS = {"Cosmetics", "Buy Tokens", "Free Tokens"};
    private String activeTab = TABS[0];

    private Label countdownLabel;                       // live next-reset countdown
    private javafx.animation.Animation claimPulse;      // pulsing claim button
    private String selectedId;                          // cosmetic shown in the shop preview
    private com.luminamc.shop.Rarity rarityFilter;      // null = show all rarities

    public ShopPanel(AppContext ctx, Runnable onWalletChanged) {
        this.ctx = ctx;
        this.onWalletChanged = onWalletChanged != null ? onWalletChanged : () -> {};
        this.wallet = new TokenEconomy(ctx.config);
        getStyleClass().add("grid-root");

        // Credit any playtime/welcome tokens earned since we last looked.
        long gained = wallet.sync(ctx.config.totalPlayMillis);

        Label title = new Label("Shop");
        title.getStyleClass().add("page-title");
        HBox header = new HBox(14, title, FxUi.hgrow(), balanceChip());
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(28, 32, 6, 32));

        VBox top = new VBox(10, header, tabBar());
        setTop(top);

        content.setPadding(new Insets(12, 32, 28, 32));
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("cosmic-scroll");
        setCenter(sp);

        rebuild();
        if (gained > 0) toast("+" + String.format("%,d", gained) + " ✦ earned from your playtime!");

        // Live 1-second clock for the daily-reset countdown; stops when this view leaves the scene.
        javafx.animation.Timeline clock = new javafx.animation.Timeline(
                new javafx.animation.KeyFrame(javafx.util.Duration.ZERO, e -> updateCountdown()),
                new javafx.animation.KeyFrame(javafx.util.Duration.seconds(1)));
        clock.setCycleCount(javafx.animation.Animation.INDEFINITE);
        clock.play();
        sceneProperty().addListener((o, was, is) -> {
            if (is == null) { clock.stop(); if (claimPulse != null) claimPulse.stop(); }
        });
    }

    private void updateCountdown() {
        if (countdownLabel != null) countdownLabel.setText(new DailyStreak(ctx.config).countdown());
    }

    private static String fmt(long n) { return String.format("%,d", n); }

    // ── tab bar ───────────────────────────────────────────────────────────

    private HBox tabBar() {
        HBox bar = new HBox(6);
        bar.getStyleClass().add("tab-bar");
        bar.setPadding(new Insets(0, 32, 0, 32));
        bar.setAlignment(Pos.CENTER_LEFT);
        ToggleGroup group = new ToggleGroup();
        for (String tab : TABS) {
            ToggleButton tb = new ToggleButton(tab);
            tb.getStyleClass().add("tab");
            tb.setToggleGroup(group);
            tb.setSelected(tab.equals(activeTab));
            tb.setOnAction(e -> {
                if (!tb.isSelected()) { tb.setSelected(true); return; }
                activeTab = tab;
                rebuild();
            });
            bar.getChildren().add(tb);
        }
        return bar;
    }

    // ── balance chip ─────────────────────────────────────────────────────

    private HBox balanceChip() {
        Label coin = new Label("✦");
        coin.setStyle("-fx-text-fill: #FACC15; -fx-font-size: 16px;");
        balanceLabel.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 15px;");
        Label unit = new Label("Tokens");
        unit.setStyle("-fx-text-fill: #C4B5FD; -fx-font-size: 12px;");
        HBox chip = new HBox(7, coin, balanceLabel, unit);
        chip.setAlignment(Pos.CENTER);
        chip.setPadding(new Insets(7, 14, 7, 14));
        chip.setStyle("-fx-background-color: linear-gradient(to right, #2E1065, #4C1D95);"
                + " -fx-background-radius: 12; -fx-border-color: #7C3AED; -fx-border-radius: 12;");
        return chip;
    }

    private void refreshBalance() {
        balanceLabel.setText(String.format("%,d", wallet.balance()));
    }

    // ── content ───────────────────────────────────────────────────────────

    private void rebuild() {
        refreshBalance();
        if (claimPulse != null) { claimPulse.stop(); claimPulse = null; }   // restarted by claimColumn if needed
        countdownLabel = null;
        switch (activeTab) {
            case "Buy Tokens"  -> content.getChildren().setAll(tokenPacksSection());
            case "Free Tokens" -> content.getChildren().setAll(streakCard(), creatorCodeCard(), redeemLogCard(), howToEarnCard());
            default            -> content.getChildren().setAll(cosmeticsSection());
        }
        onWalletChanged.run();
    }

    private VBox howToEarnCard() {
        Label t = FxUi.sectionTitle("Earn by playing");
        Label how = FxUi.muted("You earn 5 tokens for every hour you play through LuminaMC — plus a "
                + TokenEconomy.WELCOME_BONUS + "-token welcome bonus. They top up automatically; just play.");
        return FxUi.card(t, how);
    }

    // ── daily streak ──────────────────────────────────────────────────────────

    private VBox streakCard() {
        DailyStreak ds = new DailyStreak(ctx.config);

        Label title = FxUi.sectionTitle("Daily Streak");

        Label resetInfo = FxUi.muted("Next reset at 00:00 (CET)   ·   ");
        resetInfo.setWrapText(false);
        countdownLabel = new Label(ds.countdown());
        countdownLabel.setStyle("-fx-text-fill: #FCD34D; -fx-font-weight: bold; -fx-font-size: 14px;"
                + " -fx-font-family: 'Consolas','monospace';");
        HBox cd = new HBox(resetInfo, countdownLabel);
        cd.setAlignment(Pos.CENTER_LEFT);

        HBox columns = new HBox(22, claimColumn(ds), weekTree(ds));
        columns.setAlignment(Pos.TOP_LEFT);

        return FxUi.card(title, cd, columns);
    }

    private VBox claimColumn(DailyStreak ds) {
        boolean can = ds.canClaim();

        Button claim = new Button(can
                ? "Claim daily bonus   +" + fmt(ds.nextReward()) + " ✦"
                : "Already claimed today ✓");
        claim.setMaxWidth(Double.MAX_VALUE);
        claim.setWrapText(true);
        claim.setStyle((can
                ? "-fx-background-color: linear-gradient(to bottom, #8B5CF6, #6D28D9);"
                : "-fx-background-color: #2A2440;")
                + " -fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;"
                + " -fx-background-radius: 12; -fx-padding: 14 18 14 18;"
                + (can ? " -fx-cursor: hand;" : " -fx-text-fill: #8B82A0;"));
        claim.setDisable(!can);
        if (can) {
            FxUi.hoverPop(claim);
            claim.setOnAction(e -> { ds.claim(); rebuild(); });
            startPulse(claim);
        } else if (claimPulse != null) {
            claimPulse.stop();
        }

        int[] boxes = ds.weekBoxes();
        HBox chain = new HBox(7);
        chain.setAlignment(Pos.CENTER);
        for (int i = 0; i < 7; i++) chain.getChildren().add(dayBox(boxes[i], i + 1));

        Label sub = FxUi.muted("Day " + ds.nextDay() + "  ·  Week " + ds.displayWeek()
                + "  ·  today +" + fmt(ds.nextReward()) + " ✦"
                + "   (week total " + fmt(ds.weeklyTotal(ds.displayWeek())) + " ✦)");

        VBox col = new VBox(14, claim, chain, sub);
        col.setMinWidth(300);
        col.setPrefWidth(320);
        col.setAlignment(Pos.TOP_CENTER);
        return col;
    }

    private void startPulse(javafx.scene.Node node) {
        if (claimPulse != null) claimPulse.stop();
        javafx.animation.ScaleTransition st =
                new javafx.animation.ScaleTransition(javafx.util.Duration.millis(950), node);
        st.setFromX(1); st.setToX(1.03); st.setFromY(1); st.setToY(1.03);
        st.setAutoReverse(true);
        st.setCycleCount(javafx.animation.Animation.INDEFINITE);
        st.play();
        claimPulse = st;
    }

    private StackPane dayBox(int state, int dayNum) {
        StackPane b = new StackPane();
        b.setMinSize(36, 36); b.setPrefSize(36, 36); b.setMaxSize(36, 36);
        String bg, border;
        Label inner;
        switch (state) {
            case DailyStreak.DONE -> { bg = "#14532D"; border = "#22C55E"; inner = glyph("✓", "#86EFAC", 16); }
            case DailyStreak.READY -> {
                bg = "linear-gradient(to bottom, #F59E0B, #B45309)"; border = "#FDE68A";
                inner = glyph(String.valueOf(dayNum), "#1A1206", 14);
            }
            default -> { bg = "#241B38"; border = "#3A2F52"; inner = glyph(String.valueOf(dayNum), "#6B6478", 13); }
        }
        b.setStyle("-fx-background-color: " + bg + "; -fx-background-radius: 9;"
                + " -fx-border-color: " + border + "; -fx-border-radius: 9; -fx-border-width: 1.5;");
        b.getChildren().add(inner);
        if (state == DailyStreak.READY) {
            b.setEffect(new javafx.scene.effect.DropShadow(13, javafx.scene.paint.Color.web("#F59E0B")));
        }
        return b;
    }

    private VBox weekTree(DailyStreak ds) {
        HBox strip = new HBox(8);
        strip.setAlignment(Pos.BOTTOM_LEFT);
        strip.setPadding(new Insets(4, 4, 4, 2));
        for (int w = 1; w <= DailyStreak.WEEKS; w++) strip.getChildren().add(weekNode(w, ds));

        ScrollPane sp = new ScrollPane(strip);
        sp.setFitToHeight(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.getStyleClass().add("cosmic-scroll");
        sp.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        sp.setPrefHeight(116);

        Label note = FxUi.muted("Total per week — split evenly across the 7 days.");
        VBox col = new VBox(6, FxUi.sectionTitle("12-Week Progress"), note, sp);
        HBox.setHgrow(col, Priority.ALWAYS);
        return col;
    }

    private VBox weekNode(int week, DailyStreak ds) {
        boolean master = week == DailyStreak.WEEKS;
        int state = ds.weekState(week);

        StackPane icon = new StackPane();
        icon.setMinSize(48, 48); icon.setPrefSize(48, 48); icon.setMaxSize(48, 48);
        String bg, border;
        Label inner;
        if (master) {
            bg = "linear-gradient(to bottom, #FCD34D, #B45309)"; border = "#FDE68A";
            inner = glyph("∞", "#3A2606", 22);
        } else if (state == DailyStreak.DONE) {
            bg = "#14532D"; border = "#22C55E"; inner = glyph("✓", "#86EFAC", 18);
        } else if (state == DailyStreak.READY) {
            bg = "linear-gradient(to bottom, #8B5CF6, #6D28D9)"; border = "#C4B5FD";
            inner = glyph(String.valueOf(week), "white", 16);
        } else {
            bg = "#241B38"; border = "#3A2F52"; inner = glyph(String.valueOf(week), "#6B6478", 15);
        }
        icon.setStyle("-fx-background-color: " + bg + "; -fx-background-radius: 11;"
                + " -fx-border-color: " + border + "; -fx-border-radius: 11; -fx-border-width: 1.5;");
        icon.getChildren().add(inner);
        if (master || state == DailyStreak.READY) {
            icon.setEffect(new javafx.scene.effect.DropShadow(12,
                    javafx.scene.paint.Color.web(master ? "#F59E0B" : "#8B5CF6")));
        }

        Label reward = new Label(master ? "MAX" : fmt(ds.weeklyTotal(week)) + " ✦");
        reward.setStyle("-fx-text-fill: " + (master ? "#FCD34D" : "#FACC15") + ";"
                + " -fx-font-weight: bold; -fx-font-size: 11px;");
        Label wk = new Label(master ? "Week 12+" : "Week " + week);
        wk.setStyle("-fx-text-fill: #9A90B5; -fx-font-size: 10px;");

        VBox node = new VBox(4, icon, reward, wk);
        node.setAlignment(Pos.CENTER);
        node.setMinWidth(60);
        return node;
    }

    private static Label glyph(String text, String color, int size) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: " + color + "; -fx-font-weight: bold; -fx-font-size: " + size + "px;");
        return l;
    }

    // ── creator codes (free tokens) ──────────────────────────────────────────

    private VBox creatorCodeCard() {
        Label t = FxUi.sectionTitle("Creator Code");
        Label info = FxUi.muted("Got a creator code? Redeem it here for free Lumina Tokens.");

        TextField field = new TextField();
        field.setPromptText("Enter a code…");
        field.setPrefWidth(240);

        Button redeem = new Button("Redeem");
        redeem.getStyleClass().add("accent-button");
        FxUi.hoverPop(redeem);

        Label status = FxUi.muted("");

        Runnable doRedeem = () -> {
            String input = field.getText();
            if (input == null || input.isBlank()) return;
            TokenEconomy.RedeemResult res = wallet.redeem(input);
            switch (res.status()) {
                case SUCCESS -> {
                    field.clear();
                    rebuild();   // refreshes balance + the log below (newest entry shown in green)
                }
                case ALREADY_USED -> {
                    status.setText("You've already used that code.");
                    status.setStyle("-fx-text-fill: #FBBF24;");
                }
                case INVALID -> {
                    status.setText("✗ That code isn't valid.");
                    status.setStyle("-fx-text-fill: #F87171;");
                }
            }
        };
        redeem.setOnAction(e -> doRedeem.run());
        field.setOnAction(e -> doRedeem.run());

        HBox row = new HBox(8, field, redeem);
        row.setAlignment(Pos.CENTER_LEFT);
        return FxUi.card(t, info, row, status);
    }

    /** History of redeemed codes — newest first, the most recent highlighted green. */
    private VBox redeemLogCard() {
        Label t = FxUi.sectionTitle("Redeemed codes");
        VBox list = new VBox(7);

        java.util.List<com.luminamc.shop.RedemptionEntry> log = wallet.log();
        if (log.isEmpty()) {
            list.getChildren().add(FxUi.muted("No codes redeemed yet — enter one above."));
        } else {
            for (int i = log.size() - 1; i >= 0; i--) {
                list.getChildren().add(logRow(log.get(i), i == log.size() - 1));
            }
        }
        return FxUi.card(t, list);
    }

    private HBox logRow(com.luminamc.shop.RedemptionEntry e, boolean newest) {
        Label code = new Label("✓  " + e.code);
        code.setStyle("-fx-font-weight: bold; -fx-text-fill: " + (newest ? "#4ADE80" : "#E5E1F0") + ";");

        Label amount = new Label("+" + String.format("%,d", e.tokens) + " ✦");
        amount.setStyle("-fx-font-weight: bold; -fx-text-fill: " + (newest ? "#4ADE80" : "#FACC15") + ";");

        Label when = FxUi.muted(ago(e.time));

        HBox row = new HBox(10, code, FxUi.hgrow(), amount, when);
        row.setAlignment(Pos.CENTER_LEFT);
        return row;
    }

    private static String ago(long time) {
        long s = Math.max(0, (System.currentTimeMillis() - time) / 1000);
        if (s < 60)    return "just now";
        if (s < 3600)  return (s / 60) + " min ago";
        if (s < 86400) return (s / 3600) + " h ago";
        return (s / 86400) + " d ago";
    }

    // ── cosmetics ───────────────────────────────────────────────────────────

    private VBox cosmeticsSection() {
        if (selectedId == null || ShopCatalog.find(selectedId) == null) {
            selectedId = wallet.equippedCape() != null && ShopCatalog.find(wallet.equippedCape()) != null
                    ? wallet.equippedCape() : ShopCatalog.COSMETICS.get(0).id();
        }
        Cosmetic selected = ShopCatalog.find(selectedId);

        Label title = FxUi.sectionTitle("Cosmetics");
        Label hint = FxUi.muted("Click an item to try it on the 3D model (drag to rotate). "
                + "Rarity is shown by the card's colour.");

        HBox cols = new HBox(22, itemPreview(selected), collectionColumn());
        cols.setAlignment(Pos.TOP_LEFT);
        return new VBox(10, title, hint, cols);
    }

    /** Chips that narrow the collection to one rarity (the selected chip glows in its colour). */
    private HBox rarityChips() {
        HBox bar = new HBox(8);
        bar.setAlignment(Pos.CENTER_LEFT);
        ToggleGroup group = new ToggleGroup();
        java.util.List<com.luminamc.shop.Rarity> tiers = new java.util.ArrayList<>();
        tiers.add(null);                                            // "All"
        tiers.addAll(java.util.List.of(com.luminamc.shop.Rarity.values()));
        for (com.luminamc.shop.Rarity r : tiers) {
            ToggleButton chip = new ToggleButton(r == null ? "All" : r.label);
            chip.getStyleClass().add("chip");
            chip.setToggleGroup(group);
            chip.setSelected(r == rarityFilter);
            if (r != null) chip.setStyle("-fx-border-color: " + r.color + "55;");
            chip.setOnAction(e -> {
                if (!chip.isSelected()) { chip.setSelected(true); return; }
                rarityFilter = r;
                rebuild();
            });
            bar.getChildren().add(chip);
        }
        return bar;
    }

    private boolean passesFilter(Cosmetic c) {
        return rarityFilter == null || c.rarity() == rarityFilter;
    }

    private VBox collectionColumn() {
        VBox col = new VBox(14, rarityChips());

        // Capes grouped by rarity — one labelled section per tier, so the big
        // collection reads as a tidy catalogue instead of one endless wall.
        for (com.luminamc.shop.Rarity r : com.luminamc.shop.Rarity.values()) {
            if (rarityFilter != null && r != rarityFilter) continue;
            var tier = ShopCatalog.COSMETICS.stream().filter(c -> c.rarity() == r).toList();
            if (tier.isEmpty()) continue;
            long owned = tier.stream().filter(c -> wallet.owns(c.id())).count();
            FlowPane flow = new FlowPane(12, 12);
            tier.forEach(c -> flow.getChildren().add(itemCard(c)));
            col.getChildren().addAll(tierHeading(r, owned, tier.size()), flow);
        }

        var accs = ShopCatalog.ACCESSORIES.stream().filter(this::passesFilter).toList();
        if (!accs.isEmpty()) {
            long accOwned = accs.stream().filter(c -> wallet.owns(c.id())).count();
            FlowPane flow = new FlowPane(12, 12);
            accs.forEach(c -> flow.getChildren().add(itemCard(c)));
            col.getChildren().addAll(
                    subHeading("ACCESSORIES   ·   " + accOwned + " / " + ShopCatalog.ACCESSORIES.size()), flow);
        }
        HBox.setHgrow(col, Priority.ALWAYS);
        return col;
    }

    /** A rarity section header: coloured dot + name, owned count, and a hairline rule. */
    private HBox tierHeading(com.luminamc.shop.Rarity r, long owned, int total) {
        Label dot = new Label("●");
        dot.setStyle("-fx-text-fill: " + r.color + "; -fx-font-size: 10px;");
        Label name = new Label(r.label.toUpperCase());
        name.setStyle("-fx-text-fill: " + r.color + "; -fx-font-weight: bold; -fx-font-size: 12px;");
        Label count = FxUi.muted(owned + " / " + total);
        Region rule = new Region();
        rule.setPrefHeight(1);
        rule.setStyle("-fx-background-color: linear-gradient(to right, " + r.color + "55, transparent);");
        HBox.setHgrow(rule, Priority.ALWAYS);
        HBox h = new HBox(8, dot, name, count, rule);
        h.setAlignment(Pos.CENTER_LEFT);
        h.setPadding(new Insets(6, 0, 0, 0));
        return h;
    }

    private static Label subHeading(String s) {
        Label l = new Label(s);
        l.setStyle("-fx-text-fill: #C4B5FD; -fx-font-weight: bold; -fx-font-size: 13px;");
        return l;
    }

    /** Big 3D preview of the selected item on the player, framed in its rarity colour. */
    private VBox itemPreview(Cosmetic c) {
        com.luminamc.shop.Rarity r = c.rarity();

        StackPane model = new StackPane();
        model.setMinSize(290, 400);
        model.setPrefSize(290, 400);
        buildPreviewModel(model, c);

        Label name = new Label(c.name());
        name.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 20px;");
        Label rarity = new Label("◆  " + r.label);
        rarity.setStyle("-fx-text-fill: " + r.color + "; -fx-font-weight: bold; -fx-font-size: 13px;");
        Label desc = FxUi.muted(c.description());
        desc.setWrapText(true); desc.setMaxWidth(260); desc.setStyle("-fx-text-alignment: center;");
        Label price = new Label("✦  " + fmt(c.price()));
        price.setStyle("-fx-text-fill: #FACC15; -fx-font-weight: bold; -fx-font-size: 18px;");

        VBox box = new VBox(10, centered(model), centered(name), centered(rarity),
                centered(desc), centered(price), centered(itemAction(c)));
        box.setAlignment(Pos.TOP_CENTER);
        box.setPadding(new Insets(14, 18, 18, 18));
        box.setPrefWidth(330);
        box.setMinWidth(330);
        box.setStyle("-fx-background-color: rgba(18,12,34,0.55); -fx-background-radius: 16;"
                + " -fx-border-color: " + r.color + "; -fx-border-width: 2; -fx-border-radius: 16;");
        DropShadow glow = new DropShadow(28, Color.web(r.color));
        glow.setSpread(0.04);
        box.setEffect(glow);
        return box;
    }

    /** Loads the model wearing the selected item plus whatever is equipped in the other slot. */
    private void buildPreviewModel(StackPane holder, Cosmetic selected) {
        Cosmetic capeC = selected.kind() == Cosmetic.Kind.CAPE ? selected
                : (wallet.equippedCape() != null ? ShopCatalog.cosmetic(wallet.equippedCape()) : null);
        Image capeTex = capeC != null ? CapeTexture.build(capeC) : null;

        Cosmetic accC = selected.kind() == Cosmetic.Kind.ACCESSORY ? selected
                : (wallet.equippedAccessory() != null ? ShopCatalog.accessory(wallet.equippedAccessory()) : null);
        String accType = accC != null ? accC.accessoryType() : null;
        Color accColor = accC != null ? Color.web(accC.colorTop()) : null;

        // Cosmetics live on the back (cape, wings, aura) or above the head (halo), so face
        // the model away from the camera — a ¾-front view hides the cape behind the body.
        ModelPreview.into(holder, previewSkinUrl(), false, capeTex, accType, accColor,
                ModelPreview.BACK_YAW, ModelPreview.BACK_PITCH);
    }

    private String previewSkinUrl() {
        com.luminamc.auth.Account a = ctx.auth.active();
        return (a != null && a.mcUuid != null && !a.mcUuid.isBlank())
                ? com.luminamc.skin.SkinService.skinTextureUrl(a.mcUuid) : null;
    }

    private VBox itemCard(Cosmetic c) {
        com.luminamc.shop.Rarity r = c.rarity();
        boolean owned = wallet.owns(c.id());
        boolean equipped = wallet.isEquipped(c);
        boolean selected = c.id().equals(selectedId);

        StackPane preview = c.kind() == Cosmetic.Kind.CAPE
                ? CapeView.build(86, c)
                : accessoryIcon(c);

        Label name = new Label(c.name());
        name.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 11px;");
        Label state = new Label(equipped ? "● Equipped" : owned ? "✓ Owned" : "✦ " + fmt(c.price()));
        state.setStyle("-fx-font-size: 10px; -fx-text-fill: "
                + (equipped ? "#A855F7" : owned ? "#4ADE80" : "#FACC15") + ";");

        VBox card = new VBox(6, centered(preview), centered(name), centered(state));
        card.setAlignment(Pos.TOP_CENTER);
        card.setPadding(new Insets(10));
        card.setPrefWidth(124);
        // Calm by default — a subtle rarity-tinted border. Only the selected card (and a
        // hovered one) glows, so a 40-item grid doesn't turn into a wall of halos.
        String quiet = "-fx-background-color: rgba(20,14,36,0.55); -fx-background-radius: 12;"
                + " -fx-border-color: " + r.color + "66; -fx-border-width: 1.4; -fx-border-radius: 12;";
        String active = "-fx-background-color: rgba(255,255,255,0.06); -fx-background-radius: 12;"
                + " -fx-border-color: " + r.color + "; -fx-border-width: 2.2; -fx-border-radius: 12;";
        card.setStyle(selected ? active : quiet);
        if (selected) card.setEffect(new DropShadow(22, Color.web(r.color)));
        card.setCursor(Cursor.HAND);
        card.setOnMouseEntered(e -> {
            if (!selected) {
                card.setStyle(active);
                card.setEffect(new DropShadow(14, Color.web(r.color)));
            }
            javafx.animation.ScaleTransition st =
                    new javafx.animation.ScaleTransition(javafx.util.Duration.millis(120), card);
            st.setToX(1.045); st.setToY(1.045); st.play();
        });
        card.setOnMouseExited(e -> {
            if (!selected) {
                card.setStyle(quiet);
                card.setEffect(null);
            }
            javafx.animation.ScaleTransition st =
                    new javafx.animation.ScaleTransition(javafx.util.Duration.millis(120), card);
            st.setToX(1.0); st.setToY(1.0); st.play();
        });
        card.setOnMouseClicked(e -> { selectedId = c.id(); rebuild(); });
        return card;
    }

    private StackPane accessoryIcon(Cosmetic c) {
        String glyph = switch (c.accessoryType() == null ? "" : c.accessoryType()) {
            case "halo" -> "😇"; case "wings" -> "🕊"; case "aura" -> "✨"; default -> "★";
        };
        Label l = new Label(glyph);
        l.setStyle("-fx-font-size: 34px;");
        StackPane p = new StackPane(l);
        p.setMinSize(54, 86); p.setPrefSize(54, 86);
        p.setStyle("-fx-background-color: " + c.colorTop() + "22; -fx-background-radius: 10;");
        return p;
    }

    private Node itemAction(Cosmetic c) {
        boolean owned = wallet.owns(c.id());
        boolean equipped = wallet.isEquipped(c);

        if (!owned) {
            boolean affordable = wallet.canAfford(c.price());
            Button buy = new Button(affordable ? "Buy  ·  ✦ " + fmt(c.price())
                    : "Need " + fmt(wallet.shortfall(c.price())) + " more ✦");
            buy.getStyleClass().add("accent-button");
            buy.setDisable(!affordable);
            buy.setMaxWidth(Double.MAX_VALUE);
            if (affordable) {
                FxUi.hoverPop(buy);
                buy.setOnAction(e -> { if (wallet.buy(c)) { autoEquip(c); rebuild(); } });
            } else {
                Tooltip.install(buy, new Tooltip("Play a little longer (or grab a token pack) to afford it."));
            }
            return buy;
        }
        if (equipped) {
            Button off = new Button("Unequip");
            off.getStyleClass().add("ghost-button");
            off.setMaxWidth(Double.MAX_VALUE);
            off.setOnAction(e -> { wallet.unequip(c); rebuild(); });
            Label eq = new Label("● Equipped");
            eq.setStyle("-fx-text-fill: #A855F7; -fx-font-weight: bold;");
            return new VBox(6, centered(eq), off);
        }
        Button equip = new Button("Equip");
        equip.getStyleClass().add("accent-button");
        equip.setMaxWidth(Double.MAX_VALUE);
        FxUi.hoverPop(equip);
        equip.setOnAction(e -> { wallet.equip(c); rebuild(); });
        return equip;
    }

    private void autoEquip(Cosmetic c) {
        if (c.kind() == Cosmetic.Kind.CAPE && wallet.equippedCape() == null) wallet.equipCape(c.id());
        if (c.kind() == Cosmetic.Kind.ACCESSORY && wallet.equippedAccessory() == null) wallet.equipAccessory(c.id());
    }

    private static VBox centered(Node n) {
        VBox v = new VBox(n);
        v.setAlignment(Pos.CENTER);
        return v;
    }

    // ── token packs (PayPal, coming soon) ────────────────────────────────────

    private VBox tokenPacksSection() {
        Label heading = FxUi.sectionTitle("Buy Lumina Tokens");
        Label note = FxUi.muted("Secure checkout via PayPal — launching soon. Bigger packs include a "
                + "bonus, and your tokens land in your wallet the moment payment clears.");

        FlowPane grid = new FlowPane(12, 12);
        for (TokenPack p : ShopCatalog.TOKEN_PACKS) grid.getChildren().add(packCard(p));

        return FxUi.card(heading, note, grid);
    }

    private VBox packCard(TokenPack p) {
        Label tokens = new Label("✦  " + String.format("%,d", p.tokens()));
        tokens.setStyle("-fx-text-fill: #FACC15; -fx-font-weight: bold; -fx-font-size: 18px;");
        Label tokensUnit = new Label("Lumina Tokens");
        tokensUnit.setStyle("-fx-text-fill: #C4B5FD; -fx-font-size: 11px;");

        Label euros = new Label(String.format("€%.2f", p.euros()));
        euros.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 15px;");

        Button buy = new Button("Coming soon");
        buy.setDisable(true);
        buy.setMaxWidth(Double.MAX_VALUE);
        buy.getStyleClass().add("ghost-button");

        VBox card = new VBox(6, tokens, tokensUnit, euros, buy);
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(16, 14, 14, 14));
        card.setPrefWidth(150);
        card.getStyleClass().add("shot-card");

        if (p.badge() != null) {
            Label badge = new Label(p.badge() + " value");
            badge.setStyle("-fx-background-color: #16A34A; -fx-text-fill: white; -fx-font-size: 10px;"
                    + " -fx-font-weight: bold; -fx-background-radius: 8; -fx-padding: 2 8 2 8;");
            card.getChildren().add(1, badge);
        }
        return card;
    }

    // ── toast ─────────────────────────────────────────────────────────────────

    private void toast(String message) {
        Label toast = new Label(message);
        toast.setStyle("-fx-background-color: #166534; -fx-text-fill: white; -fx-font-weight: bold;"
                + " -fx-background-radius: 10; -fx-padding: 8 14 8 14;");
        toast.setTranslateY(8);
        // Slip it into the scroll content briefly at the top.
        content.getChildren().add(0, toast);
        javafx.animation.PauseTransition wait = new javafx.animation.PauseTransition(javafx.util.Duration.seconds(3.5));
        wait.setOnFinished(e -> {
            javafx.animation.FadeTransition fade =
                    new javafx.animation.FadeTransition(javafx.util.Duration.millis(400), toast);
            fade.setFromValue(1); fade.setToValue(0);
            fade.setOnFinished(ev -> content.getChildren().remove(toast));
            fade.play();
        });
        wait.play();
    }
}
