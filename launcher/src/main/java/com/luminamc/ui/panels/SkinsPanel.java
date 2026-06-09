package com.luminamc.ui.panels;

import com.luminamc.auth.Account;
import com.luminamc.shop.Cosmetic;
import com.luminamc.shop.ShopCatalog;
import com.luminamc.shop.TokenEconomy;
import com.luminamc.skin.SkinLibrary;
import com.luminamc.skin.SkinService;
import com.luminamc.ui.AppContext;
import com.luminamc.ui.FxUi;
import com.luminamc.ui.LuminaDialog;
import com.luminamc.ui.components.CapeTexture;
import com.luminamc.ui.components.CapeView;
import com.luminamc.ui.components.ModelPreview;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.SubScene;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.scene.shape.Rectangle;

import java.io.ByteArrayInputStream;

/**
 * Wardrobe — an interactive 3D character model on the left (drag to rotate), with
 * Skins / Capes / Accessories tabs and a saved-skin library on the right. Search a
 * player by name to try their skin on the model and apply it to your account, and
 * equip the Lumina Cape (which appears on the 3D model in real time).
 */
public final class SkinsPanel extends BorderPane {

    private final AppContext ctx;
    private final SkinLibrary library = SkinLibrary.load();
    private final TokenEconomy wallet;

    private String tab = "Skins";

    private final TextField nameField = new TextField();
    private final Label status = FxUi.muted("");
    private final Button applyBtn = new Button("✓  Apply to my account");

    // Currently previewed skin.
    private String previewUuid;
    private String previewName;
    private String previewSkinUrl;     // raw 64×64 texture URL (null → derive from uuid)
    private boolean previewSlim;
    private SkinService.Profile found;
    private SkinService.Skin foundSkin;

    private final VBox leftHolder = new VBox();
    private final VBox rightHolder = new VBox(18);

    public SkinsPanel(AppContext ctx) {
        this.ctx = ctx;
        this.wallet = new TokenEconomy(ctx.config);
        getStyleClass().add("grid-root");

        Account a = ctx.auth.active();
        if (a != null && a.mcUuid != null && !a.mcUuid.isBlank()) {
            previewUuid = a.mcUuid;
            previewName = a.username;
        }

        Label title = new Label("Wardrobe");
        title.getStyleClass().add("page-title");
        HBox header = new HBox(title);
        header.setPadding(new Insets(28, 32, 8, 32));
        setTop(header);

        HBox.setHgrow(rightHolder, Priority.ALWAYS);
        HBox columns = new HBox(20, leftHolder, rightHolder);
        columns.setAlignment(Pos.TOP_LEFT);
        VBox content = new VBox(columns);
        content.setPadding(new Insets(8, 32, 26, 32));
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("cosmic-scroll");
        setCenter(sp);

        applyBtn.getStyleClass().add("accent-button");
        applyBtn.setDisable(true);
        applyBtn.setOnAction(e -> apply());
        nameField.setPromptText("Enter a Minecraft name…");
        nameField.setOnAction(e -> search());

        refreshHero();
        refreshRight();
    }

    // ── left: interactive 3D preview ────────────────────────────────────────

    private void refreshHero() {
        String capeId = wallet.equippedCape();
        Cosmetic capeC = capeId != null ? ShopCatalog.cosmetic(capeId) : null;
        Image capeTex = capeC != null
                ? CapeTexture.build(Color.web(capeC.colorTop()), Color.web(capeC.colorBottom())) : null;

        String accId = wallet.equippedAccessory();
        Cosmetic accC = accId != null ? ShopCatalog.accessory(accId) : null;
        String accType = accC != null ? accC.accessoryType() : null;
        Color accColor = accC != null ? Color.web(accC.colorTop()) : null;

        String url = previewSkinUrl != null ? previewSkinUrl
                : (previewUuid != null ? SkinService.skinTextureUrl(previewUuid) : null);

        StackPane stage3d = new StackPane();
        stage3d.setMinSize(300, 420);
        stage3d.setPrefSize(300, 420);
        // When a cape or back/head accessory is equipped, face the model backward so it's
        // fully visible and centred; otherwise show the face (front) for the skin itself.
        boolean showBack = capeTex != null || accType != null;
        double yaw   = showBack ? ModelPreview.BACK_YAW   : 137;
        double pitch = showBack ? ModelPreview.BACK_PITCH : -4;
        ModelPreview.into(stage3d, url, previewSlim, capeTex, accType, accColor, yaw, pitch);

        Label name = new Label(previewName != null ? previewName : "Not signed in");
        name.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 15px;");

        HBox chips = new HBox(8);
        chips.setAlignment(Pos.CENTER);
        chips.getChildren().add(capeC != null ? equippedChip("🧥 " + capeC.name()) : mutedChip("No cape"));
        if (accC != null) chips.getChildren().add(equippedChip("✨ " + accC.name()));

        Label hint = FxUi.muted("🖱  Drag to rotate");
        hint.setStyle("-fx-text-fill: #8C8C9C; -fx-font-size: 11px;");

        VBox card = FxUi.card(FxUi.sectionTitle("Preview"), centered(stage3d),
                centered(name), centered(chips), centered(hint));
        card.setPrefWidth(360);
        card.setMinWidth(330);
        leftHolder.getChildren().setAll(card);
    }

