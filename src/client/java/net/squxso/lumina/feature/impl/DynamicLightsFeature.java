package net.squxso.lumina.feature.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.item.BlockItem;
import net.minecraft.item.ItemStack;
import net.minecraft.util.math.BlockPos;
import net.squxso.lumina.feature.Feature;
import net.squxso.lumina.feature.FeatureCategory;

/**
 * Dynamic lighting: makes held / nearby light-emitting items illuminate the world.
 *
 * <p>This class holds the loader-agnostic core: each tick it works out the light
 * level the player's held item <em>should</em> emit and where. The actual
 * injection into the light engine is the per-loader binding step (a mixin into
 * the lighting provider / world renderer, or hooking the loader's dynamic-light
 * API) — exposed here via {@link #getTargetLevel()} / {@link #getTargetPos()} so
 * each platform adapter can apply it without touching this logic.
 */
public final class DynamicLightsFeature extends Feature {

    private final Setting<Boolean> heldItems = add(Setting.toggle("held", "Light from held items", true));
    private final Setting<Boolean> dropped   = add(Setting.toggle("dropped", "Light from dropped items", true));

    private volatile int targetLevel = 0;
    private volatile BlockPos targetPos = BlockPos.ORIGIN;

    public DynamicLightsFeature() {
        super("dynamic_lights", "Dynamic Lights",
                "Light sources held in your hand or lying on the ground emit light.",
                FeatureCategory.VISUAL, false);
    }

    @Override
    public void onClientTick(MinecraftClient mc) {
        int level = 0;
        if (heldItems.asBool()) {
            level = Math.max(luminanceOf(mc.player.getMainHandStack()),
                             luminanceOf(mc.player.getOffHandStack()));
        }
        this.targetLevel = level;
        this.targetPos = mc.player.getBlockPos();
        // Binding step (per loader): feed (targetPos, targetLevel) into the light
        // engine so the surrounding blocks brighten. Kept out of this shared class.
    }

    /** Light level (0–15) the player's held item should currently emit. */
    public int getTargetLevel() { return targetLevel; }
    public BlockPos getTargetPos() { return targetPos; }
    public boolean lightsFromDropped() { return dropped.asBool(); }

    /** Block luminance of a stack if it places a light-emitting block, else 0. */
    public static int luminanceOf(ItemStack stack) {
        if (stack != null && stack.getItem() instanceof BlockItem bi) {
            return bi.getBlock().getDefaultState().getLuminance();
        }
        return 0;
    }
}
