package net.squxso.lumina.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.squxso.lumina.feature.FeatureManager;

/**
 * Loader-agnostic client logic. Each loader's client adapter (Fabric / Forge /
 * NeoForge) forwards its engine events here, so the behaviour is identical
 * everywhere and lives in one place.
 */
public final class LuminaClientCore {

    private LuminaClientCore() {}

    /** Called once when the client has finished starting. */
    public static void onClientStarted(Minecraft mc) {
        if (mc.getWindow() != null) mc.getWindow().setTitle("LuminaMC");
        FeatureManager.load();
    }

    /** Called when the client is shutting down. */
    public static void onClientStopping() {
        FeatureManager.save();
    }

    /** Forward each client tick. */
    public static void onClientTick(Minecraft mc) {
        FeatureManager.onClientTick(mc);
    }

    /** Forward each HUD-render frame. */
    public static void renderHud(GuiGraphics ctx, Minecraft mc) {
        FeatureManager.renderHud(ctx, mc);
    }

    /** Open the Right-Shift control panel (no-op if another screen is open). */
    public static void openPanel(Minecraft mc) {
        if (mc.screen == null) mc.setScreen(new LuminaScreen());
    }
}
