package com.luminamc.ui.panels;

import com.luminamc.crash.CrashReporter;
import com.luminamc.instance.Instance;
import com.luminamc.servers.ServerFavorite;
import com.luminamc.servers.ServerPinger;
import com.luminamc.ui.AppContext;
import com.luminamc.ui.FxUi;
import com.luminamc.ui.LaunchService;
import com.luminamc.ui.LuminaDialog;
import com.luminamc.ui.Theme;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.Button;
import javafx.scene.control.ComboBox;
import javafx.scene.control.ContextMenu;
import javafx.scene.control.Label;
import javafx.scene.control.MenuItem;
import javafx.scene.control.ProgressBar;
import javafx.scene.control.ScrollPane;
import javafx.scene.control.SeparatorMenuItem;
import javafx.scene.control.TextField;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.input.KeyCode;
import javafx.scene.layout.BorderPane;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.util.Comparator;
import java.util.List;

/**
 * Server favorites with Quick-Connect: save your servers once, see their live
 * status (MOTD, players, ping) and join them with one click — the launcher starts
 * the right instance and connects straight to the server via Quick Play.
 */
public final class ServersPanel extends BorderPane {

    private final AppContext ctx;
    private final VBox list = new VBox(12);

    // Bottom status bar (join progress).
    private final Label progressLabel = new Label("Ready");
    private final ProgressBar progress = new ProgressBar(0);

    public ServersPanel(AppContext ctx) {
        this.ctx = ctx;
        getStyleClass().add("grid-root");

        Label title = new Label("Servers");
        title.getStyleClass().add("page-title");

        Button refresh = new Button("⟳  Refresh");
        refresh.getStyleClass().add("ghost-button");
        refresh.setOnAction(e -> {
            if (pingActions.isEmpty()) rebuild();
            else List.copyOf(pingActions).forEach(Runnable::run);   // re-ping in place, no flicker
        });

        Button add = new Button("✛   Add Server");
        add.getStyleClass().add("create-button");
        add.setOnAction(e -> editDialog(null));

        HBox header = new HBox(12, title, FxUi.hgrow(), refresh, add);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(28, 32, 18, 32));
        setTop(header);

        list.setPadding(new Insets(4, 32, 24, 32));
        ScrollPane sp = new ScrollPane(list);
        sp.setFitToWidth(true);
        sp.getStyleClass().add("cosmic-scroll");
        setCenter(sp);

        progressLabel.getStyleClass().add("status-progress-label");
        progress.getStyleClass().add("global-progress");
        progress.setPrefWidth(180);
        HBox bar = new HBox(14, FxUi.muted("Join launches the server's instance and connects automatically."),
                FxUi.hgrow(), progressLabel, progress);
        bar.setAlignment(Pos.CENTER_LEFT);
        bar.setPadding(new Insets(14, 32, 14, 32));
        bar.getStyleClass().add("status-bar");
        setBottom(bar);

