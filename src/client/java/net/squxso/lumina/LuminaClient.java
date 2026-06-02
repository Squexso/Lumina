package net.squxso.lumina;

import net.fabricmc.api.ClientModInitializer;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientLifecycleEvents;
import net.fabricmc.fabric.api.client.event.lifecycle.v1.ClientTickEvents;
import net.fabricmc.fabric.api.client.keybinding.v1.KeyBindingHelper;
import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.util.Identifier;
import net.squxso.lumina.feature.FeatureManager;
import net.squxso.lumina.gui.LuminaHudRenderer;
import net.squxso.lumina.gui.LuminaPanel;
import org.lwjgl.glfw.GLFW;

/**
 * Fabric client entry point for the LuminaMC client mod.
 *
 * <p>Thin platform adapter: registers the Right-Shift keybind, the HUD render
 * hook and the lifecycle load/save, all forwarding to the loader-agnostic
 * {@link FeatureManager}. A Forge / NeoForge entry point would mirror this with
 * the same three hooks — the feature code itself is shared.
 */
public final class LuminaClient implements ClientModInitializer {

    public static final KeyBinding.Category LUMINA_CATEGORY =
            KeyBinding.Category.create(Identifier.of("lumina", "lumina"));

    private static KeyBinding openGuiKey;
    private static KeyBinding zoomKey;

    @Override
    public void onInitializeClient() {
        FeatureManager.init();

        openGuiKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.lumina.open_gui",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_RIGHT_SHIFT,
                LUMINA_CATEGORY));

        zoomKey = KeyBindingHelper.registerKeyBinding(new KeyBinding(
                "key.lumina.zoom",
                InputUtil.Type.KEYSYM,
                GLFW.GLFW_KEY_C,
                LUMINA_CATEGORY));

        // Per-tick: open the panel on key press, drive features, update zoom state.
        ClientTickEvents.END_CLIENT_TICK.register(client -> {
            while (openGuiKey.wasPressed()) {
                if (client.currentScreen == null) client.setScreen(new LuminaPanel());
            }
            net.squxso.lumina.feature.impl.ZoomFeature.active =
                    net.squxso.lumina.feature.impl.ZoomFeature.featureEnabled()
                    && zoomKey.isPressed() && client.currentScreen == null;
            FeatureManager.onClientTick(client);
        });

        // HUD overlays (FPS, armour/effects, coordinates, crosshair…).
        HudRenderCallback.EVENT.register(new LuminaHudRenderer());

        // Load saved feature toggles on start; persist on stop.
        ClientLifecycleEvents.CLIENT_STARTED.register(c -> {
            c.getWindow().setTitle("LuminaMC");
            FeatureManager.load();
        });
        ClientLifecycleEvents.CLIENT_STOPPING.register(c -> FeatureManager.save());

        // The op-items creative tab (separate, vanilla registry feature).
        LuminaCreativeTab.register();
    }

    /** Opens the control panel programmatically. */
    public static void openGui() {
        MinecraftClient mc = MinecraftClient.getInstance();
        mc.execute(() -> mc.setScreen(new LuminaPanel()));
    }
}
