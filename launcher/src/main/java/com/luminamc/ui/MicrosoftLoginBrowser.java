package com.luminamc.ui;

import com.luminamc.auth.Account;
import com.luminamc.auth.MicrosoftAuth;
import javafx.application.Platform;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Scene;
import javafx.scene.control.*;
import javafx.scene.input.Clipboard;
import javafx.scene.input.ClipboardContent;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;
import javafx.scene.text.Font;
import javafx.scene.text.FontWeight;
import javafx.stage.Modality;
import javafx.stage.Stage;
import javafx.stage.StageStyle;
import javafx.stage.Window;

import java.util.function.Consumer;

/**
 * Microsoft sign-in dialog using the device-code flow.
 *
 * <p>No Azure setup needed — {@link MicrosoftAuth} uses Microsoft's own
 * pre-approved launcher client id. The dialog shows a short code, opens
 * {@code microsoft.com/link}, the user enters the code, and the dialog closes
 * automatically once authentication completes.
 */
public final class MicrosoftLoginBrowser {

    private final AppContext ctx;
    private Thread loginThread;

    public MicrosoftLoginBrowser(AppContext ctx) {
        this.ctx = ctx;
    }

    public void show(Window owner, Consumer<Account> onSuccess, Consumer<String> onError) {
        Stage stage = buildStage(owner);

        ProgressIndicator spinner = new ProgressIndicator(-1);
        spinner.setPrefSize(22, 22);
        Label statusLabel = new Label("Requesting a code from Microsoft…");
        statusLabel.getStyleClass().add("row-label");
        Label detailLabel = FxUi.muted("Please wait a moment.");

        Label codeLabel = new Label("");
        codeLabel.setFont(Font.font("Consolas", FontWeight.BOLD, 40));
        codeLabel.setStyle("-fx-text-fill: #E9D5FF; -fx-letter-spacing: 8;");

        Button copyCodeBtn = new Button("📋  Copy code");
        copyCodeBtn.getStyleClass().add("accent-button");
        copyCodeBtn.setVisible(false);
        Button openLinkBtn = new Button("🌐  Open login page again");
        openLinkBtn.getStyleClass().add("ghost-button");
        openLinkBtn.setVisible(false);
        Label timerLabel = FxUi.muted("");

        Runnable cancel = () -> { if (loginThread != null) loginThread.interrupt(); stage.close(); };

        Button cancelBtn = new Button("Cancel");
        cancelBtn.getStyleClass().add("danger-button");
        cancelBtn.setOnAction(e -> cancel.run());

        VBox codeCard = FxUi.card(
                FxUi.sectionTitle("1.  A login page is opening in your browser"),
                FxUi.muted("If it didn't open, click the button below."),
                FxUi.sectionTitle("2.  Enter this code there:"),
                codeLabel,
                new HBox(10, copyCodeBtn, openLinkBtn),
                timerLabel,
                new Separator(),
                FxUi.muted("Sign in with your Microsoft account that owns Minecraft. "
                        + "This window closes by itself once you're done."));

        VBox root = new VBox(14,
                new HBox(10, spinner, statusLabel),
                detailLabel,
                codeCard,
                new HBox(FxUi.hgrow(), cancelBtn));
        root.setPadding(new Insets(4, 22, 18, 22));

        // Frameless Lumina header (title + close) — no white OS title bar.
        Label titleLabel = new Label("✦  Sign in with Microsoft");
        titleLabel.getStyleClass().add("dialog-title");
        Button x = new Button("✕");
        x.getStyleClass().add("icon-button");
        x.setOnAction(e -> cancel.run());
        HBox header = new HBox(10, titleLabel, FxUi.hgrow(), x);
        header.setAlignment(Pos.CENTER_LEFT);
        header.setPadding(new Insets(14, 12, 6, 20));

        VBox outer = new VBox(0, header, root);
        outer.getStyleClass().add("lumina-dialog");

        final double[] drag = new double[2];
        header.setOnMousePressed(e -> { drag[0] = e.getScreenX() - stage.getX(); drag[1] = e.getScreenY() - stage.getY(); });
        header.setOnMouseDragged(e -> { stage.setX(e.getScreenX() - drag[0]); stage.setY(e.getScreenY() - drag[1]); });

        Scene scene = stage.getScene();
        scene.setRoot(outer);
        Theme.applyAccent(scene);
        stage.sizeToScene();
        stage.setOnShown(e -> {
            Window own = stage.getOwner();
            if (own != null) {
                stage.setX(own.getX() + (own.getWidth() - outer.getWidth()) / 2);
                stage.setY(own.getY() + (own.getHeight() - outer.getHeight()) / 2);
            }
        });
        stage.show();

        loginThread = new Thread(() -> {
            try {
                Account account = new MicrosoftAuth().loginViaDeviceCode(
                        (userCode, verifyUri, expSec) -> Platform.runLater(() -> {
                            spinner.setVisible(false);
                            statusLabel.setText("Waiting for you to sign in…");
                            detailLabel.setText("");
                            codeLabel.setText(userCode);
                            copyCodeBtn.setVisible(true);
                            openLinkBtn.setVisible(true);
                            timerLabel.setText("This code expires in " + (expSec / 60) + " minutes.");

                            copyCodeBtn.setOnAction(ev -> {
                                ClipboardContent cc = new ClipboardContent();
                                cc.putString(userCode);
                                Clipboard.getSystemClipboard().setContent(cc);
                                copyCodeBtn.setText("✓  Copied!");
                            });
                            openLinkBtn.setOnAction(ev -> MicrosoftAuth.openBrowser(verifyUri));
                            MicrosoftAuth.openBrowser(verifyUri);   // auto-open once
                        }));

                Platform.runLater(() -> { stage.close(); onSuccess.accept(account); });
            } catch (InterruptedException ignored) {
                // cancelled
            } catch (Exception ex) {
                Platform.runLater(() -> {
                    stage.close();
                    onError.accept(ex.getMessage() != null ? ex.getMessage() : ex.toString());
                });
            }
        }, "luminamc-devcode");
        loginThread.setDaemon(true);
        loginThread.start();
    }

    private static Stage buildStage(Window owner) {
        Stage stage = new Stage(StageStyle.TRANSPARENT);
        Scene scene = new Scene(new VBox(), 480, 500);
        scene.setFill(Color.TRANSPARENT);
        Theme.apply(scene);
        stage.setScene(scene);
        stage.setTitle("Microsoft sign-in — LuminaMC");
        stage.initOwner(owner);
        stage.initModality(Modality.WINDOW_MODAL);
        stage.setResizable(false);
        return stage;
    }
}
