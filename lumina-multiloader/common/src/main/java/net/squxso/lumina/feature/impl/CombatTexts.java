package net.squxso.lumina.feature.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;

/** Static text providers for the combat-tab stat readouts (fair info only). */
public final class CombatTexts {

    private CombatTexts() {}

    public static String health(Minecraft mc) {
        return "§7HP: §f" + (int) Math.ceil(mc.player.getHealth()) + " / " + (int) mc.player.getMaxHealth();
    }
    public static String hunger(Minecraft mc) {
        return "§7Hunger: §f" + mc.player.getFoodData().getFoodLevel() + " / 20";
    }
    public static String saturation(Minecraft mc) {
        return "§7Saturation: §f" + (int) mc.player.getFoodData().getSaturationLevel();
    }
    public static String armor(Minecraft mc) {
        return "§7Armor: §f" + mc.player.getArmorValue() + " pts";
    }
    public static String held(Minecraft mc) {
        ItemStack st = mc.player.getMainHandItem();
        return st.isEmpty() ? "" : "§7Held: §f" + st.getCount() + "x " + st.getHoverName().getString();
    }
    public static String xp(Minecraft mc) {
        return "§7XP Level: §f" + mc.player.experienceLevel;
    }
}
