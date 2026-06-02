package net.squxso.lumina.feature.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.world.effect.MobEffectInstance;
import net.minecraft.world.entity.EquipmentSlot;
import net.minecraft.world.item.ItemStack;
import net.squxso.lumina.feature.HudFeature;

/**
 * Flat inventory-style HUD for equipped armour (icon + durability bar, with a low
 * durability warning) plus active potion effects.
 */
public final class ArmorPotionHudFeature extends HudFeature {

    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final Setting<Boolean> showArmor   = add(Setting.toggle("armor", "Show armour", true));
    private final Setting<Boolean> showEffects = add(Setting.toggle("effects", "Show potion effects", true));

    public ArmorPotionHudFeature() {
        super("armor_potion_hud", "Armor & Effects",
                "Armour HUD with durability + low-durability warning, plus active potion effects.", 6, 100);
    }

    @Override public void render(GuiGraphics ctx, Minecraft mc) {
        var font = mc.font;
        int py = 0;
        if (showArmor.asBool()) {
            for (EquipmentSlot slot : ARMOR) {
                ItemStack stack = mc.player.getItemBySlot(slot);
                if (stack.isEmpty()) continue;
                ctx.renderItem(stack, 1, py + 1);
                ctx.renderItemDecorations(font, stack, 1, py + 1);
                if (stack.isDamageableItem()) {
                    int max = stack.getMaxDamage(), left = max - stack.getDamageValue();
                    float frac = max > 0 ? (float) left / max : 1f;
                    int barX = 20, barY = py + 11, barW = 50;
                    ctx.fill(barX, barY, barX + barW, barY + 3, 0xFF202020);
                    ctx.fill(barX, barY, barX + (int) (barW * frac), barY + 3, barColor(frac));
                    ctx.drawString(font, (int) (frac * 100) + "%", 20, py + 1, 0xFFD8D0EC);
                    if (frac < 0.20f) {
                        ctx.fill(74, py + 3, 86, py + 15, 0xFFE03030);
                        ctx.drawCenteredString(font, "§f§l!", 80, py + 5, 0xFFFFFFFF);
                    }
                }
                py += 20;
            }
            py += 4;
        }
        if (showEffects.asBool()) {
            for (MobEffectInstance e : mc.player.getActiveEffects()) {
                String name = e.getEffect().value().getDisplayName().getString();
                int amp = e.getAmplifier();
                if (amp > 0) name += " " + roman(amp + 1);
                ctx.drawString(font, name + "  §7" + formatTime(e.getDuration()), 0, py, 0xFFCFC4EA);
                py += 11;
            }
        }
    }

    @Override public int width(Minecraft mc) { return 92; }
    @Override public int height(Minecraft mc) {
        int h = 0;
        if (showArmor.asBool()) for (EquipmentSlot s : ARMOR) if (!mc.player.getItemBySlot(s).isEmpty()) h += 20;
        if (showEffects.asBool()) h += mc.player.getActiveEffects().size() * 11 + 4;
        return Math.max(20, h);
    }

    private static int barColor(float frac) {
        if (frac > 0.5f) return 0xFF4ADE80;
        if (frac > 0.2f) return 0xFFFFC861;
        return 0xFFE03030;
    }
    private static String formatTime(int ticks) {
        if (ticks < 0) return "∞";
        int sec = ticks / 20;
        return String.format("%d:%02d", sec / 60, sec % 60);
    }
    private static String roman(int n) {
        return switch (n) { case 1 -> "I"; case 2 -> "II"; case 3 -> "III"; case 4 -> "IV"; case 5 -> "V"; default -> String.valueOf(n); };
    }
}
