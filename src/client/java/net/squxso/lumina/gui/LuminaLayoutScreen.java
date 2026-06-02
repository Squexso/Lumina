package net.squxso.lumina.gui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.squxso.lumina.logic.LuminaLayout;

/**
 * In-game layout editor: drag the status overlay and the control panel to new
 * positions. Reached from the QoL tab ("Edit GUI Layout"). Positions are written
 * straight into {@link LuminaLayout}, so the HUD and panel pick them up immediately.
 */
public class LuminaLayoutScreen extends Screen {

    // HUD outer box size (mirrors LuminaHud).
    private static final int HUD_W = 117, HUD_H = 54;
    // Panel size (mirrors LuminaScreen).
    private static final int PANEL_W = 340, PANEL_H = 350;

    private final Screen parent;

    /** What is being dragged: -1 none, 0 HUD, 1 panel. */
    private int dragging = -1;
    private double grabMouseX, grabMouseY;
    private int grabValX, grabValY;

    public LuminaLayoutScreen(Screen parent) {
        super(Text.literal("Lumina Layout"));
        this.parent = parent;
    }

    private int panelX() { return this.width  / 2 - PANEL_W / 2 + LuminaLayout.panelOffsetX; }
    private int panelY() { return this.height / 2 - PANEL_H / 2 + LuminaLayout.panelOffsetY; }

    @Override
    protected void init() {
        addDrawableChild(new LuminaButton(this.width / 2 - 60, this.height - 26, 120, 20,
                "§lDone", () -> this.client.setScreen(parent)));
    }

    @Override
    public void render(DrawContext ctx, int mx, int my, float delta) {
        super.render(ctx, mx, my, delta); // dim background + Done button

        ctx.drawCenteredTextWithShadow(textRenderer,
                Text.literal("§d§lEdit Layout §7— drag the boxes"),
                this.width / 2, 10, LuminaTheme.TEXT);

        // ── Panel placeholder ────────────────────────────────────────────
        int px = panelX(), py = panelY();
        boolean overP = inPanel(mx, my);
        ctx.fill(px, py, px + PANEL_W, py + PANEL_H, overP ? 0xC0241038 : 0xA01A0628);
        ctx.fill(px, py, px + PANEL_W, py + 20, LuminaTheme.PANEL_HEADER);
        outline(ctx, px, py, PANEL_W, PANEL_H, overP ? LuminaTheme.ACCENT : LuminaTheme.ACCENT_DIM);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("§d§lLumina Panel"),
                px + PANEL_W / 2, py + 6, LuminaTheme.TEXT);
        ctx.drawCenteredTextWithShadow(textRenderer, Text.literal("§8(control panel position)"),
                px + PANEL_W / 2, py + PANEL_H / 2, LuminaTheme.TEXT_MUTED);

        // ── HUD placeholder (matches the real overlay) ───────────────────
        int hx = LuminaLayout.hudX, hy = LuminaLayout.hudY;
        boolean overH = inHud(mx, my);
        ctx.fill(hx, hy, hx + HUD_W, hy + HUD_H, 0xCC0D0014);
        ctx.fill(hx, hy, hx + HUD_W, hy + 2, overH ? LuminaTheme.ACCENT : 0xFF9933CC);
        outline(ctx, hx, hy, HUD_W, HUD_H, overH ? LuminaTheme.ACCENT : LuminaTheme.ACCENT_DIM);
        ctx.drawTextWithShadow(textRenderer, Text.literal("§d§lLUMINA"),       hx + 8, hy + 4,  0xFFDD88FF);
        ctx.drawTextWithShadow(textRenderer, Text.literal("§7Status: §aON"),   hx + 8, hy + 15, 0xFFCC77FF);
        ctx.drawTextWithShadow(textRenderer, Text.literal("§7Session: §d…"),   hx + 8, hy + 25, 0xFFCC77FF);
        ctx.drawTextWithShadow(textRenderer, Text.literal("§7Catches: §d0"),   hx + 8, hy + 35, 0xFFCC77FF);
    }

    private void outline(DrawContext ctx, int x, int y, int w, int h, int c) {
        ctx.fill(x, y, x + w, y + 1, c);
        ctx.fill(x, y + h - 1, x + w, y + h, c);
        ctx.fill(x, y, x + 1, y + h, c);
        ctx.fill(x + w - 1, y, x + w, y + h, c);
    }

    private boolean inHud(double mx, double my) {
        return mx >= LuminaLayout.hudX && mx <= LuminaLayout.hudX + HUD_W
            && my >= LuminaLayout.hudY && my <= LuminaLayout.hudY + HUD_H;
    }

    private boolean inPanel(double mx, double my) {
        return mx >= panelX() && mx <= panelX() + PANEL_W
            && my >= panelY() && my <= panelY() + PANEL_H;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        if (super.mouseClicked(click, doubled)) return true; // Done button first
        double mx = click.x(), my = click.y();
        if (inHud(mx, my)) {
            dragging = 0; grabMouseX = mx; grabMouseY = my;
            grabValX = LuminaLayout.hudX; grabValY = LuminaLayout.hudY;
            return true;
        }
        if (inPanel(mx, my)) {
            dragging = 1; grabMouseX = mx; grabMouseY = my;
            grabValX = LuminaLayout.panelOffsetX; grabValY = LuminaLayout.panelOffsetY;
            return true;
        }
        return false;
    }

    @Override
    public boolean mouseDragged(Click click, double dx, double dy) {
        if (dragging == 0) {
            LuminaLayout.hudX = clamp(grabValX + (int) (click.x() - grabMouseX), 0, this.width  - HUD_W);
            LuminaLayout.hudY = clamp(grabValY + (int) (click.y() - grabMouseY), 0, this.height - HUD_H);
            return true;
        }
        if (dragging == 1) {
            LuminaLayout.panelOffsetX = grabValX + (int) (click.x() - grabMouseX);
            LuminaLayout.panelOffsetY = grabValY + (int) (click.y() - grabMouseY);
            clampPanel();
            return true;
        }
        return super.mouseDragged(click, dx, dy);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragging != -1) { dragging = -1; return true; }
        return super.mouseReleased(click);
    }

    private void clampPanel() {
        int centreX = this.width / 2 - PANEL_W / 2, centreY = this.height / 2 - PANEL_H / 2;
        LuminaLayout.panelOffsetX = clamp(LuminaLayout.panelOffsetX, -centreX, this.width  - PANEL_W - centreX);
        LuminaLayout.panelOffsetY = clamp(LuminaLayout.panelOffsetY, -centreY, this.height - PANEL_H - centreY);
    }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }

    @Override
    public void close() { this.client.setScreen(parent); }
}
