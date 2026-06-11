package com.luminamc.ui.components;

import com.luminamc.game.CapeArt;
import com.luminamc.shop.Cosmetic;
import com.luminamc.shop.ShopCatalog;
import javafx.embed.swing.SwingFXUtils;
import javafx.geometry.Insets;
import javafx.scene.effect.DropShadow;
import javafx.scene.image.ImageView;
import javafx.scene.layout.Border;
import javafx.scene.layout.BorderStroke;
import javafx.scene.layout.BorderStrokeStyle;
import javafx.scene.layout.BorderWidths;
import javafx.scene.layout.CornerRadii;
import javafx.scene.layout.StackPane;
import javafx.scene.paint.Color;
import javafx.scene.shape.Rectangle;

/**
 * A cape preview card image: shows the cape's real {@link CapeArt} design (the same
 * artwork used by the 3D preview and in-game), rounded with a soft tinted glow.
 */
public final class CapeView {

    private CapeView() {}

    /** Builds the default Lumina (violet) cape preview. */
    public static StackPane build(double height) {
        return build(height, ShopCatalog.cosmetic(ShopCatalog.LUMINA_CAPE_ID));
    }

    /** Builds the cape's design preview {@code height} px tall. */
    public static StackPane build(double height, Cosmetic cape) {
        double w = Math.round(height * 0.625);

        // Render at 2× for crisp downscaling on the card.
        ImageView iv = new ImageView(SwingFXUtils.toFXImage(
                CapeArt.render(cape, (int) (w * 2), (int) (height * 2)), null));
        iv.setFitWidth(w);
        iv.setFitHeight(height);
        iv.setSmooth(true);

        StackPane pane = new StackPane(iv);
        pane.setMinSize(w, height);
        pane.setMaxSize(w, height);

        Rectangle clip = new Rectangle(w, height);
        clip.setArcWidth(18);
        clip.setArcHeight(18);
        iv.setClip(clip);

        Color top = safe(cape.colorTop()), bottom = safe(cape.colorBottom());
        pane.setBorder(new Border(new BorderStroke(top.interpolate(Color.WHITE, 0.3),
                BorderStrokeStyle.SOLID, new CornerRadii(10), new BorderWidths(1.2))));

        DropShadow glow = new DropShadow(18, top.interpolate(bottom, 0.5));
        glow.setSpread(0.04);
        pane.setEffect(glow);
        pane.setPadding(Insets.EMPTY);
        return pane;
    }

    private static Color safe(String hex) {
        try { return Color.web(hex); } catch (Exception e) { return Color.web("#7C3AED"); }
    }
}