        rebuild();
    }

    // ── list ─────────────────────────────────────────────────────────────

    /** One status-recheck per row; re-run by Refresh and the 30-second auto-refresh. */
    private final List<Runnable> pingActions = new java.util.ArrayList<>();
    private javafx.animation.Timeline autoRefresh;

    private void startAutoRefresh() {
        if (autoRefresh != null) return;
        autoRefresh = new javafx.animation.Timeline(new javafx.animation.KeyFrame(
                javafx.util.Duration.seconds(30),
                e -> List.copyOf(pingActions).forEach(Runnable::run)));
        autoRefresh.setCycleCount(javafx.animation.Animation.INDEFINITE);
        autoRefresh.play();
        sceneProperty().addListener((o, was, is) -> { if (is == null) autoRefresh.stop(); });
    }

    private void rebuild() {
        list.getChildren().clear();
        pingActions.clear();
        startAutoRefresh();
        List<ServerFavorite> favs = ctx.config.serverFavorites;
        if (favs.isEmpty()) {
            Label l1 = new Label("No servers yet");
            l1.getStyleClass().add("row-label");
            Label l2 = FxUi.muted("Click “Add Server” and save your favorite servers — then join them with one click.");
            VBox hint = new VBox(6, l1, l2);
            hint.setPadding(new Insets(40));
            list.getChildren().add(hint);
            return;
        }
        favs.stream()
                .sorted(Comparator.comparingLong((ServerFavorite f) -> f.lastJoined).reversed())
                .forEach(f -> list.getChildren().add(row(f)));
    }

    private HBox row(ServerFavorite fav) {
        // Status dot: gray while pinging → green/red.
        Circle dot = new Circle(5, Color.web("#6E6486"));

        Label name = new Label(fav.name == null || fav.name.isBlank() ? fav.address : fav.name);
        name.setStyle("-fx-text-fill: white; -fx-font-weight: bold; -fx-font-size: 14px;");
        Label addr = FxUi.muted(fav.address);
        Label motd = FxUi.muted("Pinging…");
        motd.setStyle("-fx-text-fill: #8B81A6; -fx-font-size: 11px;");
        VBox titles = new VBox(2, name, addr, motd);
        titles.setAlignment(Pos.CENTER_LEFT);

        Label players = new Label("");
        players.setStyle("-fx-text-fill: #C4B5FD; -fx-font-weight: bold; -fx-font-size: 12px;");
        Label ping = FxUi.muted("");
        VBox stats = new VBox(2, players, ping);
        stats.setAlignment(Pos.CENTER_RIGHT);
        stats.setMinWidth(90);

        Button join = new Button("▶  JOIN");
        join.getStyleClass().add("play-button");
        join.setOnAction(e -> join(fav, join));

        Button menu = new Button("⋮");
        menu.getStyleClass().add("card-icon-btn");
        menu.setOnAction(e -> rowMenu(menu, fav));

        HBox row = new HBox(14, dot, titles, FxUi.hgrow(), stats, join, menu);
        row.setAlignment(Pos.CENTER_LEFT);
        row.getStyleClass().add("instance-card");
        row.setPadding(new Insets(14, 18, 14, 18));

        // One re-runnable status check for this row (guarded against overlapping runs),
        // registered for the Refresh button + the 30-second auto-refresh.
        java.util.concurrent.atomic.AtomicBoolean busy = new java.util.concurrent.atomic.AtomicBoolean();
        Runnable check = () -> {
            if (busy.getAndSet(true)) return;
            pingAsync(fav, dot, motd, players, ping, () -> busy.set(false));
        };
        pingActions.add(check);
        check.run();
        return row;
    }

    private void pingAsync(ServerFavorite fav, Circle dot, Label motd, Label players, Label ping,
                           Runnable onDone) {
        // Pulse the dot while the check runs, so "loading" is clearly visible.
        dot.setFill(Color.web("#6E6486"));
        javafx.animation.FadeTransition pulse =
                new javafx.animation.FadeTransition(javafx.util.Duration.millis(550), dot);
        pulse.setFromValue(1.0);
        pulse.setToValue(0.3);
        pulse.setAutoReverse(true);
        pulse.setCycleCount(javafx.animation.Animation.INDEFINITE);
        pulse.play();

        new Thread(() -> {
            ServerPinger.Status s = ServerPinger.ping(fav.address);
            Platform.runLater(() -> {
                pulse.stop();
                dot.setOpacity(1);
                if (s.online()) {
                    dot.setFill(Color.web("#4ADE80"));
                    motd.setText(s.motd().isBlank() ? "Online" : s.motd());
                    players.setText(s.playersOnline() + " / " + s.playersMax() + " online");
                    ping.setText(s.latencyMs() + " ms" + (s.version().isBlank() ? "" : "  ·  " + s.version()));
                } else {
                    dot.setFill(Color.web("#F87171"));
                    motd.setText("Offline / unreachable  ·  rechecking automatically");
                    players.setText("—");
                    ping.setText("");
                }
                onDone.run();
            });
        }, "luminamc-ping").start();
    }

    private void rowMenu(Button anchor, ServerFavorite fav) {
        ContextMenu m = new ContextMenu();
        MenuItem edit = new MenuItem("Edit");
        edit.setOnAction(e -> editDialog(fav));
        MenuItem copy = new MenuItem("Copy address");
        copy.setOnAction(e -> {
            ClipboardContent cc = new ClipboardContent();
            cc.putString(fav.address);
            Clipboard.getSystemClipboard().setContent(cc);
        });
        MenuItem del = new MenuItem("Delete");
        del.setStyle("-fx-text-fill: #F87171;");
        del.setOnAction(e -> {
            if (LuminaDialog.confirmDanger(win(), "Delete server",
                    "Remove \"" + (fav.name == null || fav.name.isBlank() ? fav.address : fav.name)
                            + "\" from your servers?", "Delete")) {
                ctx.config.serverFavorites.removeIf(f -> f.id.equals(fav.id));
                ctx.config.save();
                rebuild();
            }
        });
        m.getItems().addAll(edit, copy, new SeparatorMenuItem(), del);
        m.show(anchor, javafx.geometry.Side.BOTTOM, 0, 0);
    }

    // ── join ─────────────────────────────────────────────────────────────

    private void join(ServerFavorite fav, Button join) {
        Instance inst = resolveInstance(fav);
        if (inst == null) {
            LuminaDialog.error(win(), "No instance",
                    "Create an instance first — the server is joined through one of your instances.");
            return;
        }
        fav.lastJoined = System.currentTimeMillis();
        ctx.config.save();

        join.setDisable(true);
        join.setText("LAUNCHING…");
        progressLabel.setText("Joining " + fav.address + " with " + inst.name + "…");
        progress.setProgress(ProgressBar.INDETERMINATE_PROGRESS);

        new LaunchService(ctx).launchAsync(inst, fav.address, new LaunchService.Callbacks() {
            @Override public void phase(String text) { progressLabel.setText(text); }
            @Override public void progress(double f, String detail) {
                progress.setProgress(f < 0 ? ProgressBar.INDETERMINATE_PROGRESS : f);
                progressLabel.setText(detail);
            }
            @Override public void log(String line) { }
            @Override public void started(Process p) {
                join.setText("IN GAME");
                progressLabel.setText("Connected to " + fav.address);
                progress.setProgress(1);
            }
            @Override public void finished(CrashReporter.Report report) {
                join.setDisable(false);
                join.setText("▶  JOIN");
                progress.setProgress(0);
                progressLabel.setText(report.crashed()
                        ? inst.name + " crashed (exit " + report.exitCode() + ")" : "Session ended");
            }
            @Override public void failed(Throwable error) {
                join.setDisable(false);
                join.setText("▶  JOIN");
                progress.setProgress(0);
                progressLabel.setText("Launch failed");
                String msg = error.getMessage() != null ? error.getMessage() : error.toString();
                LuminaDialog.error(win(), "Couldn't join \"" + fav.address + "\"", msg);
            }
        });
    }

    /** The instance assigned to this favorite, else the most recently played one. */
    private Instance resolveInstance(ServerFavorite fav) {
        var all = ctx.instances.all();
        if (all.isEmpty()) return null;
        if (fav.instanceId != null) {
            for (Instance i : all) if (fav.instanceId.equals(i.id)) return i;
        }
        return all.stream().max(Comparator.comparingLong(i -> i.lastPlayed)).orElse(all.get(0));
    }

    // ── add / edit dialog ────────────────────────────────────────────────

    private void editDialog(ServerFavorite existing) {
        Stage stage = new Stage(StageStyle.TRANSPARENT);

        Label title = new Label(existing == null ? "Add Server" : "Edit Server");
        title.getStyleClass().add("dialog-title");

        TextField name = new TextField(existing != null ? existing.name : "");
        name.setPromptText("Display name (optional)");
        TextField address = new TextField(existing != null ? existing.address : "");
        address.setPromptText("play.example.net  or  host:25565");

        // Which instance joins this server.
        ComboBox<Object> instBox = new ComboBox<>();
        instBox.getItems().add("Most recently played");
        for (Instance i : ctx.instances.all()) instBox.getItems().add(i);
        instBox.setConverter(new javafx.util.StringConverter<>() {
            @Override public String toString(Object o) {
                return o instanceof Instance i ? i.name + "  (" + i.mcVersion + ")" : String.valueOf(o);
            }
            @Override public Object fromString(String s) { return s; }
        });
        instBox.getSelectionModel().select(0);
        if (existing != null && existing.instanceId != null) {
            for (Object o : instBox.getItems()) {
                if (o instanceof Instance i && i.id.equals(existing.instanceId)) {
                    instBox.getSelectionModel().select(o);
                }
            }
        }

        Label error = FxUi.muted("");
        error.setStyle("-fx-text-fill: #F87171;");

        Button cancel = new Button("Cancel");
        cancel.getStyleClass().add("ghost-button");
        cancel.setOnAction(e -> stage.close());
        Button save = new Button(existing == null ? "Add" : "Save");
        save.getStyleClass().add("accent-button");
        save.setDefaultButton(true);
        save.setOnAction(e -> {
            String adr = address.getText() == null ? "" : address.getText().trim();
            if (adr.isBlank()) { error.setText("Please enter a server address."); return; }
            String instId = instBox.getValue() instanceof Instance i ? i.id : null;
            if (existing == null) {
                ctx.config.serverFavorites.add(ServerFavorite.create(name.getText().trim(), adr, instId));
            } else {
                existing.name = name.getText().trim();
                existing.address = adr;
                existing.instanceId = instId;
            }
            ctx.config.save();
            stage.close();
            rebuild();
        });
        HBox buttons = new HBox(10, FxUi.hgrow(), cancel, save);
        buttons.setAlignment(Pos.CENTER_RIGHT);

        VBox panel = new VBox(12, title,
                labeled("Name", name),
                labeled("Address", address),
                labeled("Join with", instBox),
                error, buttons);
        panel.getStyleClass().add("lumina-dialog");
        panel.setPadding(new Insets(22));
        panel.setPrefWidth(380);

        Scene scene = new Scene(panel, Color.TRANSPARENT);
        Theme.apply(scene);
        Theme.applyAccent(scene);
        scene.setOnKeyPressed(e -> { if (e.getCode() == KeyCode.ESCAPE) stage.close(); });

        Window owner = win();
        stage.setScene(scene);
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setOnShown(e -> {
            if (owner != null) {
                stage.setX(owner.getX() + (owner.getWidth() - stage.getWidth()) / 2);
                stage.setY(owner.getY() + (owner.getHeight() - stage.getHeight()) / 2);
            }
        });
        stage.showAndWait();
    }

    private VBox labeled(String text, javafx.scene.Node field) {
        Label l = new Label(text);
        l.getStyleClass().add("field-label");
        if (field instanceof javafx.scene.layout.Region r) r.setMaxWidth(Double.MAX_VALUE);
        return new VBox(4, l, field);
    }

    private Window win() {
        return getScene() != null ? getScene().getWindow() : null;
    }
}
