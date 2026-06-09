package com.luminamc.ui;

import com.luminamc.auth.Account;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.Window;

import java.awt.Desktop;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Account-management dialog: signed-in accounts, Microsoft PKCE login, offline.
 *
 * <p>The Microsoft button ALWAYS does something — it starts the PKCE browser flow
 * immediately. If the client id is not configured, a setup guide is shown alongside
 * the browser button so the user can fix it without restarting.
 */
public final class AccountDialog {

    private final AppContext ctx;
    private final Stage stage = new Stage(javafx.stage.StageStyle.TRANSPARENT);
    private final ScrollPane scroll = new ScrollPane();
    private Runnable onChanged = () -> {};

    public AccountDialog(AppContext ctx) {
        this.ctx = ctx;
    }

    public void show(Window owner, Runnable onChanged) {
        this.onChanged = onChanged;
        scroll.setFitToWidth(true);
        scroll.getStyleClass().addAll("content-scroll", "account-scroll");

        renderIdle();

        // Frameless Lumina header (title + close) instead of the OS title bar.
        Label titleLabel = new Label("✦  Account");
        titleLabel.getStyleClass().add("dialog-title");
        Button x = new Button("✕");
        x.getStyleClass().add("icon-button");
        x.setOnAction(e -> stage.close());
        HBox header = new HBox(10, titleLabel, FxUi.hgrow(), x);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(2, 2, 6, 4));

        VBox outer = new VBox(0, header, scroll);
        outer.getStyleClass().add("lumina-dialog");
        outer.setPadding(new Insets(14, 14, 12, 14));
        VBox.setVgrow(scroll, Priority.ALWAYS);

        // Drag the frameless window by the header.
        final double[] drag = new double[2];
        header.setOnMousePressed(e -> { drag[0] = e.getScreenX() - stage.getX(); drag[1] = e.getScreenY() - stage.getY(); });
        header.setOnMouseDragged(e -> { stage.setX(e.getScreenX() - drag[0]); stage.setY(e.getScreenY() - drag[1]); });

