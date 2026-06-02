package net.squxso.lumina;

import com.mojang.blaze3d.platform.InputConstants;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.neoforged.bus.api.IEventBus;
import net.neoforged.neoforge.common.NeoForge;
import net.neoforged.neoforge.client.event.ClientTickEvent;
import net.neoforged.neoforge.client.event.RenderGuiEvent;
import net.neoforged.neoforge.client.event.RegisterKeyMappingsEvent;
import net.neoforged.fml.event.lifecycle.FMLClientSetupEvent;
import net.squxso.lumina.client.LuminaClientCore;
import org.lwjgl.glfw.GLFW;

/**
 * NeoForge client adapter: same four hooks as the Fabric adapter (keybind, tick,
 * HUD render, lifecycle), wired programmatically on the mod + game event buses.
 * HUD is drawn via {@link RenderGuiEvent.Post} (no GUI-layer id needed).
 */
public final class LuminaNeoForgeClient {

    private static KeyMapping openKey;

    private LuminaNeoForgeClient() {}

    public static void init(IEventBus modBus) {
        modBus.addListener(LuminaNeoForgeClient::onRegisterKeys);
        modBus.addListener(LuminaNeoForgeClient::onClientSetup);
        NeoForge.EVENT_BUS.addListener(LuminaNeoForgeClient::onClientTick);
        NeoForge.EVENT_BUS.addListener(LuminaNeoForgeClient::onRenderGui);
    }

    private static void onRegisterKeys(RegisterKeyMappingsEvent e) {
        openKey = new KeyMapping("key.lumina.open_gui",
                InputConstants.Type.KEYSYM, GLFW.GLFW_KEY_RIGHT_SHIFT, KeyMapping.Category.MISC);
        e.register(openKey);
    }

    private static void onClientSetup(FMLClientSetupEvent e) {
        e.enqueueWork(() -> LuminaClientCore.onClientStarted(Minecraft.getInstance()));
    }

    private static void onClientTick(ClientTickEvent.Post e) {
        Minecraft mc = Minecraft.getInstance();
        if (openKey != null) while (openKey.consumeClick()) LuminaClientCore.openPanel(mc);
        LuminaClientCore.onClientTick(mc);
    }

    private static void onRenderGui(RenderGuiEvent.Post e) {
        LuminaClientCore.renderHud(e.getGuiGraphics(), Minecraft.getInstance());
    }
}
