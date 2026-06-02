package net.squxso.lumina.client;

import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.squxso.lumina.feature.Feature;
import net.squxso.lumina.feature.FeatureCategory;
import net.squxso.lumina.feature.FeatureManager;
import org.joml.Matrix3x2fStack;

import java.util.List;

/**
 * The Right-Shift control panel — a glassy, glowing card in LuminaMC's violet→cyan
 * style: a dim backdrop, a soft glow halo, a gradient "LUMINA CLIENT" title, pill
 * category tabs and feature rows with glowing pill toggles. Fully custom-drawn via
 * {@link LuminaTheme}, identical on every loader.
 */
public final class LuminaScreen extends Screen {

    private static final int PW = 344, PH = 280;
    private static final String VERSION = "1.1.8";
    private static final FeatureCategory[] CATS = FeatureCategory.values();

    private int activeTab = 0;
    private int scroll = 0;

    public LuminaScreen() { super(Component.literal("LuminaMC")); }

    @Override public boolean isPauseScreen() { return false; }

    private int px() { return (this.width - PW) / 2; }
    private int py() { return (this.height - PH) / 2; }

    private List<Feature> rows() { return FeatureManager.byCategory(CATS[activeTab]); }

    private int[] tabRect(int i) {
        int n = CATS.length, gap = 5, areaW = PW - 32;
        int pillW = (areaW - (n - 1) * gap) / n;
        return new int[]{ px() + 16 + i * (pillW + gap), py() + 44, pillW, 22 };
    }
    private int[] editRect() {
        return new int[]{ px() + 22, py() + PH - 31, this.font.width("✎ Edit HUD Layout") + 8, 22 };
    }
    private int contentTop() { return py() + 74; }
    private int contentBottom() { return py() + PH - 34; }
    private int[] rowRect(int j) {
        return new int[]{ px() + 16, contentTop() + 4 + j * 36 - scroll, PW - 32, 32 };
    }

