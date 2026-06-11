package com.luminamc.ui.components;

import com.luminamc.skin.SkinService;
import javafx.application.Platform;
import javafx.scene.Node;
import javafx.scene.SubScene;
import javafx.scene.control.Label;
import javafx.scene.effect.GaussianBlur;
import javafx.scene.image.Image;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Circle;

import java.io.ByteArrayInputStream;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Builds an interactive 3D character preview (with an optional cape + accessory)
 * into a holder, caching downloaded skins so switching cosmetics is instant.
 */
public final class ModelPreview {

    private ModelPreview() {}

    private static final Map<String, Image> CACHE = new ConcurrentHashMap<>();

    /** Front ¾ view (shows the face) — the default for skin previews. */
    private static final double FRONT_YAW = 137, FRONT_PITCH = -4;
    /** Slight ¾-back view — the cape shows fully but the model keeps some depth,
     *  instead of the flat "pressed against glass" look of a dead-straight back shot. */
    public static final double BACK_YAW = 24, BACK_PITCH = -7;

    public static void into(StackPane holder, String skinUrl, boolean slim,
                            Image capeTex, String accType, Color accColor) {
        into(holder, skinUrl, slim, capeTex, accType, accColor, FRONT_YAW, FRONT_PITCH);
    }

    /**
     * Builds the preview facing a chosen yaw/pitch. Cosmetic screens pass the back angle
     * ({@link #BACK_YAW}/{@link #BACK_PITCH}) so the cape/wings show fully and centred —
     * from a ¾-front view a back-mounted cape only peeks around one side and looks cut off.
     */
    public static void into(StackPane holder, String skinUrl, boolean slim,
                            Image capeTex, String accType, Color accColor,
                            double yaw, double pitch) {
        if (skinUrl == null) { show(holder, msg("Sign in to preview your skin")); return; }

        Image cached = CACHE.get(skinUrl);
        if (valid(cached)) { show(holder, scene(holder, cached, slim, capeTex, accType, accColor, yaw, pitch)); return; }

        show(holder, msg("Loading model…"));
        new Thread(() -> {
            try {
                byte[] png = SkinService.download(skinUrl);
                Image skin = new Image(new ByteArrayInputStream(png));
                if (!valid(skin)) throw new IllegalStateException("bad skin");
                CACHE.put(skinUrl, skin);
                Platform.runLater(() -> show(holder, scene(holder, skin, slim, capeTex, accType, accColor, yaw, pitch)));
            } catch (Exception e) {
                Platform.runLater(() -> show(holder, msg("Couldn't load the model.")));
            }
        }, "luminamc-model").start();
    }

    private static boolean valid(Image img) {
        return img != null && !img.isError() && img.getPixelReader() != null;
    }

    private static SubScene scene(StackPane holder, Image skin, boolean slim, Image capeTex,
                                  String accType, Color accColor, double yaw, double pitch) {
        SkinView3D v = new SkinView3D(skin, slim, capeTex, accType, accColor);
        v.setView(yaw, pitch);
        // Size the 3D viewport to the holder, so compact previews render compact.
        double w = holder.getPrefWidth() > 0 ? holder.getPrefWidth() : 300;
        double h = holder.getPrefHeight() > 0 ? holder.getPrefHeight() : 420;
        return v.buildScene(w, h);
    }

    private static void show(StackPane holder, Node content) {
        double h = holder.getPrefHeight() > 0 ? holder.getPrefHeight() : 420;
        Circle glow = new Circle(h * 0.31, Color.web("#7C3AED", 0.16));
        glow.setEffect(new GaussianBlur(45));
        holder.getChildren().setAll(glow, content);
    }

    private static Label msg(String s) {
        Label l = new Label(s);
        l.setStyle("-fx-text-fill: #8C8C9C; -fx-font-size: 12px;");
        return l;
    }
}
