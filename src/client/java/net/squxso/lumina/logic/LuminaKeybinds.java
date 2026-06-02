package net.squxso.lumina.logic;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.input.KeyInput;
import net.minecraft.client.option.KeyBinding;
import net.minecraft.client.util.InputUtil;
import net.minecraft.text.Text;

/**
 * Thin facade over the single, real Fabric {@link KeyBinding} that toggles the
 * auto-fisher.
 *
 * <p>There is exactly one binding for the toggle, registered in
 * {@code LuminaClient}. It shows up in vanilla's Controls screen <em>and</em> can
 * be rebound from inside the Lumina panel via the {@code [?]} button — both edit
 * the same {@link KeyBinding}, so they never disagree. Rebinding writes the
 * options file so the choice survives a restart.
 */
public final class LuminaKeybinds {

    private LuminaKeybinds() {}

    /** The auto-fisher toggle binding. Assigned once by {@code LuminaClient}. */
    public static KeyBinding toggleFisher;

    /** True while the panel is waiting for the next keypress to rebind. */
    public static boolean listeningForKey = false;

    /** Human-readable label for the bound key, or {@code --} when unbound. */
    public static Text boundLabel() {
        if (toggleFisher == null || toggleFisher.isUnbound()) return Text.literal("--");
        return toggleFisher.getBoundKeyLocalizedText();
    }

    /** Bind the toggle to the given key and persist it. */
    public static void bind(KeyInput input) {
        if (toggleFisher == null) return;
        toggleFisher.setBoundKey(InputUtil.fromKeyCode(input));
        commit();
    }

    /** Clear the toggle binding and persist it. */
    public static void clear() {
        if (toggleFisher == null) return;
        toggleFisher.setBoundKey(InputUtil.UNKNOWN_KEY);
        commit();
    }

    private static void commit() {
        KeyBinding.updateKeysByCode();
        MinecraftClient.getInstance().options.write();
    }
}
