package com.luminamc.ui.components;

import com.luminamc.game.CapeArt;
import com.luminamc.shop.Cosmetic;
import javafx.embed.swing.SwingFXUtils;
import javafx.scene.image.Image;

/**
 * Provides the 3D model's cape texture: the cape's own {@link CapeArt} design
 * (fabric base + unique emblem) — the exact same artwork the shop cards and the
 * in-game texture use, so the preview is always truthful.
 */
public final class CapeTexture {

    private CapeTexture() {}

    private static final int W = 160, H = 256;

    /** The full back texture for {@code cape}, ready for the 3D cape mesh. */
    public static Image build(Cosmetic cape) {
        return SwingFXUtils.toFXImage(CapeArt.render(cape, W, H), null);
    }
}
