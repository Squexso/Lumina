package net.squxso.lumina.feature.impl;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.multiplayer.ClientPacketListener;
import net.minecraft.client.multiplayer.PlayerInfo;
import net.squxso.lumina.feature.HudFeature;

/** Ping (latency) HUD, movable/scalable. */
public final class PingHudFeature extends HudFeature {

    private final Setting<Integer> color = add(Setting.color("color", "Text color", 0xFFB7AED0));

    public PingHudFeature() { super("ping_hud", "Ping", "Show your connection latency in milliseconds.", 6, 54); }

    private String text(Minecraft mc) {
        int ping = -1;
        ClientPacketListener nh = mc.getConnection();
        if (nh != null && mc.player != null) {
            PlayerInfo e = nh.getPlayerInfo(mc.player.getUUID());
            if (e != null) ping = e.getLatency();
        }
        return ping < 0 ? "Ping: §7—" : "Ping: " + ping + "ms";
    }
    @Override public void render(GuiGraphics ctx, Minecraft mc) {
        ctx.drawString(mc.font, text(mc), 0, 0, color.asInt());
    }
    @Override public int width(Minecraft mc) { return mc.font.width("Ping: 000ms"); }
    @Override public int height(Minecraft mc) { return 9; }
}