    // ── right column: tabs + library ────────────────────────────────────────

    private void refreshRight() {
        rightHolder.getChildren().setAll(tabsCard(), libraryCard());
    }

    private VBox tabsCard() {
        HBox bar = new HBox(6);
        bar.getStyleClass().add("tab-bar");
        bar.setAlignment(Pos.CENTER_LEFT);
        ToggleGroup group = new ToggleGroup();
        for (String t : new String[]{"Skins", "Capes", "Accessories"}) {
            ToggleButton tb = new ToggleButton(t);
            tb.getStyleClass().add("tab");
            tb.setToggleGroup(group);
            tb.setSelected(t.equals(tab));
            tb.setOnAction(e -> {
                if (!tb.isSelected()) { tb.setSelected(true); return; }
                tab = t;
                refreshRight();
            });
            bar.getChildren().add(tb);
        }

        Node body = switch (tab) {
            case "Capes"       -> capesTab();
            case "Accessories" -> accessoriesTab();
            default            -> skinsTab();
        };
        return FxUi.card(bar, new Separator(), body);
    }

    // ── Skins tab ────────────────────────────────────────────────────────────

    private Node skinsTab() {
        Button search = new Button("🔍  Search & Equip");
        search.setStyle("-fx-background-color: linear-gradient(to right, #EC4899, #DB2777);"
                + " -fx-text-fill: white; -fx-font-weight: bold; -fx-background-radius: 10;"
                + " -fx-padding: 9 16 9 16; -fx-cursor: hand;");
        FxUi.hoverPop(search);
        search.setOnAction(e -> search());

        HBox searchRow = new HBox(8, nameField, search);
        HBox.setHgrow(nameField, Priority.ALWAYS);
        searchRow.setAlignment(Pos.CENTER_LEFT);

        HBox applyRow = new HBox(FxUi.hgrow(), applyBtn);
        applyRow.setAlignment(Pos.CENTER_RIGHT);

        return new VBox(10,
                FxUi.sectionTitle("Use another player's skin"),
                FxUi.muted("Type a username → try it on the 3D model → apply it to your account. "
                        + "Found skins are saved to your library."),
                searchRow, status, applyRow);
    }

    // ── Capes tab ────────────────────────────────────────────────────────────

    private Node capesTab() {
        FlowPane grid = new FlowPane(12, 12);
        for (Cosmetic c : ShopCatalog.COSMETICS) grid.getChildren().add(wardrobeCapeCard(c));

        long owned = ShopCatalog.COSMETICS.stream().filter(c -> wallet.owns(c.id())).count();
        return new VBox(8,
                FxUi.sectionTitle("Capes  (" + owned + " / " + ShopCatalog.COSMETICS.size() + ")"),
                FxUi.muted("Click a cape you own to wear it — it appears on the 3D model instantly. Buy more in the Shop."),
                grid);
    }

    private VBox wardrobeCapeCard(Cosmetic c) {
        boolean owned = wallet.owns(c.id());
        boolean equipped = wallet.isCapeEquipped(c.id());

        StackPane preview = CapeView.build(96, c.colorTop(), c.colorBottom());
        if (!owned) preview.setOpacity(0.4);

        Label name = new Label(c.name());
        name.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px;");

        Node action;
        if (!owned) {
            Label lock = new Label("🔒  In Shop");
            lock.setStyle("-fx-text-fill: #8C8C9C; -fx-font-size: 11px;");
            action = lock;
        } else if (equipped) {
            Label eq = new Label("● Equipped · click to remove");
            eq.setStyle("-fx-text-fill: #A855F7; -fx-font-weight: bold; -fx-font-size: 10px;");
            action = eq;
        } else {
            Label eq = new Label("Click to wear");
            eq.setStyle("-fx-text-fill: #C4B5FD; -fx-font-size: 11px;");
            action = eq;
        }

        return collectibleCard(preview, name, action, c.rarity(), owned, equipped, () -> {
            if (equipped) wallet.unequipCape(); else wallet.equipCape(c.id());
            refreshHero(); refreshRight();
        });
    }

    // ── Accessories tab ───────────────────────────────────────────────────────

