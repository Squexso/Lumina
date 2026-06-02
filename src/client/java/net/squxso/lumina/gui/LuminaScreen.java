package net.squxso.lumina.gui;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.input.KeyInput;
import net.minecraft.text.Text;
import net.squxso.lumina.logic.LuminaKeybinds;
import net.squxso.lumina.logic.LuminaLayout;
import net.squxso.lumina.logic.LuminaLogic;
import net.squxso.lumina.logic.LuminaNotifications;
import net.squxso.lumina.logic.LuminaTweaks;
import org.lwjgl.glfw.GLFW;

/**
 * The in-game Lumina control panel (Right Shift, {@code /lumina gui}).
 *
 * <p>Panel position is stored in {@link LuminaLayout} and edited via the
 * layout editor (QoL tab → "Edit GUI Layout"), so it persists across reopens.
 */
public class LuminaScreen extends Screen {

    // ── Panel dimensions ─────────────────────────────────────────────────
    static final int PW = 340;
    static final int PH = 350;

    // ── Delay slider bounds ──────────────────────────────────────────────
    private static final int DELAY_FLOOR = 100;
    private static final int DELAY_CEIL  = 2000;

    private enum Tab { FISHING, COMBAT, QOL }
    private Tab tab = Tab.FISHING;

    public LuminaScreen() {
        super(Text.literal("LUMINA"));
    }

    // Position comes from the shared layout state, set in the layout editor.
    private int px() { return this.width  / 2 - PW / 2 + LuminaLayout.panelOffsetX; }
    private int py() { return this.height / 2 - PH / 2 + LuminaLayout.panelOffsetY; }

    // ══════════════════════════════════════════════════════════════════════
    //  LAYOUT
    // ══════════════════════════════════════════════════════════════════════
    @Override
    protected void init() {
        clearChildren();
        int px = px(), py = py();

        // ── Tab bar ──────────────────────────────────────────────────────
        int tabY = py + 44, tabH = 20, gap = 4;
        int tabW = (PW - 24 - gap * 2) / 3;
        int tx   = px + 12;
        addTab("Fishing", Tab.FISHING, tx,                    tabY, tabW, tabH);
        addTab("Combat",  Tab.COMBAT,  tx + tabW + gap,       tabY, tabW, tabH);
        addTab("QoL",     Tab.QOL,     tx + (tabW + gap) * 2, tabY, tabW, tabH);

        int bx = px + 12, bw = PW - 24, y = py + 74;

        switch (tab) {
            case FISHING -> buildFishing(bx, bw, y);
            case COMBAT  -> buildCombat(bx, bw, y);
            case QOL     -> buildQol(bx, bw, y);
        }

        addDrawableChild(new LuminaButton(px + PW / 2 - 60, py + PH - 28, 120, 20,
                "§lClose", this::close));
    }

    private void buildFishing(int bx, int bw, int y) {
        addDrawableChild(new LuminaToggle(bx, y, bw - 52, 20, "Auto Fisher",
                () -> LuminaLogic.enabled, LuminaLogic::toggle));
        addDrawableChild(new LuminaButton(bx + bw - 48, y, 48, 20,
                this::keyLabel,
                () -> {
                    LuminaKeybinds.listeningForKey = true;
                    LuminaNotifications.push("Press a key to bind · ESC clears");
                }));

        addDrawableChild(new LuminaToggle(bx, y + 26, bw, 20, "Catch Messages",
                () -> LuminaLogic.showCatchMessages,
                () -> LuminaLogic.showCatchMessages ^= true));
        addDrawableChild(new LuminaToggle(bx, y + 52, bw, 20, "Soft Look",
                () -> LuminaLogic.softLook,
                () -> LuminaLogic.softLook ^= true));
        addDrawableChild(new LuminaToggle(bx, y + 78, bw, 20, "Human Pauses",
                () -> LuminaLogic.humanPauses,
                () -> LuminaLogic.humanPauses ^= true));

        addDrawableChild(new LuminaRangeSlider(bx, y + 124, bw, 28,
                LuminaLogic.getDelayMin(), LuminaLogic.getDelayMax(),
                DELAY_FLOOR, DELAY_CEIL, LuminaLogic::setDelay));

        addDrawableChild(new LuminaButton(bx, y + 168, bw, 20, "§7Reset Catch Counter",
                () -> { LuminaLogic.resetCatchCount(); LuminaNotifications.push("Catch counter reset"); }));
    }

