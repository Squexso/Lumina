package net.squxso.lumina.feature.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.item.ItemStack;
import net.minecraft.text.Text;
import net.squxso.lumina.feature.HudFeature;

/** Held-tool durability HUD with a low-durability warning. Movable/scalable. */
public final class ToolHudFeature extends HudFeature {

    private final Setting<Boolean> background = add(Setting.toggle("bg", "Show background", false));

    public ToolHudFeature() {
        super("tool_hud", "Tool Durability",
                "Show the durability of the item in your hand, with a low-durability warning.", 6, 150);
    }

    @Override public void render(DrawContext ctx, MinecraftClient mc) {
        int py = renderStack(ctx, mc, mc.player.getMainHandStack(), 0);
        renderStack(ctx, mc, mc.player.getOffHandStack(), py);
    }

    private int renderStack(DrawContext ctx, MinecraftClient mc, ItemStack stack, int py) {
        if (stack.isEmpty() || !stack.isDamageable()) return py;
        var tr = mc.textRenderer;
        int rowH = 18;
        if (background.asBool()) {
            ctx.fill(0, py, 92, py + rowH, 0x55140A24);
            ctx.fill(0, py, 1, py + rowH, 0x33C36BFF);
        }
        ctx.drawItem(stack, 1, py + 1);
        ctx.drawStackOverlay(tr, stack, 1, py + 1);
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
        return py + rowH + 2;
    }

    @Override public int width(MinecraftClient mc) { return 92; }
    @Override public int height(MinecraftClient mc) {
        int h = 0;
        if (mc.player.getMainHandStack().isDamageable()) h += 20;
        if (mc.player.getOffHandStack().isDamageable()) h += 20;
        return Math.max(20, h);
    }

    private static int barColor(float frac) {
        if (frac > 0.5f) return 0xFF4ADE80;
        if (frac > 0.2f) return 0xFFFFC861;
        return 0xFFE03030;
    }
}
