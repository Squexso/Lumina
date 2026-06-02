package net.squxso.lumina.client;

import net.minecraft.client.gui.Font;
import net.minecraft.client.gui.GuiGraphics;

/**
 * LuminaMC's visual language for in-game GUIs — violet→cyan cosmic palette, soft
 * glows, glassy translucency, true rounded rectangles and pill toggles — so screens
 * look clean and consistent and match the launcher / brand on every loader.
 */
public final class LuminaTheme {

    private LuminaTheme() {}

    // ── palette (ARGB) ────────────────────────────────────────────────────
    public static final int ACCENT      = 0xFF8B5CF6; // violet
    public static final int ACCENT_SOFT = 0xFFB7A2FF;
    public static final int ACCENT_DEEP = 0xFF5B21B6;
    public static final int CYAN        = 0xFF67E8F9; // gradient companion
    public static final int SCRIM_TOP   = 0xA8090518;
    public static final int SCRIM_BOT   = 0xCC04020C;
    public static final int PANEL       = 0xE01A1230; // translucent glass body
    public static final int PANEL_INNER = 0x3A241B44; // inner section fill
    public static final int BORDER      = 0x99B79BFF; // panel outline
    public static final int DIVIDER     = 0x1AFFFFFF;
    public static final int TAB_IDLE    = 0x24201838;
    public static final int TEXT        = 0xFFF3EEFC;
    public static final int TEXT_MUTED  = 0xFFA79CC6;
    public static final int TEXT_FAINT  = 0xFF6E6488;
    public static final int ROW_HOVER   = 0x16B7A2FF;
    public static final int TRACK_OFF   = 0xFF3A2F54;
    public static final int TRACK_HOVER = 0xFF483A66;
    public static final int KNOB        = 0xFFF6F2FE;

    /** A filled rectangle with real rounded corners (quarter-circle, per-row). */
    public static void roundRect(GuiGraphics g, int x, int y, int w, int h, int r, int color) {
        if (w <= 0 || h <= 0) return;
        r = Math.max(0, Math.min(r, Math.min(w, h) / 2));
        if (r == 0) { g.fill(x, y, x + w, y + h, color); return; }
        g.fill(x, y + r, x + w, y + h - r, color);
        for (int i = 0; i < r; i++) {
            int inset = r - (int) Math.floor(Math.sqrt((double) r * r - (double) (r - i) * (r - i)));
            g.fill(x + inset, y + i, x + w - inset, y + i + 1, color);
            g.fill(x + inset, y + h - 1 - i, x + w - inset, y + h - i, color);
        }
    }

    /** Soft glow halo around a rounded rectangle (concentric translucent rings). */
    public static void glow(GuiGraphics g, int x, int y, int w, int h, int r, int rgb, int layers) {
        for (int i = layers; i >= 1; i--) {
            roundRect(g, x - i, y - i, w + 2 * i, h + 2 * i, r + i, (0x10 << 24) | (rgb & 0xFFFFFF));
        }
    }

    /** A glowing, bordered glass panel card. */
    public static void panel(GuiGraphics g, int x, int y, int w, int h, int r) {
        glow(g, x, y, w, h, r, ACCENT, 6);
        roundRect(g, x - 1, y - 1, w + 2, h + 2, r + 1, BORDER);
        roundRect(g, x, y, w, h, r, PANEL);
    }

    /** A clean pill toggle switch; on = violet with a soft glow + knob to the right. */
    public static void toggle(GuiGraphics g, int x, int y, int w, int h, boolean on, boolean hover) {
        if (on) glow(g, x, y, w, h, h / 2, ACCENT, 3);
        roundRect(g, x, y, w, h, h / 2, on ? ACCENT : (hover ? TRACK_HOVER : TRACK_OFF));
        if (on) roundRect(g, x + 2, y + 2, w - 4, (h - 4) / 2, (h - 4) / 4, 0x33FFFFFF); // top sheen
        int knob = h - 4;
        int kx = on ? (x + w - knob - 2) : (x + 2);
        roundRect(g, kx, y + 2, knob, knob, knob / 2, KNOB);
    }

    /** A small readable HUD card: 1px violet border on a dark translucent body. */
    public static void card(GuiGraphics g, int x, int y, int w, int h, int r) {
        roundRect(g, x - 1, y - 1, w + 2, h + 2, r + 1, 0x66A78BFA);
        roundRect(g, x, y, w, h, r, 0xDC130B26);
    }

    /** A glossy progress/health pill bar (track + rounded fill + top highlight). */
    public static void bar(GuiGraphics g, int x, int y, int w, int h, float frac, int color) {
        frac = Math.max(0f, Math.min(1f, frac));
        roundRect(g, x, y, w, h, h / 2, 0xFF241B33);
        int fw = frac <= 0f ? 0 : Math.max(h, Math.round(w * frac));
        if (fw > 0) {
            roundRect(g, x, y, fw, h, h / 2, color);
            if (h >= 4) roundRect(g, x + 1, y + 1, Math.max(1, fw - 2), Math.max(1, h / 2 - 1), (h - 2) / 4, 0x33FFFFFF);
        }
    }

    /** Per-character horizontal gradient text (no shadow). */
    public static void gradientText(GuiGraphics g, Font f, String s, int x, int y, int from, int to) {
        int cx = x, n = s.length();
        for (int i = 0; i < n; i++) {
            String ch = String.valueOf(s.charAt(i));
            g.drawString(f, ch, cx, y, lerpColor(from, to, n <= 1 ? 0f : (float) i / (n - 1)), false);
            cx += f.width(ch);
        }
    }

    /** Linear-interpolate two ARGB colours. */
    public static int lerpColor(int a, int b, float t) {
        int aa = (a >>> 24) & 0xFF, ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int ba = (b >>> 24) & 0xFF, br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        return (Math.round(aa + (ba - aa) * t) << 24) | (Math.round(ar + (br - ar) * t) << 16)
                | (Math.round(ag + (bg - ag) * t) << 8) | Math.round(ab + (bb - ab) * t);
    }
}
