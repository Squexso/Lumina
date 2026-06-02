package net.squxso.lumina.logic;

/**
 * Shared screen positions for Lumina's movable UI pieces.
 *
 * <p>Edited from the in-game layout editor ({@code LuminaLayoutScreen}) and read by
 * the HUD and the control panel. Kept as plain static state — positions live for the
 * session; there is no on-disk config yet.
 *
 * <ul>
 *   <li>{@code hudX/hudY} — top-left corner of the status overlay.</li>
 *   <li>{@code panelOffsetX/Y} — offset of the control panel from screen centre.</li>
 * </ul>
 */
public final class LuminaLayout {

    private LuminaLayout() {}

    public static int hudX = 6;
    public static int hudY = 6;

    public static int panelOffsetX = 0;
    public static int panelOffsetY = 0;
}
