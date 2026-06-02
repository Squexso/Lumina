package net.squxso.lumina;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.minecraft.resources.Identifier;
import net.minecraftforge.client.event.AddGuiOverlayLayersEvent;
import net.minecraftforge.client.event.RegisterKeyMappingsEvent;
import net.minecraftforge.client.gui.overlay.ForgeLayer;
import net.minecraftforge.event.TickEvent;
import net.squxso.lumina.client.LuminaClientCore;
import org.lwjgl.glfw.GLFW;

/**
 * Forge client adapter (Forge 1.21.11 / eventbus 7). The rewritten event bus
 * exposes a static {@code BUS} per event, so listeners are added directly; the HUD
 * is a {@link ForgeLayer} added via {@link AddGuiOverlayLayersEvent}. Mirrors the
 * Fabric / NeoForge adapters via the shared {@link LuminaClientCore}.
 */
public final class LuminaForgeClient {

    private static KeyMapping openKey;
    private static boolean loaded = false;

    private LuminaForgeClient() {}

    public static void init() {
        RegisterKeyMappingsEvent.BUS.addListener(LuminaForgeClient::onRegisterKeys);
        AddGuiOverlayLayersEvent.BUS.addListener(LuminaForgeClient::onAddGuiLayers);
        TickEvent.ClientTickEvent.Post.BUS.addListener(LuminaForgeClient::onClientTick);
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent e) {
        openKey = new KeyMapping("key.lumina.open_gui",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, KeyMapping.Category.MISC);
        e.register(openKey);
    }

    private static void onAddGuiLayers(AddGuiOverlayLayersEvent e) {
        ForgeLayer layer = (guiGraphics, deltaTracker) ->
                LuminaClientCore.renderHud(guiGraphics, Minecraft.getInstance());
        e.getLayeredDraw().add(Identifier.fromNamespaceAndPath("lumina", "hud"), layer);
    }

    private static void onClientTick(TickEvent.ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (!loaded) { loaded = true; LuminaClientCore.onClientStarted(mc); }
        if (openKey != null) while (openKey.consumeClick()) LuminaClientCore.openPanel(mc);
        LuminaClientCore.onClientTick(mc);
    }
}