        Scene scene = new Scene(outer, 500, 540);
        scene.setFill(javafx.scene.paint.Color.TRANSPARENT);
        Theme.apply(scene);
        Theme.applyAccent(scene, ctx.config.accentColor);
        stage.setScene(scene);
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setResizable(false);
        stage.setOnShown(e -> {
            if (owner != null) {
                stage.setX(owner.getX() + (owner.getWidth() - outer.getWidth()) / 2);
                stage.setY(owner.getY() + (owner.getHeight() - outer.getHeight()) / 2);
            }
        });
        stage.show();
    }

    // ── screens ──────────────────────────────────────────────────────────

    private void renderIdle() {
        VBox root = new VBox(16);
        root.getStyleClass().add("app-root");
        root.setPadding(new Insets(28));

        root.getChildren().add(FxUi.h1("Accounts"));

        // List of ALL accounts — click one to make it active, with a remove button.
        Account active = ctx.auth.active();
        if (!ctx.auth.accounts.isEmpty()) {
            VBox listBox = new VBox(8);
            listBox.getChildren().add(FxUi.sectionTitle("Your accounts — click to switch"));
            for (Account a : ctx.auth.accounts) {
                listBox.getChildren().add(accountRow(a, active));
            }
            root.getChildren().add(FxUi.card(listBox.getChildren().toArray(new javafx.scene.Node[0])));
        }

        boolean haveAccounts = !ctx.auth.accounts.isEmpty();

        // ── Microsoft login (primary, accent) ───────────────────────────
        Button msBtn = new Button(haveAccounts
                ? "🔑  Add another Microsoft account"
                : "🔑  Sign in with Microsoft");
        msBtn.getStyleClass().add("accent-button");
        msBtn.setMaxWidth(Double.MAX_VALUE);
        // When accounts already exist, force a fresh sign-in so a DIFFERENT account
        // can be chosen instead of silently reusing the current browser session.
        msBtn.setOnAction(e -> openEmbeddedBrowser(haveAccounts));

        Label msNote = FxUi.muted(haveAccounts
                ? "You can add as many accounts as you like and switch anytime. We'll sign you "
                  + "out of Microsoft in your browser first so you can pick a different account."
                : "Opens your browser with a real Microsoft login page — "
                  + "secure, no password stored in the launcher, no Azure setup needed.");

        // ── Play offline (secondary) ─────────────────────────────────────
        Button offlineBtn = new Button("▶  Play offline");
        offlineBtn.getStyleClass().add("ghost-button");
        offlineBtn.setMaxWidth(Double.MAX_VALUE);
        offlineBtn.setOnAction(e -> renderOffline());

        Label offlineNote = FxUi.muted(
                "Instant — no sign-in needed. Singleplayer and LAN only; "
                + "premium online servers require a real Microsoft account.");

        VBox choices = FxUi.card(
                FxUi.sectionTitle("Add account"),
                msBtn, msNote,
                new Separator(),
                offlineBtn, offlineNote);
        root.getChildren().add(choices);

        Button close = new Button("Close");
        close.getStyleClass().add("ghost-button");
        close.setOnAction(e -> stage.close());
        HBox footer = new HBox(FxUi.hgrow(), close);
        footer.setAlignment(Pos.CENTER_RIGHT);
        root.getChildren().add(footer);

        scroll.setContent(root);
    }

    /** One row in the account list: avatar initial, name, type, active badge / switch + remove. */
    private javafx.scene.Node accountRow(Account a, Account active) {
        boolean isActive = active != null && a.id.equals(active.id);
        boolean offline = "offline".equals(a.type);

        Label name = new Label(a.username);
        name.getStyleClass().add("row-label");
        Label type = FxUi.muted((offline ? "Offline" : "Microsoft") + " account");
        VBox text = new VBox(1, name, type);

        Label badge = new Label(isActive ? "● Active" : "Switch");
        badge.getStyleClass().add(isActive ? "badge" : "muted");

        Button remove = new Button("✕");
        remove.getStyleClass().add("icon-button");
        remove.setOnAction(e -> {
            ctx.auth.remove(a);
            onChanged.run();
            renderIdle();
        });

        HBox row = new HBox(12, text, FxUi.hgrow(), badge, remove);
        row.setAlignment(Pos.CENTER_LEFT);
        row.setPadding(new Insets(8, 6, 8, 6));
        row.setStyle(isActive
                ? "-fx-background-color: rgba(124,58,237,0.18); -fx-background-radius: 10;"
                : "-fx-background-color: transparent; -fx-cursor: hand;");
        // Clicking the row (not the remove button) switches the active account.
        row.setOnMouseClicked(e -> {
            if (e.getTarget() == remove || (e.getTarget() instanceof javafx.scene.Node n && inButton(n))) return;
            if (!isActive) {
                ctx.auth.setActive(a);
                onChanged.run();
                renderIdle();
            }
        });
        return row;
    }

    private static boolean inButton(javafx.scene.Node n) {
        for (javafx.scene.Node c = n; c != null; c = c.getParent())
            if (c instanceof javafx.scene.control.ButtonBase) return true;
        return false;
    }

    // ── Offline account ───────────────────────────────────────────────────

    private void renderOffline() {
        TextField usernameField = new TextField();
        usernameField.setPromptText("Username  (3 – 16 characters: A–Z, a–z, 0–9, _)");
        Label errorLabel = FxUi.muted("");
        errorLabel.setStyle("-fx-text-fill: #F87171;");

        Button addBtn = new Button("Add account");
        addBtn.getStyleClass().add("accent-button");
        addBtn.setDisable(true);
        usernameField.textProperty().addListener((o, was, now) -> {
            boolean ok = now.matches("[A-Za-z0-9_]{3,16}");
            addBtn.setDisable(!ok);
            errorLabel.setText(ok || now.isEmpty()
                    ? "" : "Only letters, numbers and _ allowed — 3 to 16 characters.");
        });
        addBtn.setOnAction(e -> {
            String name = usernameField.getText().trim();
            Account acc = new Account();
            acc.username        = name;
            acc.type            = "offline";
            acc.mcUuid          = offlineUuid(name);
            acc.mcAccessToken   = "0";
            acc.expiresAtEpochMs = Long.MAX_VALUE;
            ctx.auth.addOrReplace(acc);
            onChanged.run();
            renderIdle();
        });

        Button back = new Button("← Back");
        back.getStyleClass().add("ghost-button");
        back.setOnAction(e -> renderIdle());

        VBox card = FxUi.card(
                FxUi.sectionTitle("Offline account"),
                FxUi.muted("Pick any username. No login required — works instantly."),
                usernameField, errorLabel,
                new HBox(FxUi.hgrow(), addBtn));

        buildRoot(FxUi.h1("Offline account"), card, new HBox(back));
    }

    // ── Microsoft embedded browser login ─────────────────────────────────

    /** Starts the Microsoft device-code sign-in; {@code freshLogin} forces a different account. */
    private void openEmbeddedBrowser(boolean freshLogin) {
        Window owner = stage.getOwner();
        new MicrosoftLoginBrowser(ctx).show(owner, freshLogin,
                account -> {
                    // Success — account is ready.
                    ctx.auth.addOrReplace(account);
                    onChanged.run();
                    renderIdle();
                },
                errorMsg -> {
                    // Failure — show a themed error over the accounts screen.
                    renderIdle();
                    LuminaDialog.error(owner, "Microsoft sign-in failed", errorMsg);
                });
    }

    // ── helpers ──────────────────────────────────────────────────────────

    private void buildRoot(javafx.scene.Node... children) {
        VBox root = new VBox(16, children);
        root.getStyleClass().add("app-root");
        root.setPadding(new Insets(28));
        scroll.setContent(root);
    }

    private static String offlineUuid(String name) {
        return UUID.nameUUIDFromBytes(("OfflinePlayer:" + name).getBytes(StandardCharsets.UTF_8))
                .toString().replace("-", "");
    }

}
