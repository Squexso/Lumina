package net.squxso.lumina.feature.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.squxso.lumina.feature.HudFeature;

/** Shows the biome the player is standing in. */
public final class BiomeHudFeature extends HudFeature {

    private final Setting<Integer> color = add(Setting.color("color", "Text color", 0xFFB7AED0));

    public BiomeHudFeature() { super("biome_hud", "Biome", "Show the biome you're standing in.", 6, 54); }

    private String text(Minecraft mc) {
        String name = mc.level.getBiome(mc.player.blockPosition()).getRegisteredName(); // "minecraft:plains"
        int colon = name.indexOf(':');
        return "Biome: " + pretty(colon >= 0 ? name.substring(colon + 1) : name);
    }

    @Override public void render(GuiGraphics ctx, Minecraft mc) {
        if (mc.level == null || mc.player == null) return;
        ctx.drawString(mc.font, text(mc), 0, 0, color.asInt());
    }
    @Override public int width(Minecraft mc)  { return (mc.level == null || mc.player == null) ? 0 : mc.font.width(text(mc)); }
    @Override public int height(Minecraft mc) { return (mc.level == null || mc.player == null) ? 0 : 9; }

    private static String pretty(String id) {
        StringBuilder sb = new StringBuilder();
        for (String part : id.split("_")) {
            if (part.isEmpty()) continue;
            if (sb.length() > 0) sb.append(' ');
            sb.append(Character.toUpperCase(part.charAt(0))).append(part.substring(1));
        }
        return sb.length() == 0 ? id : sb.toString();
    }
}
