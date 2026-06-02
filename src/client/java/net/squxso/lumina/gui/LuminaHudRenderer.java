package net.squxso.lumina.gui;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.squxso.lumina.feature.FeatureManager;

/**
 * Fabric HUD adapter — forwards the engine's HUD-render event to the
 * loader-agnostic {@link FeatureManager}. The Forge / NeoForge builds provide
 * their own equivalent adapter; the feature code stays shared.
 */
public final class LuminaHudRenderer implements HudRenderCallback {
    @Override
    public void onHudRender(DrawContext ctx, RenderTickCounter ticker) {
        FeatureManager.renderHud(ctx, MinecraftClient.getInstance());
    }
}