    @Override
    public void render(GuiGraphics g, int mouseX, int mouseY, float partial) {
        // Own backdrop instead of Screen.renderBackground() — vanilla's blur throws
        // "Can only blur once per frame" when another mod (Sodium) already blurred.
        g.fillGradient(0, 0, this.width, this.height, LuminaTheme.SCRIM_TOP, LuminaTheme.SCRIM_BOT);
        g.fill(0, 0, this.width, this.height, 0x10A78BFA);

        int px = px(), py = py();
        LuminaTheme.panel(g, px, py, PW, PH, 13);
        g.fillGradient(px + 13, py + 2, px + PW - 13, py + 40, 0x40A78BFA, 0x00140A28); // header sheen

        // ── title: gradient LUMINA CLIENT + version (no icons) ──────────────
        Matrix3x2fStack m = g.pose();
        m.pushMatrix();
        m.translate(px + 20f, py + 17f);
        m.scale(1.4f, 1.4f);
        LuminaTheme.gradientText(g, this.font, "LUMINA CLIENT", 0, 0, LuminaTheme.ACCENT, LuminaTheme.CYAN);
        m.popMatrix();
        int titleW = (int) (1.4f * this.font.width("LUMINA CLIENT"));
        g.drawString(this.font, "v" + VERSION, px + 22 + titleW, py + 22, LuminaTheme.TEXT_FAINT, false);

        // ── pill tab bar ────────────────────────────────────────────────────
        for (int i = 0; i < CATS.length; i++) {
            int[] t = tabRect(i);
            boolean active = i == activeTab;
            boolean hover = inside(mouseX, mouseY, t);
            if (active) {
                LuminaTheme.glow(g, t[0], t[1], t[2], t[3], 11, LuminaTheme.ACCENT, 3);
                LuminaTheme.roundRect(g, t[0], t[1], t[2], t[3], 11, LuminaTheme.ACCENT);
            } else {
                LuminaTheme.roundRect(g, t[0], t[1], t[2], t[3], 11, LuminaTheme.TAB_IDLE);
            }
            int col = active ? 0xFFFFFFFF : (hover ? LuminaTheme.ACCENT_SOFT : LuminaTheme.TEXT_MUTED);
            String label = CATS[i].label;
            g.drawString(this.font, label, t[0] + (t[2] - this.font.width(label)) / 2, t[1] + 7, col, false);
        }

        // ── feature rows (scrollable, clipped to the content region) ────────
        List<Feature> rows = rows();
        int ct = contentTop(), cb = contentBottom(), viewH = cb - ct;
        int totalH = rows.size() * 36;
        int maxScroll = Math.max(0, totalH - viewH + 8);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        if (rows.isEmpty()) {
            String none = "Nothing here yet";
            g.drawString(this.font, none, px + (PW - this.font.width(none)) / 2, ct + viewH / 2 - 4, LuminaTheme.TEXT_FAINT, false);
        }

        g.enableScissor(px + 12, ct, px + PW - 12, cb);
        for (int j = 0; j < rows.size(); j++) {
            Feature f = rows.get(j);
            int[] r = rowRect(j);
            if (r[1] + r[3] < ct || r[1] > cb) continue; // skip off-screen rows
            boolean hover = mouseY >= ct && mouseY < cb && inside(mouseX, mouseY, r);
            if (hover) LuminaTheme.roundRect(g, r[0], r[1], r[2], r[3], 9, LuminaTheme.ROW_HOVER);

            int nameCol = f.isEnabled() ? LuminaTheme.ACCENT_SOFT : LuminaTheme.TEXT;
            g.drawString(this.font, f.name, r[0] + 12, r[1] + 5, nameCol, false);
            g.drawString(this.font, trim(f.description, r[2] - 86), r[0] + 12, r[1] + 17, LuminaTheme.TEXT_MUTED, false);

            int tw = 32, th = 16, tx = r[0] + r[2] - 12 - tw, ty = r[1] + (r[3] - th) / 2;
            LuminaTheme.toggle(g, tx, ty, tw, th, f.isEnabled(), hover);
        }
        g.disableScissor();

        // scrollbar
        if (maxScroll > 0) {
            int barH = Math.max(16, viewH * viewH / totalH);
            int barY = ct + (viewH - barH) * scroll / maxScroll;
            LuminaTheme.roundRect(g, px + PW - 9, barY, 3, barH, 1, LuminaTheme.ACCENT);
        }

        // ── footer: Edit-HUD button + close hint ────────────────────────────
        int fy = py + PH - 30;
        LuminaTheme.roundRect(g, px + 16, fy, PW - 32, 20, 10, 0x14FFFFFF);
        boolean editHover = inside(mouseX, mouseY, editRect());
        g.drawString(this.font, "✎ Edit HUD Layout", px + 26, fy + 6,
                editHover ? LuminaTheme.CYAN : LuminaTheme.ACCENT_SOFT, false);
        String esc = "Esc to close";
        g.drawString(this.font, esc, px + PW - 26 - this.font.width(esc), fy + 6, LuminaTheme.TEXT_MUTED, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        if (event.button() == 0) {
            int mx = (int) event.x(), my = (int) event.y();
            if (inside(mx, my, editRect()) && this.minecraft != null) {
                this.minecraft.setScreen(new LuminaHudEditor());
                return true;
            }
            for (int i = 0; i < CATS.length; i++) {
                if (inside(mx, my, tabRect(i))) { activeTab = i; scroll = 0; return true; }
            }
            if (my >= contentTop() && my <= contentBottom()) {
                List<Feature> rows = rows();
                for (int j = 0; j < rows.size(); j++) {
                    if (inside(mx, my, rowRect(j))) {
                        Feature f = rows.get(j);
                        f.setEnabled(!f.isEnabled());
                        FeatureManager.save();
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        scroll -= (int) (scrollY * 22);
        return true;
    }

    private static boolean inside(int mx, int my, int[] r) {
        return mx >= r[0] && mx < r[0] + r[2] && my >= r[1] && my < r[1] + r[3];
    }

    /** Truncates {@code s} with an ellipsis to fit {@code maxWidth} px. */
    private String trim(String s, int maxWidth) {
        if (this.font.width(s) <= maxWidth) return s;
        while (s.length() > 1 && this.font.width(s + "…") > maxWidth) s = s.substring(0, s.length() - 1);
        return s + "…";
    }
}
