package net.squxso.lumina.gui;

import net.fabricmc.fabric.api.client.rendering.v1.HudRenderCallback;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.render.RenderTickCounter;
import net.minecraft.text.Text;
import net.squxso.lumina.logic.LuminaLayout;
import net.squxso.lumina.logic.LuminaLogic;
import net.squxso.lumina.logic.LuminaNotifications;

public class LuminaHud implements HudRenderCallback {

    @Override
    public void onHudRender(DrawContext ctx, RenderTickCounter ticker) {
        MinecraftClient mc = MinecraftClient.getInstance();
        if (mc.player == null) return;

        if (mc.currentScreen == null && LuminaLogic.showHud) {
            long sec = LuminaLogic.getActiveSeconds();
            String time   = String.format("%02d:%02d:%02d", sec / 3600, (sec % 3600) / 60, sec % 60);
            String status = LuminaLogic.enabled ? "§aON" : "§cOFF";

            int x = LuminaLayout.hudX + 2, y = LuminaLayout.hudY + 2, w = 115, h = 52;

            ctx.fill(x - 2, y - 2, x + w, y + h, 0xAA0D0014);
            ctx.fill(x - 2, y - 2, x + w, y,     0xFF9933CC);

            ctx.drawTextWithShadow(mc.textRenderer,
                    "§d§lLUMINA", x, y + 2, 0xFFDD88FF);
            ctx.drawTextWithShadow(mc.textRenderer,
                    "§7Status: " + status, x, y + 13, 0xFFCC77FF);
            ctx.drawTextWithShadow(mc.textRenderer,
                    "§7Session: §d" + time, x, y + 23, 0xFFCC77FF);
            ctx.drawTextWithShadow(mc.textRenderer,
                    "§7Catches: §d" + LuminaLogic.getCatchCount(), x, y + 33, 0xFFCC77FF);
            ctx.drawTextWithShadow(mc.textRenderer,
                    "§7Delay: §d" + LuminaLogic.getDelayMin() + "-" + LuminaLogic.getDelayMax() + "ms",
                    x, y + 43, 0xFFCC77FF);
        }

        // ── Weapon-ability scan status banner ────────────────────────────────
        if (mc.currentScreen == null
                && System.currentTimeMillis() < LuminaLogic.abilityStatusUntil
                && !LuminaLogic.abilityStatusText.isEmpty()) {

            net.minecraft.text.Text statusTxt =
                    net.minecraft.text.Text.literal(LuminaLogic.abilityStatusText);
            int sw = mc.getWindow().getScaledWidth();
            int sh = mc.getWindow().getScaledHeight();
            int tw = mc.textRenderer.getWidth(statusTxt);
            int sx = (sw - tw) / 2;
            int sy = sh / 2 + 18; // just below the crosshair

            ctx.fill(sx - 6, sy - 3, sx + tw + 6, sy + 11, 0xBB000010);
            ctx.fill(sx - 6, sy - 3, sx + tw + 6, sy - 2,  LuminaTheme.ACCENT);
            ctx.drawTextWithShadow(mc.textRenderer, statusTxt, sx, sy, 0xFFFFFFFF);
        }

        if (mc.currentScreen == null) {
            var notifs = LuminaNotifications.getActiveNotifications();
            int nx = mc.getWindow().getScaledWidth() - 8;
            int ny = 8;

            for (var n : notifs) {
                long rem  = n.expiresAt() - System.currentTimeMillis();
                int alpha = (int) Math.min(240, rem < 500 ? rem * 240 / 500 : 240);
                String msg = n.message();
                int tw = mc.textRenderer.getWidth(msg);

                ctx.fill(nx - tw - 8, ny,     nx + 2, ny + 14, ((alpha * 2 / 3) << 24) | 0x110025);
                ctx.fill(nx - tw - 8, ny,     nx + 2, ny + 1,  (alpha << 24) | 0xAA33FF);
                ctx.fill(nx - tw - 8, ny,     nx - tw - 7, ny + 14, (alpha << 24) | 0x7711BB);

                ctx.drawTextWithShadow(mc.textRenderer,
                        Text.literal("§d" + msg),
                        nx - tw - 4, ny + 3,
                        (alpha << 24) | 0xCC77FF);
                ny += 18;
            }
        }
    }
}