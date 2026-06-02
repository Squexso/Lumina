package net.squxso.lumina;

import com.mojang.blaze3d.platform.InputConstants;
import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.KeyMapping;
import net.minecraft.client.Minecraft;
import net.squxso.lumina.client.LuminaClientCore;
import org.lwjgl.glfw.GLFW;

/**
 * Fabric client adapter: registers the Right-Shift keybind, the per-tick driver,
 * the HUD render hook and the start/stop lifecycle — all forwarding to the shared
 * {@link LuminaClientCore}. The Forge / NeoForge adapters mirror these hooks.
 */
public final class LuminaFabricClient implements ClientModInitializer {

    @Override
    public void onInitializeClient() {
        KeyMapping openKey = KeyBindingHelper.registerKeyBinding(new KeyMapping(
                "key.lumina.open_gui",
                InputConstants.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                KeyMapping.Category.MISC));

        ClientTickEvents.END_CLIENT_TICK.register(mc -> {
            while (openKey.consumeClick()) LuminaClientCore.openPanel(mc);
            LuminaClientCore.onClientTick(mc);
        });

        HudRenderCallback.EVENT.register((ctx, tick) ->
                LuminaClientCore.renderHud(ctx, Minecraft.getInstance()));

        ClientLifecycleEvents.CLIENT_STARTED.register(LuminaClientCore::onClientStarted);
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> LuminaClientCore.onClientStopping());
    }
}
