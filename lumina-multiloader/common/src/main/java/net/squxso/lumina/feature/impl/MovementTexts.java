package net.squxso.lumina.feature.impl;

import net.minecraft.client.Minecraft;

/** Static text providers for the movement-tab readouts (fair info only). */
public final class MovementTexts {

    private MovementTexts() {}

    public static String fall(Minecraft mc) {
        double f = mc.player.fallDistance;
        return f < 1.0 ? "" : "§7Fall: §f" + (int) f + "m";
    }
    public static String vspeed(Minecraft mc) {
        return String.format("§7V-Speed: §f%.1f b/s", mc.player.getDeltaMovement().y * 20.0);
    }
    public static String sprint(Minecraft mc) {
        return mc.player.isSprinting() ? "§eSprinting" : "";
    }
    public static String sneak(Minecraft mc) {
        return mc.player.isCrouching() ? "§7Sneaking" : "";
    }
    public static String heading(Minecraft mc) {
        return "§7Heading: §f" + (int) Math.floor(mc.player.getYRot()) + "°  " + mc.player.getDirection().getName();
    }
}
