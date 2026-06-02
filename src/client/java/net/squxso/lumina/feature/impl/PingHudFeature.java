package net.squxso.lumina.feature.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.network.PlayerListEntry;
import net.squxso.lumina.feature.HudFeature;

/** Ping (latency) HUD, movable/scalable. */
public final class PingHudFeature extends HudFeature {

    private final Setting<Integer> color = add(Setting.color("color", "Text color", 0xFFB7AED0));

    public PingHudFeature() { super("ping_hud", "Ping", "Show your connection latency in milliseconds.", 6, 54); }

    private String text(MinecraftClient mc) {
        int ping = -1;
        ClientPlayNetworkHandler nh = mc.getNetworkHandler();
        if (nh != null) {
            PlayerListEntry e = nh.getPlayerListEntry(mc.player.getUuid());
            if (e != null) ping = e.getLatency();
        }
        return ping < 0 ? "Ping: §7—" : "Ping: " + ping + "ms";
    }
    @Override public void render(DrawContext ctx, MinecraftClient mc) {
        ctx.drawTextWithShadow(mc.textRenderer, text(mc), 0, 0, color.asInt());
    }
    @Override public int width(MinecraftClient mc) { return mc.textRenderer.getWidth("Ping: 000ms"); }
    @Override public int height(MinecraftClient mc) { return 9; }
}
