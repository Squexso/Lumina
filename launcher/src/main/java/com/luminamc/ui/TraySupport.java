package com.luminamc.ui;

import javafx.application.Platform;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;
import javafx.stage.Stage;

import javax.swing.SwingUtilities;
import java.awt.*;
import java.awt.image.BufferedImage;

/**
 * Minimise-to-tray support. When the window is minimised it hides from the taskbar
 * and lives in the system tray; clicking the tray icon (or its "Open" menu entry)
 * restores it. A "Quit" entry exits fully. Best-effort: silently no-ops where the
 * system tray isn't available.
 */
public final class TraySupport {

    private TraySupport() {}

    private static TrayIcon trayIcon;

    public static void install(Stage stage) {
        if (!SystemTray.isSupported()) return;

        Image fx = Theme.logo();
        if (fx == null) {
            var imgs = com.luminamc.ui.components.AppIcon.images();
            if (!imgs.isEmpty()) fx = imgs.get(0);
        }
        if (fx == null) return;
        final BufferedImage awt = SwingFXUtils.fromFXImage(fx, null);

        // Keep the JavaFX runtime alive while only the tray icon remains.
        Platform.setImplicitExit(false);

        SwingUtilities.invokeLater(() -> {
            try {
                SystemTray tray = SystemTray.getSystemTray();

                PopupMenu menu = new PopupMenu();
                MenuItem open = new MenuItem("Open LuminaMC");
                open.addActionListener(e -> Platform.runLater(() -> restore(stage)));
                MenuItem quit = new MenuItem("Quit");
                quit.addActionListener(e -> shutdown(stage));
                menu.add(open);
                menu.addSeparator();
                menu.add(quit);

                trayIcon = new TrayIcon(awt, "LuminaMC", menu);
                trayIcon.setImageAutoSize(true);
                trayIcon.addActionListener(e -> Platform.runLater(() -> restore(stage))); // double-click
                tray.add(trayIcon);

                // Hide to tray when minimised.
                Platform.runLater(() -> stage.iconifiedProperty().addListener((o, was, minimised) -> {
                    if (minimised) {
                        stage.hide();
                        if (trayIcon != null) trayIcon.displayMessage("LuminaMC",
                                "Still running in the tray — click to reopen.", TrayIcon.MessageType.NONE);
                    }
                }));

                Runtime.getRuntime().addShutdownHook(new Thread(TraySupport::removeIcon, "luminamc-tray-cleanup"));
            } catch (Exception ignored) {
                // tray unavailable — app keeps working without it
            }
        });
    }

    private static void restore(Stage stage) {
        if (!stage.isShowing()) stage.show();
        stage.setIconified(false);
        stage.toFront();
        stage.requestFocus();
    }

    private static void shutdown(Stage stage) {
        removeIcon();
        Platform.runLater(() -> {
            stage.close();
            Platform.exit();
        });
        // Ensure the JVM exits even if no FX windows remain.
        new Thread(() -> { sleepQuietly(400); System.exit(0); }, "luminamc-tray-exit").start();
    }

    private static void removeIcon() {
        if (trayIcon != null && SystemTray.isSupported()) {
            try { SystemTray.getSystemTray().remove(trayIcon); } catch (Exception ignored) {}
            trayIcon = null;
        }
    }

    private static void sleepQuietly(long ms) {
        try { Thread.sleep(ms); } catch (InterruptedException ignored) {}
    }
}