    private Node accessoriesTab() {
        FlowPane grid = new FlowPane(12, 12);
        for (Cosmetic c : ShopCatalog.ACCESSORIES) grid.getChildren().add(wardrobeAccessoryCard(c));

        long owned = ShopCatalog.ACCESSORIES.stream().filter(c -> wallet.owns(c.id())).count();
        return new VBox(8,
                FxUi.sectionTitle("Accessories  (" + owned + " / " + ShopCatalog.ACCESSORIES.size() + ")"),
                FxUi.muted("Worn on top of your skin and cape. Click one you own to wear it; click again to remove. Buy more in the Shop."),
                grid);
    }

    private VBox wardrobeAccessoryCard(Cosmetic c) {
        boolean owned = wallet.owns(c.id());
        boolean equipped = wallet.isAccessoryEquipped(c.id());

        StackPane preview = accessoryIcon(c);
        if (!owned) preview.setOpacity(0.4);

        Label name = new Label(c.name());
        name.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 12px;");

        Label action = new Label(!owned ? "🔒  In Shop" : equipped ? "● Equipped · click to remove" : "Click to wear");
        action.setStyle("-fx-font-size: " + (equipped ? "10px" : "11px") + "; -fx-text-fill: "
                + (!owned ? "#8C8C9C" : equipped ? "#A855F7" : "#C4B5FD") + ";");

        return collectibleCard(preview, name, action, c.rarity(), owned, equipped, () -> {
            if (equipped) wallet.unequipAccessory(); else wallet.equipAccessory(c.id());
            refreshHero(); refreshRight();
        });
    }

    /** A rarity-framed collectible tile shared by capes & accessories. */
    private VBox collectibleCard(Node preview, Label name, Node action, com.luminamc.shop.Rarity r,
                                 boolean owned, boolean equipped, Runnable onToggle) {
        VBox card = new VBox(7, centered(preview), centered(name), centered(action));
        card.setAlignment(Pos.TOP_CENTER);
        card.setPadding(new Insets(10));
        card.setPrefWidth(148);
        card.setStyle("-fx-background-color: rgba(255,255,255,0.03); -fx-background-radius: 12;"
                + " -fx-border-color: " + r.color + "; -fx-border-width: " + (equipped ? 2.2 : 1.4)
                + "; -fx-border-radius: 12;");
        card.setEffect(new javafx.scene.effect.DropShadow(equipped ? 20 : (owned ? 9 : 4), Color.web(r.color)));
        if (owned) {
            card.setOnMouseClicked(e -> onToggle.run());
            card.setCursor(javafx.scene.Cursor.HAND);
        }
        return card;
    }

    private StackPane accessoryIcon(Cosmetic c) {
        String glyph = switch (c.accessoryType() == null ? "" : c.accessoryType()) {
            case "halo" -> "😇"; case "wings" -> "🕊"; case "aura" -> "✨"; default -> "★";
        };
        Label l = new Label(glyph);
        l.setStyle("-fx-font-size: 40px;");
        StackPane p = new StackPane(l);
        p.setMinSize(70, 96); p.setPrefSize(70, 96);
        p.setStyle("-fx-background-color: " + c.colorTop() + "22; -fx-background-radius: 10;");
        return p;
    }

    // ── library ───────────────────────────────────────────────────────────────

    private VBox libraryCard() {
        HBox row = new HBox(10);
        row.setAlignment(Pos.CENTER_LEFT);
        if (library.skins.isEmpty()) {
            row.getChildren().add(FxUi.muted("No saved skins yet — search a player to add one."));
        } else {
            for (SkinLibrary.Entry e : library.skins) row.getChildren().add(libraryTile(e));
        }
        ScrollPane sp = new ScrollPane(row);
        sp.setFitToHeight(true);
        sp.setHbarPolicy(ScrollPane.ScrollBarPolicy.AS_NEEDED);
        sp.setVbarPolicy(ScrollPane.ScrollBarPolicy.NEVER);
        sp.getStyleClass().add("cosmic-scroll");
        sp.setStyle("-fx-background: transparent; -fx-background-color: transparent;");
        sp.setPrefHeight(108);
        return FxUi.card(FxUi.sectionTitle("Saved skins — click to try on"), sp);
    }

