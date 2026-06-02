package net.squxso.lumina.gui;

import net.fabricmc.fabric.api.client.screen.v1.ScreenEvents;
import net.minecraft.client.gui.screen.TitleScreen;

/**
 * Replaces the vanilla title screen with {@link LuminaTitleScreen} via
 * {@code ScreenEvents.AFTER_INIT}.
 *
 * <p>The game-pause menu background is handled by {@code LuminaGameMenuBgMixin}
 * (no screen swap → no missing-buttons issue). Options and Sodium screens are
 * handled by {@code LuminaScreenBgMixin} / {@code LuminaSodiumMixin}.
 */
public final class LuminaTitleBranding {

    private LuminaTitleBranding() {}

    public static void register() {
        ScreenEvents.AFTER_INIT.register((client, screen, width, height) -> {
            if (screen instanceof TitleScreen && !(screen instanceof LuminaTitleScreen)) {
                client.setScreen(new LuminaTitleScreen());
            }
        });
    }
}
