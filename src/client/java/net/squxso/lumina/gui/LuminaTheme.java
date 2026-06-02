package net.squxso.lumina.gui;

/**
 * Central colour palette for the whole Lumina UI.
 *
 * <p>All values are 0xAARRGGBB ints, the format expected by
 * {@link net.minecraft.client.gui.DrawContext#fill}. Keeping every colour in one
 * place means the entire client can be re-themed by editing this single file.
 */
public final class LuminaTheme {

    private LuminaTheme() {} // utility class — never instantiated

    // ── Surfaces ──────────────────────────────────────────────────────────
    public static final int PANEL        = 0xF5140520; // main window background
    public static final int PANEL_DEEP   = 0xF50E0317; // gradient bottom of panel
    public static final int PANEL_HEADER = 0xFF22093A; // header strip
    public static final int ROW          = 0xE61C0A2E; // resting button/row
    public static final int ROW_HOVER    = 0xFF341356; // hovered button/row
    public static final int ROW_ACTIVE   = 0xFF4A1B7A; // selected tab / enabled toggle
    public static final int TRACK        = 0xFF1E0A33; // slider track

    // ── Accents ───────────────────────────────────────────────────────────
    public static final int ACCENT       = 0xFFC36BFF; // bright violet highlight
    public static final int ACCENT_HOT   = 0xFFE3A8FF; // brightest accent (pop)
    public static final int ACCENT_DIM   = 0xFF8138C4; // softer accent / borders
    public static final int ACCENT_DEEP  = 0xFF3A1060; // darkest accent (glow base)
    public static final int ACCENT2      = 0xFF6B8BFF; // secondary indigo accent
    public static final int THUMB        = 0xFFE7BCFF; // slider handle
    public static final int THUMB_HOT    = 0xFFFFE8FF; // slider handle (hover/drag)

    // ── Text ──────────────────────────────────────────────────────────────
    public static final int TEXT         = 0xFFF2E6FF; // primary text
    public static final int TEXT_DIM     = 0xFFB79AD9; // secondary text
    public static final int TEXT_MUTED   = 0xFF7C5DA6; // tertiary / hints

    // ── State colours ─────────────────────────────────────────────────────
    public static final int ON           = 0xFF66F0B0; // enabled
    public static final int OFF          = 0xFFF06A88; // disabled
    public static final int WARN         = 0xFFFF5577; // warnings
}