    private void buildCombat(int bx, int bw, int y) {
        addDrawableChild(new LuminaToggle(bx, y, bw, 20, "Wither Blade",
                () -> LuminaLogic.autoWitherBlade,
                () -> notifyToggle("Wither Blade", LuminaLogic.autoWitherBlade ^= true)));
        addDrawableChild(new LuminaToggle(bx, y + 26, bw, 20, "Yeti Sword",
                () -> LuminaLogic.autoYetiSword,
                () -> notifyToggle("Yeti Sword", LuminaLogic.autoYetiSword ^= true)));
        addDrawableChild(new LuminaToggle(bx, y + 52, bw, 20, "Ink Wand",
                () -> LuminaLogic.autoInkWand,
                () -> notifyToggle("Ink Wand", LuminaLogic.autoInkWand ^= true)));

        // Hint that abilities are cast on sea creature catch (chat-triggered).
        // Drawn in render() so it can be muted and doesn't need a widget.
    }

    private void buildQol(int bx, int bw, int y) {
        addDrawableChild(new LuminaToggle(bx, y, bw, 20, "Auto Totem",
                () -> LuminaLogic.autoTotem,
                () -> notifyToggle("Auto Totem", LuminaLogic.autoTotem ^= true)));
        addDrawableChild(new LuminaToggle(bx, y + 26, bw, 20, "Auto-Sell",
                () -> LuminaLogic.autoSell,
                () -> notifyToggle("Auto-Sell", LuminaLogic.autoSell ^= true)));
        addDrawableChild(new LuminaToggle(bx, y + 52, bw, 20, "HUD Overlay",
                () -> LuminaLogic.showHud,
                () -> notifyToggle("HUD Overlay", LuminaLogic.showHud ^= true)));

        // FPS Boost — lowers video settings; restored when toggled off.
        addDrawableChild(new LuminaToggle(bx, y + 78, bw, 20, "FPS Boost  §8(video settings)",
                () -> LuminaLogic.fpsBoost,
                () -> {
                    LuminaLogic.fpsBoost ^= true;
                    LuminaTweaks.setFpsBoost(MinecraftClient.getInstance(), LuminaLogic.fpsBoost);
                    LuminaNotifications.push(LuminaLogic.fpsBoost
                            ? "FPS Boost ON · graphics lowered"
                            : "FPS Boost OFF · settings restored");
                }));

        // ── Zoom ─────────────────────────────────────────────────────────
        int half = (bw - 4) / 2;
        addDrawableChild(new LuminaToggle(bx, y + 104, half, 20,
                "Zoom (Hold C)",
                () -> LuminaLogic.zoomEnabled,
                () -> notifyToggle("Zoom", LuminaLogic.zoomEnabled ^= true)));

        // Zoom sensitivity preset buttons (2×, 4×, 8×).
        int presetW = (bw - half - 8) / 3;
        int px2 = bx + half + 4;
        addDrawableChild(new LuminaButton(px2,               y + 104, presetW, 20,
                () -> zoomLabel("2×", 0.50f), () -> setZoom(0.50f)));
        addDrawableChild(new LuminaButton(px2 + presetW + 2, y + 104, presetW, 20,
                () -> zoomLabel("4×", 0.25f), () -> setZoom(0.25f)));
        addDrawableChild(new LuminaButton(px2 + (presetW + 2) * 2, y + 104, presetW, 20,
                () -> zoomLabel("8×", 0.125f), () -> setZoom(0.125f)));

        // Opens the layout editor to reposition the HUD and this panel.
        addDrawableChild(new LuminaButton(bx, y + 130, bw, 20, "§7Edit GUI Layout",
                () -> client.setScreen(new LuminaLayoutScreen(this))));
    }

    private Text zoomLabel(String label, float factor) {
        boolean active = Math.abs(LuminaTweaks.zoomFactor - factor) < 0.001f;
        return Text.literal(active ? "§d§l" + label : "§7" + label);
    }

    private void setZoom(float factor) {
        LuminaTweaks.zoomFactor = factor;
        LuminaNotifications.push("Zoom: " + (int)(1f / factor) + "× sensitivity");
    }

    // ══════════════════════════════════════════════════════════════════════
    //  RENDER
    // ══════════════════════════════════════════════════════════════════════
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        // Do NOT call renderBackground() — engine already blurred the background
        // once before render(); calling it again crashes ("Can only blur once per frame").
        int px = px(), py = py(), cx = px + PW / 2;

        // Glow halo.
        for (int i = 7; i >= 1; i--) {
            int a = 0x05 * i;
            ctx.fill(px - i, py - i, px + PW + i, py + PH + i, (a << 24) | 0xAA33FF);
        }

        // Panel body + header.
        ctx.fillGradient(px, py, px + PW, py + PH, LuminaTheme.PANEL, LuminaTheme.PANEL_DEEP);
        ctx.fill(px, py, px + PW, py + 38, LuminaTheme.PANEL_HEADER);
        ctx.fillGradient(px, py, px + PW, py + 3, LuminaTheme.ACCENT, LuminaTheme.ACCENT_DEEP);
        ctx.fill(px, py + 38, px + PW, py + 39, LuminaTheme.ACCENT_DEEP);