    private StackPane libraryTile(SkinLibrary.Entry e) {
        ImageView head = new ImageView();
        head.setFitWidth(52); head.setFitHeight(52);
        try { head.setImage(new Image("https://mc-heads.net/avatar/" + e.uuid + "/52", 52, 52, true, true, true)); }
        catch (Exception ignored) {}
        Rectangle clip = new Rectangle(52, 52); clip.setArcWidth(12); clip.setArcHeight(12);
        head.setClip(clip);

        Label name = new Label(e.name);
        name.setStyle("-fx-text-fill: #C4B5FD; -fx-font-size: 10px;");
        name.setMaxWidth(64);
        VBox card = new VBox(4, centered(head), centered(name));
        card.setAlignment(Pos.CENTER);
        card.setPadding(new Insets(8, 6, 6, 6));
        card.getStyleClass().add("shot-card");
        card.setOnMouseClicked(ev -> loadEntry(e));

        Label del = new Label("✕");
        del.setStyle("-fx-text-fill: white; -fx-background-color: rgba(220,38,38,0.85);"
                + " -fx-background-radius: 8; -fx-padding: 0 5 1 5; -fx-font-size: 10px; -fx-cursor: hand;");
        del.setOnMouseClicked(ev -> { ev.consume(); library.remove(e); refreshRight(); });
        StackPane tile = new StackPane(card, del);
        StackPane.setAlignment(del, Pos.TOP_RIGHT);
        tile.setMaxSize(Region.USE_PREF_SIZE, Region.USE_PREF_SIZE);
        FxUi.hoverPop(card);
        return tile;
    }

    private void loadEntry(SkinLibrary.Entry e) {
        found = new SkinService.Profile(e.uuid, e.name);
        foundSkin = new SkinService.Skin(e.url, e.slim);
        previewUuid = e.uuid;
        previewName = e.name + (e.slim ? "  (slim)" : "");
        previewSkinUrl = e.url;
        previewSlim = e.slim;
        status.setText("Trying \"" + e.name + "\" on — apply it to your account to keep it.");
        applyBtn.setDisable(false);
        refreshHero();
        refreshRight();
    }

    // ── actions ─────────────────────────────────────────────────────────────

    private void search() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) return;
        status.setText("Searching…");
        applyBtn.setDisable(true);

        new Thread(() -> {
            try {
                SkinService.Profile p = SkinService.resolve(name);
                if (p == null) {
                    Platform.runLater(() -> status.setText("No player named \"" + name + "\" found."));
                    return;
                }
                SkinService.Skin skin = SkinService.fetchSkin(p.uuid());
                Platform.runLater(() -> {
                    found = p; foundSkin = skin;
                    previewUuid = p.uuid();
                    previewName = p.name() + (skin != null && skin.slim() ? "  (slim)" : "");
                    previewSkinUrl = skin != null ? skin.url() : null;
                    previewSlim = skin != null && skin.slim();
                    status.setText(skin != null ? "Found! Trying it on the model." : "Skin not readable.");
                    applyBtn.setDisable(skin == null);
                    if (skin != null) {
                        library.add(new SkinLibrary.Entry(p.name(), p.uuid(), skin.url(), skin.slim()));
                    }
                    refreshHero();
                    refreshRight();
                });
            } catch (Exception ex) {
                Platform.runLater(() -> status.setText("Search failed: " + ex.getMessage()));
            }
        }, "luminamc-skin-search").start();
    }

    private void apply() {
        if (found == null || foundSkin == null) return;
        Account acc = ctx.auth.active();
        if (acc == null || "offline".equals(acc.type)) {
            LuminaDialog.error(getScene() != null ? getScene().getWindow() : null,
                    "Microsoft account needed", "Sign in with a Microsoft account to change your skin.");
            return;
        }
        if (!LuminaDialog.confirm(getScene() != null ? getScene().getWindow() : null,
                "Apply skin", "Change " + acc.username + "'s skin to " + found.name() + "'s skin?")) return;

        status.setText("Applying…");
        applyBtn.setDisable(true);
        new Thread(() -> {
            try {
                byte[] png = SkinService.download(foundSkin.url());
                SkinService.apply(acc, png, foundSkin.slim() ? "slim" : "classic");
                Platform.runLater(() -> {
                    status.setText("✓ Skin applied! It may take a moment to update in-game.");
                    applyBtn.setDisable(false);
                });
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    status.setText("Failed: " + ex.getMessage());
                    applyBtn.setDisable(false);
                });
            }
        }, "luminamc-skin-apply").start();
    }

    // ── small helpers ─────────────────────────────────────────────────────────

    private static VBox centered(Node n) {
        VBox v = new VBox(n);
        v.setAlignment(Pos.CENTER);
        return v;
    }

    private static HBox equippedChip(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #E9D5FF; -fx-font-size: 11px; -fx-font-weight: bold;"
                + " -fx-background-color: rgba(124,58,237,0.28); -fx-background-radius: 9;"
                + " -fx-border-color: #7C3AED; -fx-border-radius: 9; -fx-padding: 4 10 4 10;");
        HBox h = new HBox(l); h.setAlignment(Pos.CENTER);
        return h;
    }

    private static HBox mutedChip(String text) {
        Label l = new Label(text);
        l.setStyle("-fx-text-fill: #8C8C9C; -fx-font-size: 11px;"
                + " -fx-background-color: rgba(255,255,255,0.05); -fx-background-radius: 9; -fx-padding: 4 10 4 10;");
        HBox h = new HBox(l); h.setAlignment(Pos.CENTER);
        return h;
    }
}
