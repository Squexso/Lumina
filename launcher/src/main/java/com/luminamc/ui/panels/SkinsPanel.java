package com.luminamc.ui.panels;

import com.luminamc.auth.Account;
import com.luminamc.skin.SkinLibrary;
import com.luminamc.skin.SkinService;
import com.luminamc.ui.AppContext;
import com.luminamc.ui.FxUi;
import com.luminamc.ui.LuminaDialog;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;

/**
 * Skins page — preview your current skin, "steal" another player's skin by name,
 * and keep a saved library to switch between skins anytime.
 */
public final class SkinsPanel extends BorderPane {

    private final AppContext ctx;
    private final SkinLibrary library = SkinLibrary.load();

    private final TextField nameField = new TextField();
    private final ImageView resultView = new ImageView();
    private final Label resultName = new Label();
    private final Label status = FxUi.muted("");
    private final Button applyBtn = new Button("Use this skin");
    private final FlowPane libraryGrid = new FlowPane(10, 10);

    private SkinService.Profile found;
    private SkinService.Skin foundSkin;

    public SkinsPanel(AppContext ctx) {
        this.ctx = ctx;
        getStyleClass().add("grid-root");

        Label title = new Label("Skins");
        title.getStyleClass().add("page-title");
        HBox header = new HBox(title);
        header.setPadding(new Insets(28, 32, 12, 32));
        setTop(header);

        VBox content = new VBox(18, new HBox(20, yourSkinCard(), stealCard()), libraryCard());
        content.setPadding(new Insets(8, 32, 24, 32));
        ScrollPane sp = new ScrollPane(content);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("cosmic-scroll");
        setCenter(sp);

        refreshLibrary();
    }

    // ── your current skin ─────────────────────────────────────────────────

    private VBox yourSkinCard() {
        Account a = ctx.auth.active();
        ImageView mine = new ImageView();
        mine.setFitWidth(140);
        mine.setPreserveRatio(true);
        if (a != null && a.mcUuid != null && !a.mcUuid.isBlank()) {
            try { mine.setImage(new Image(SkinService.bodyRenderUrl(a.mcUuid), 140, 280, true, true, true)); }
            catch (Exception ignored) {}
        }
        Label who = new Label(a != null ? a.username : "Not signed in");
        who.getStyleClass().add("row-label");
        VBox box = FxUi.card(FxUi.sectionTitle("Your skin"), centered(mine), centered(who));
        box.setPrefWidth(200);
        return box;
    }

    // ── steal a skin ────────────────────────────────────────────────────────

    private VBox stealCard() {
        nameField.setPromptText("Enter a player name…");
        Button search = new Button("🔍  Search");
        search.getStyleClass().add("accent-button");
        search.setOnAction(e -> search());
        nameField.setOnAction(e -> search());
        HBox searchRow = new HBox(8, nameField, search);
        HBox.setHgrow(nameField, Priority.ALWAYS);
        searchRow.setAlignment(Pos.CENTER_LEFT);

        resultView.setFitWidth(140);
        resultView.setPreserveRatio(true);
        resultName.getStyleClass().add("row-label");

        applyBtn.getStyleClass().add("accent-button");
        applyBtn.setDisable(true);
        applyBtn.setOnAction(e -> apply());

        VBox box = FxUi.card(
                FxUi.sectionTitle("Use another player's skin"),
                FxUi.muted("Type a username, preview the skin, then apply it. Found skins are saved to your library."),
                searchRow,
                centered(resultView),
                centered(resultName),
                status,
                new HBox(FxUi.hgrow(), applyBtn));
        box.setPrefWidth(360);
        HBox.setHgrow(box, Priority.ALWAYS);
        return box;
    }

    // ── library ───────────────────────────────────────────────────────────

    private VBox libraryCard() {
        return FxUi.card(FxUi.sectionTitle("Saved skins — click to load, then Use"), libraryGrid);
    }

    private void refreshLibrary() {
        libraryGrid.getChildren().clear();
        if (library.skins.isEmpty()) {
            libraryGrid.getChildren().add(FxUi.muted("No saved skins yet — search a player to add one."));
            return;
        }
        for (SkinLibrary.Entry e : library.skins) libraryGrid.getChildren().add(libraryEntry(e));
    }

    private VBox libraryEntry(SkinLibrary.Entry e) {
        ImageView head = new ImageView();
        head.setFitWidth(48); head.setFitHeight(48);
        try { head.setImage(new Image("https://mc-heads.net/avatar/" + e.uuid + "/48", 48, 48, true, true, true)); }
        catch (Exception ignored) {}
        Label name = FxUi.muted(e.name);
        name.getStyleClass().add("card-detail");

        Button remove = new Button("✕");
        remove.getStyleClass().add("icon-button");
        remove.setOnAction(ev -> { library.remove(e); refreshLibrary(); });

        VBox box = new VBox(4, centered(head), centered(name), centered(remove));
        box.getStyleClass().add("shot-card");
        box.setPadding(new Insets(8));
        box.setPrefWidth(86);
        box.setOnMouseClicked(ev -> {
            if (ev.getTarget() != remove) loadEntry(e);
        });
        return box;
    }

    private void loadEntry(SkinLibrary.Entry e) {
        found = new SkinService.Profile(e.uuid, e.name);
        foundSkin = new SkinService.Skin(e.url, e.slim);
        resultName.setText(e.name + (e.slim ? "  (slim)" : ""));
        try { resultView.setImage(new Image(SkinService.bodyRenderUrl(e.uuid), 140, 280, true, true, true)); }
        catch (Exception ignored) {}
        status.setText("Loaded \"" + e.name + "\" — click Use this skin to apply.");
        applyBtn.setDisable(false);
    }

    // ── actions ─────────────────────────────────────────────────────────────

    private void search() {
        String name = nameField.getText().trim();
        if (name.isEmpty()) return;
        status.setText("Searching…");
        applyBtn.setDisable(true);
        resultName.setText("");
        resultView.setImage(null);

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
                    resultName.setText(p.name() + (skin != null && skin.slim() ? "  (slim)" : ""));
                    try { resultView.setImage(new Image(SkinService.bodyRenderUrl(p.uuid()), 140, 280, true, true, true)); }
                    catch (Exception ignored) {}
                    status.setText("");
                    applyBtn.setDisable(skin == null);
                    if (skin != null) {
                        library.add(new SkinLibrary.Entry(p.name(), p.uuid(), skin.url(), skin.slim()));
                        refreshLibrary();
                    }
                });
            } catch (Exception ex) {
                Platform.runLater(() -> status.setText("Lookup failed: " + ex.getMessage()));
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
        boolean ok = LuminaDialog.confirm(getScene() != null ? getScene().getWindow() : null,
                "Apply skin", "Change " + acc.username + "'s skin to " + found.name() + "'s skin?");
        if (!ok) return;

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

    private static VBox centered(javafx.scene.Node n) {
        VBox v = new VBox(n);
        v.setAlignment(Pos.CENTER);
        return v;
    }
}