        // Outline.
        ctx.fill(px, py + PH - 1, px + PW, py + PH, LuminaTheme.ACCENT_DIM);
        ctx.fill(px, py, px + 1, py + PH, LuminaTheme.ACCENT_DIM);
        ctx.fill(px + PW - 1, py, px + PW, py + PH, LuminaTheme.ACCENT_DIM);

        // Title.
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("§d§lL U M I N A"),
                cx, py + 9, LuminaTheme.TEXT);
        String ver = FabricLoader.getInstance().getModContainer("lumina")
                .map(c -> c.getMetadata().getVersion().getFriendlyString()).orElse("?");
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("§8v" + ver + " · 1.21.10"),
                cx, py + 23, LuminaTheme.TEXT_MUTED);

        super.render(ctx, mx, my, delta);

        // Fishing-only: slider section labels.
        if (tab == Tab.FISHING) {
            int y = py + 74;
            ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("§5- §dRandom Delay Range §5-"),
                    cx, y + 110, LuminaTheme.ACCENT);
            // "Higher = safer" hint intentionally removed per user request.
        }

        // Combat-only: ability cast hint.
        if (tab == Tab.COMBAT) {
            int y = py + 74;
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("§8Abilities cast on sea-creature catch"),
                    cx, y + 84, LuminaTheme.TEXT_MUTED);
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("§8Swap → look down → Wither Impact / cast"),
                    cx, y + 96, LuminaTheme.TEXT_MUTED);
        }

        // Stats strip.
        long s = LuminaLogic.getActiveSeconds();
        String time = String.format("%02d:%02d:%02d", s / 3600, (s % 3600) / 60, s % 60);
        ctx.fill(px + 1, py + PH - 52, px + PW - 1, py + PH - 32, 0x22FFFFFF);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§7Session §d" + time + "  §8|  §7Catches §d" + LuminaLogic.getCatchCount()),
                cx, py + PH - 46, LuminaTheme.TEXT);

        renderNotifications(ctx);
    }

    private void renderNotifications(DrawContext ctx) {
        int nx = this.width - 8, ny = 8;
        for (var n : LuminaNotifications.getActiveNotifications()) {
            long rem   = n.expiresAt() - System.currentTimeMillis();
            int alpha  = (int) Math.min(240, rem < 500 ? rem * 240 / 500 : 240);
            String msg = n.message();
            int tw     = textRenderer.getWidth(msg);
            ctx.fill(nx - tw - 8, ny, nx + 2, ny + 14, ((alpha * 2 / 3) << 24) | 0x110025);
            ctx.fill(nx - tw - 8, ny, nx + 2, ny + 1,  (alpha << 24) | 0xAA33FF);
            ctx.fill(nx - tw - 8, ny, nx - tw - 7, ny + 14, (alpha << 24) | 0x7711BB);
            ctx.drawTextWithShadow(textRenderer, Text.literal("§d" + msg),
                    nx - tw - 4, ny + 3, (alpha << 24) | 0xCC77FF);
            ny += 18;
        }
    }

    // ══════════════════════════════════════════════════════════════════════
    //  INPUT — keybind capture only
    // ══════════════════════════════════════════════════════════════════════
    @Override
    public boolean keyPressed(KeyInput input) {
        if (LuminaKeybinds.listeningForKey) {
            if (input.key() == GLFW.GLFW_KEY_ESCAPE) {
                LuminaKeybinds.clear();
                LuminaNotifications.push("Keybind cleared");
            } else {
                LuminaKeybinds.bind(input);
                LuminaNotifications.push("Bound to " + LuminaKeybinds.boundLabel().getString());
            }
            LuminaKeybinds.listeningForKey = false;
            return true;
        }
        return super.keyPressed(input);
    }

    // ── Helpers ────────────────────────────────────────────────────────────
    private void addTab(String label, Tab t, int x, int y, int w, int h) {
        addDrawableChild(new LuminaButton(x, y, w, h, label,
                () -> { tab = t; clearAndInit(); }, () -> tab == t));
    }

    private Text keyLabel() {
        if (LuminaKeybinds.listeningForKey) return Text.literal("§e[?]");
        String name = LuminaKeybinds.boundLabel().getString();
        return name.equals("--")
                ? Text.literal("§8[--]")
                : Text.literal("§d[" + name + "]");
    }

    private void notifyToggle(String name, boolean state) {
        LuminaNotifications.push(name + (state ? " ON" : " OFF"));
    }

    @Override public boolean shouldPause() { return false; }
}
