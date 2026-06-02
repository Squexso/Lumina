package net.squxso.lumina.feature.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.squxso.lumina.client.LuminaTheme;
import net.squxso.lumina.feature.FeatureCategory;
import net.squxso.lumina.feature.HudFeature;

/**
 * A labelled, glossy bar that fills as your vanilla attack cooldown recharges —
 * cyan + full when your next hit lands at max strength. Timing aid only.
 */
public final class AttackCooldownFeature extends HudFeature {

    public AttackCooldownFeature() {
        super("attack_cooldown", "Attack Cooldown", "Labelled bar: when your next hit is fully charged.",
                FeatureCategory.COMBAT, 6, 104);
    }

    @Override public void render(GuiGraphics ctx, Minecraft mc) {
        if (mc.player == null) return;
        float scale = mc.player.getAttackStrengthScale(0f); // 0..1
        boolean ready = scale >= 1f;
        int w = 96, h = 16;

        LuminaTheme.card(ctx, 0, 0, w, h, 6);
        ctx.drawString(mc.font, "ATK", 8, 4, ready ? LuminaTheme.CYAN : 0xFFB7AED0);
        LuminaTheme.bar(ctx, 33, 5, w - 33 - 9, 6, scale, ready ? LuminaTheme.CYAN : LuminaTheme.ACCENT);
    }

    @Override public int width(Minecraft mc) { return 96; }
    @Override public int height(Minecraft mc) { return 16; }
}
