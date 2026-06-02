package net.squxso.lumina.mixin;

import net.minecraft.client.render.GameRenderer;
import org.spongepowered.asm.mixin.Mixin;

/**
 * Placeholder — zoom is now handled entirely by setting
 * {@code options.getFov()} in {@code LuminaTweaks.applyZoom()}, which both
 * vanilla and Sodium renderers read. No mixin injection is needed.
 *
 * <p>The class is kept registered so the JSON entry is still valid and can
 * be extended later without a build-file change.
 */
@Mixin(GameRenderer.class)
public class LuminaFovMixin {
    // intentionally empty
}
