package net.squxso.lumina.feature.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.entity.LivingEntity;
import net.minecraft.world.phys.EntityHitResult;
import net.squxso.lumina.client.LuminaTheme;
import net.squxso.lumina.feature.FeatureCategory;
import net.squxso.lumina.feature.HudFeature;

/** A small card showing the name + a glossy health bar of the entity you're looking at. */
public final class TargetInfoFeature extends HudFeature {

    private final Setting<Integer> accent = add(Setting.color("color", "Accent color", 0xFF8B5CF6));

    public TargetInfoFeature() {
        super("target_info", "Target Info", "Card with name + health bar of what you're looking at.",
                FeatureCategory.COMBAT, 6, 70);
    }

    private LivingEntity target(Minecraft mc) {
        if (mc.hitResult instanceof EntityHitResult e && e.getEntity() instanceof LivingEntity le) return le;
        return null;
    }

    private int cardW(Minecraft mc) {
        LivingEntity t = target(mc);
        return t == null ? 0 : Math.max(104, mc.font.width(t.getName().getString()) + 40);
    }

    @Override public void render(GuiGraphics ctx, Minecraft mc) {
        LivingEntity t = target(mc);
        if (t == null) return;
        String name = t.getName().getString();
        float hp = t.getHealth(), max = t.getMaxHealth();
        float frac = max > 0 ? hp / max : 0f;
        int w = cardW(mc), h = 27;

        LuminaTheme.card(ctx, 0, 0, w, h, 6);
        LuminaTheme.roundRect(ctx, 4, 5, 2, h - 10, 1, accent.asInt());
        ctx.drawString(mc.font, name, 9, 5, 0xFFFFFFFF);
        String hpTxt = (int) Math.ceil(hp) + " / " + (int) max;
        ctx.drawString(mc.font, hpTxt, w - 8 - mc.font.width(hpTxt), 5, healthColor(frac));
        LuminaTheme.bar(ctx, 8, 17, w - 16, 5, frac, healthColor(frac));
    }

    @Override public int width(Minecraft mc) { return cardW(mc); }
    @Override public int height(Minecraft mc) { return target(mc) == null ? 0 : 27; }

    private static int healthColor(float f) {
        if (f > 0.5f) return 0xFF63E2A0;
        if (f > 0.25f) return 0xFFFFCB6B;
        return 0xFFFF6B6B;
    }
}
