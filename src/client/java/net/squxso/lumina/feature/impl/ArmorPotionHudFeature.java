package net.squxso.lumina.feature.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.entity.EquipmentSlot;
import net.minecraft.entity.effect.StatusEffectInstance;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.squxso.lumina.feature.HudFeature;

/**
 * Modern, flat inventory-style HUD for equipped armour (icon + durability bar)
 * and active potion effects. A bright red warning marker appears next to any
 * armour piece below 20 % durability. Movable/scalable via the HUD editor.
 */
public final class ArmorPotionHudFeature extends HudFeature {

    private static final EquipmentSlot[] ARMOR = {
            EquipmentSlot.HEAD, EquipmentSlot.CHEST, EquipmentSlot.LEGS, EquipmentSlot.FEET
    };

    private final Setting<Boolean> showArmor   = add(Setting.toggle("armor", "Show armour", true));
    private final Setting<Boolean> showEffects = add(Setting.toggle("effects", "Show potion effects", true));
    private final Setting<Boolean> background  = add(Setting.toggle("bg", "Show background", false));

    public ArmorPotionHudFeature() {
        super("armor_potion_hud", "Armor & Effects",
                "Flat armour HUD with durability + low-durability warning, plus active potion effects.", 6, 100);
    }

    @Override public void render(DrawContext ctx, MinecraftClient mc) {
        var tr = mc.textRenderer;
        int py = 0;
        if (showArmor.asBool()) {
            for (EquipmentSlot slot : ARMOR) {
                ItemStack stack = mc.player.getEquippedStack(slot);
                if (stack.isEmpty()) continue;
                int rowH = 18;
                if (background.asBool()) {
                    ctx.fill(0, py, 92, py + rowH, 0x55140A24);
                    ctx.fill(0, py, 1, py + rowH, 0x33C36BFF);
                }
                ctx.drawItem(stack, 1, py + 1);
                ctx.drawStackOverlay(tr, stack, 1, py + 1);
                if (stack.isDamageable()) {
                    int max = stack.getMaxDamage(), left = max - stack.getDamage();
                    float frac = max > 0 ? (float) left / max : 1f;
                    int barX = 20, barY = py + 11, barW = 50;
                    ctx.fill(barX, barY, barX + barW, barY + 3, 0xFF202020);
                    ctx.fill(barX, barY, barX + (int) (barW * frac), barY + 3, barColor(frac));
                    ctx.drawTextWithShadow(tr, (int) (frac * 100) + "%", 20, py + 1, 0xFFD8D0EC);
                    if (frac < 0.20f) {
                        ctx.fill(74, py + 3, 86, py + 15, 0xFFE03030);
                        ctx.drawCenteredTextWithShadow(tr, Text.literal("§f§l!"), 80, py + 5, 0xFFFFFFFF);
                    }
                }
                py += rowH + 2;
            }
            py += 4;
        }
        if (showEffects.asBool()) {
            for (StatusEffectInstance effect : mc.player.getStatusEffects()) {
                String name = effect.getEffectType().value().getName().getString();
                int amp = effect.getAmplifier();
                if (amp > 0) name += " " + roman(amp + 1);
                ctx.drawTextWithShadow(tr, name + "  §7" + formatTime(effect.getDuration()), 0, py, 0xFFCFC4EA);
                py += 11;
            }
        }
    }

    @Override public int width(MinecraftClient mc) { return 92; }
    @Override public int height(MinecraftClient mc) {
        int h = 0;
        if (showArmor.asBool()) for (EquipmentSlot s : ARMOR) if (!mc.player.getEquippedStack(s).isEmpty()) h += 20;
        if (showEffects.asBool()) h += mc.player.getStatusEffects().size() * 11 + 4;
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
