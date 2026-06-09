package net.squxso.lumina.feature.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.world.item.ItemStack;
import net.minecraft.world.level.LightLayer;
import net.minecraft.world.phys.BlockHitResult;

/** Static text providers for the generic mining HUD readouts (fair info only — no X-ray). */
public final class MiningTexts {

    private MiningTexts() {}

    public static String coords(Minecraft mc) {
        var p = mc.player;
        return String.format("§7XYZ §f%d  %d  %d", (int) Math.floor(p.getX()), (int) Math.floor(p.getY()), (int) Math.floor(p.getZ()));
    }

    public static String bedrock(Minecraft mc) {
        int above = (int) Math.floor(mc.player.getY()) + 64;
        return "§7Bedrock: §f" + above + " below";
    }

    public static String oreSheet(Minecraft mc) {
        return "§7Diamond Y-59 · Iron Y16 · Gold Y-16 · Copper Y48 · Coal Y96";
    }

    public static String lava(Minecraft mc) {
        return (int) Math.floor(mc.player.getY()) <= -50 ? "§c⚠ Lava risk — mine to the side" : "";
    }

    public static String tool(Minecraft mc) {
        ItemStack st = mc.player.getMainHandItem();
        if (st.isEmpty() || !st.isDamageableItem()) return "";
        int max = st.getMaxDamage(), left = max - st.getDamageValue();
        int pct = max > 0 ? left * 100 / max : 100;
        return (pct <= 15 ? "§c" : "§7Tool: §f") + (pct <= 15 ? "Tool: " + pct + "% — low!" : pct + "%");
    }

    public static String block(Minecraft mc) {
        if (mc.hitResult instanceof BlockHitResult bhr) {
            String name = mc.level.getBlockState(bhr.getBlockPos()).getBlock().getName().getString();
            return "§7Looking at: §f" + name;
        }
        return "";
    }

    public static String inventory(Minecraft mc) {
        int free = 0;
        for (int i = 0; i < 36; i++) if (mc.player.getInventory().getItem(i).isEmpty()) free++;
        if (free == 0) return "§c⚠ Inventory full";
        if (free <= 3) return "§eFree slots: " + free;
        return "";
    }

    public static String spawn(Minecraft mc) {
        int block = mc.level.getBrightness(LightLayer.BLOCK, mc.player.blockPosition());
        return block == 0 ? "§c⚠ Mobs can spawn here" : "";
    }

    public static String tunnel(Minecraft mc) {
        String dir = mc.player.getDirection().getName();
        String letter = dir.isEmpty() ? "?" : dir.substring(0, 1).toUpperCase();
        return "§7Tunnel: §f" + letter + " " + (int) Math.floor(mc.player.getYRot()) + "°";
    }
}
