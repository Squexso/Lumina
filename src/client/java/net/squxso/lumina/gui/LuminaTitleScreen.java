package net.squxso.lumina.gui;

import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.client.gui.screen.world.SelectWorldScreen;
import net.minecraft.text.Text;
import net.minecraft.util.Identifier;

/**
 * Fully custom main menu — no vanilla elements remain.
 *
 * <p>Layout (all Y values are relative to the computed {@code topY}):
 * <pre>
 *   topY + 0              logo image  (88 × 88 px)
 *   topY + 94             "L U M I N A" title
 *   topY + 107            version line
 *   topY + 127            Singleplayer button
 *   topY + 152            Multiplayer button
 *   topY + 177            Options button
 *   topY + 202            ★ Lumina Panel button
 *   topY + 235            Quit button
 * </pre>
 * Total content height ≈ 257 px, centred vertically (min top margin = 6 px).
 */
public class LuminaTitleScreen extends Screen {

    private static final int BTN_W = 220;
    private static final int BTN_H = 22;
    private static final int LOGO_SZ = 88;

    /** Logo texture: assets/lumina/textures/gui/logo.png (256 × 256 px — Lumina crystal). */
    private static final Identifier LOGO     = Identifier.of("lumina", "textures/gui/logo.png");
    /** Actual pixel dimensions of logo.png — used for correct UV mapping. */
    private static final float      LOGO_TEX = 256f;

    // Total content block height so we can centre it.
    private static final int CONTENT_H = LOGO_SZ + 6 + 10 + 10 + 20 + 22 + 25 + 22 + 25 + 22 + 25 + 22 + 33 + 22;
    // ≈ logo(88) + gap(6) + title(10) + ver(10) + gap(20) + btn×4 with 25 spacing + gap(33) + quit(22)
    // = 88+6+10+10+20+(22+25)*3+22+33+22 = 257

    public LuminaTitleScreen() {
        super(Text.literal("Lumina"));
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    /** Y coordinate of the top of the content block (centred, ≥ 6 px from screen top). */
    private int topY() {
        return Math.max(6, (this.height - 257) / 2);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  LAYOUT
    // ══════════════════════════════════════════════════════════════════════
    @Override
    protected void init() {
        int bx   = this.width / 2 - BTN_W / 2;
        int top  = topY();
        // Buttons start after: logo(88) + gap(6) + title(10) + ver(10) + gap(20) + extra(3) = 137
        int by   = top + 137;

        addDrawableChild(new LuminaButton(bx, by,       BTN_W, BTN_H, "Singleplayer",
                () -> client.setScreen(new SelectWorldScreen(this))));
        addDrawableChild(new LuminaButton(bx, by + 27,  BTN_W, BTN_H, "Multiplayer",
                () -> client.setScreen(new LuminaMultiplayerScreen(this))));
        addDrawableChild(new LuminaButton(bx, by + 54,  BTN_W, BTN_H, "Options",
                () -> client.setScreen(new LuminaOptionsScreen(this, client.options))));
        addDrawableChild(new LuminaButton(bx, by + 81,  BTN_W, BTN_H, "§d§l★  Lumina Panel",
                () -> client.setScreen(new LuminaScreen())));
        // Quit has a small extra gap above it.
        addDrawableChild(new LuminaButton(bx, by + 114, BTN_W, BTN_H, "§7Quit",
                () -> client.scheduleStop()));
    }

    // ══════════════════════════════════════════════════════════════════════
    //  BACKGROUND
    // ══════════════════════════════════════════════════════════════════════
    @Override
    public void renderBackground(DrawContext ctx, int mx, int my, float delta) {
        int w = this.width, h = this.height;
        long t = System.currentTimeMillis();

        // Deep-space gradient.
        ctx.fillGradient(0, 0, w, h, 0xFF160622, 0xFF070210);

        // Slow diagonal light-sweep.
        int sweep = (int) ((t / 40) % (w + 200)) - 100;
        ctx.fillGradient(sweep, 0, sweep + 120, h, 0x00C36BFF, 0x18C36BFF);

        // Twinkling stars.
        for (int i = 0; i < 22; i++) {
            int dx = (int) ((i * 79 + t / 60) % w);
            int dy = (i * 131) % h;
            int tw = (int) ((Math.sin(t / 500.0 + i) + 1) * 55);
            ctx.fill(dx, dy, dx + 2, dy + 2, ((tw & 0xFF) << 24) | 0xC36BFF);
        }

        // Accent rails.
        ctx.fillGradient(0, 0,     w, 3,     LuminaTheme.ACCENT,      LuminaTheme.ACCENT2);
        ctx.fillGradient(0, h - 3, w, h,     LuminaTheme.ACCENT_DEEP, LuminaTheme.ACCENT_DIM);
    }

    // ══════════════════════════════════════════════════════════════════════
    //  RENDER
    // ══════════════════════════════════════════════════════════════════════
    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta); // background + widgets

        int cx  = this.width / 2;
        int top = topY();

        // ── Logo image ────────────────────────────────────────────────────
        int lx = cx - LOGO_SZ / 2;
        int ly = top;
        // drawTexturedQuad UV is in texture-pixel space for this MC build.
        try {
            ctx.drawTexturedQuad(LOGO,
                    lx, ly, lx + LOGO_SZ, ly + LOGO_SZ,
                    0f, 0f, LOGO_TEX, LOGO_TEX);
        } catch (Exception ignored) {
            // Fallback: draw a glowing star so the area is never empty.
            ctx.drawCenteredTextWithShadow(textRenderer,
                    Text.literal("§d§l✦"), cx, top + LOGO_SZ / 2 - 5, 0xDD88FF);
        }

        // ── Text card ─────────────────────────────────────────────────────
        int titleY = top + LOGO_SZ + 6;
        int verY   = titleY + 13;

        // Subtle backing strip.
        ctx.fill(cx - 116, titleY - 3, cx + 116, verY + 11, 0x33000018);
        ctx.fill(cx - 116, titleY - 3, cx + 116, titleY - 2, 0xCCDD88FF);

        String ver = FabricLoader.getInstance()
                .getModContainer("lumina")
                .map(c -> c.getMetadata().getVersion().getFriendlyString())
                .orElse("?");

        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§d§lL U M I N A"), cx, titleY, LuminaTheme.TEXT);
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§8v" + ver + " · 1.21.10"),  cx, verY, LuminaTheme.TEXT_MUTED);

        // ── Footer ────────────────────────────────────────────────────────
        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§8Lumina · Private SkyBlock Client"),
                cx, this.height - 12, LuminaTheme.TEXT_MUTED);
    }

    @Override public boolean shouldCloseOnEsc() { return false; }
}
