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
    // All categories except hidden, launcher-driven ones (e.g. COSMETIC).
    private static final FeatureCategory[] CATS = java.util.Arrays.stream(FeatureCategory.values())
            .filter(c -> c != FeatureCategory.COSMETIC)
            .toArray(FeatureCategory[]::new);

    private int activeTab = 0;
    private int scroll = 0;
    private Feature settingsFor = null;   // when set, the panel shows this feature's settings

    private static final int[] COLOR_PRESETS = {
            0xFFB7AED0, 0xFF8B5CF6, 0xFF67E8F9, 0xFF5BE08A, 0xFFFFCB6B, 0xFFFF6B6B, 0xFFE9A5FF, 0xFFFFFFFF
    };

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
    private int[] screenshotsRect() {
        int w = this.font.width("Screenshots") + 10;
        return new int[]{ px() + PW - 22 - w, py() + PH - 31, w, 22 };
    }
    private void openScreenshots() {
        if (this.minecraft != null) this.minecraft.setScreen(new LuminaScreenshotGallery());
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

        // ── content: feature list, or one feature's settings ───────────────
        int ct = contentTop(), cb = contentBottom(), viewH = cb - ct;
        if (settingsFor != null) {
            renderSettings(g, mouseX, mouseY, px, ct, cb);
        } else {
            List<Feature> rows = rows();
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
                g.drawString(this.font, trim(f.description, r[2] - 110), r[0] + 12, r[1] + 17, LuminaTheme.TEXT_MUTED, false);

                int tw = 32, th = 16, tx = r[0] + r[2] - 12 - tw, ty = r[1] + (r[3] - th) / 2;
                LuminaTheme.toggle(g, tx, ty, tw, th, f.isEnabled(), hover);

                // settings affordance (sliders icon) for features that expose options
                if (!f.settings().isEmpty()) {
                    int[] gr = gearRect(r);
                    boolean gh = hover && inside(mouseX, mouseY, gr);
                    int gc = gh ? LuminaTheme.CYAN : LuminaTheme.TEXT_MUTED, cy = gr[1] + gr[3] / 2;
                    for (int li = -1; li <= 1; li++) g.fill(gr[0], cy + li * 4, gr[0] + 12, cy + li * 4 + 1, gc);
                }
            }
            g.disableScissor();

            if (maxScroll > 0) {
                int barH = Math.max(16, viewH * viewH / totalH);
                int barY = ct + (viewH - barH) * scroll / maxScroll;
                LuminaTheme.roundRect(g, px + PW - 9, barY, 3, barH, 1, LuminaTheme.ACCENT);
            }
        }

        // ── footer: Edit-HUD button + close hint ────────────────────────────
        int fy = py + PH - 30;
        LuminaTheme.roundRect(g, px + 16, fy, PW - 32, 20, 10, 0x14FFFFFF);
        boolean editHover = inside(mouseX, mouseY, editRect());
        g.drawString(this.font, "✎ Edit HUD Layout", px + 26, fy + 6,
                editHover ? LuminaTheme.CYAN : LuminaTheme.ACCENT_SOFT, false);
        String shots = "Screenshots";
        boolean shotHover = inside(mouseX, mouseY, screenshotsRect());
        g.drawString(this.font, shots, px + PW - 26 - this.font.width(shots), fy + 6,
                shotHover ? LuminaTheme.CYAN : LuminaTheme.ACCENT_SOFT, false);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent event, boolean doubleClick) {
        int mx = (int) event.x(), my = (int) event.y();

        // ── settings sub-view ──────────────────────────────────────────────
        if (settingsFor != null) {
            if (event.button() == 1) { settingsFor = null; scroll = 0; return true; }   // right-click = back
            if (event.button() == 0) {
                if (inside(mx, my, backRect())) { settingsFor = null; scroll = 0; return true; }
                int ct = contentTop(), cb = contentBottom();
                if (my >= ct + 26 && my <= cb) {
                    List<Feature.Setting<?>> list = settingsFor.settings();
                    for (int k = 0; k < list.size(); k++) {
                        int[] sr = settingRect(k);
                        if (!inside(mx, my, sr)) continue;
                        applyClick(list.get(k), mx, sr);
                        FeatureManager.save();
                        return true;
                    }
                }
            }
            return true;   // stay in settings; swallow stray clicks
        }

        if (event.button() == 0) {
            if (inside(mx, my, editRect()) && this.minecraft != null) {
                this.minecraft.setScreen(new LuminaHudEditor());
                return true;
            }
            if (inside(mx, my, screenshotsRect())) { openScreenshots(); return true; }
            for (int i = 0; i < CATS.length; i++) {
                if (inside(mx, my, tabRect(i))) { activeTab = i; scroll = 0; return true; }
            }
            if (my >= contentTop() && my <= contentBottom()) {
                List<Feature> rows = rows();
                for (int j = 0; j < rows.size(); j++) {
                    int[] r = rowRect(j);
                    if (!inside(mx, my, r)) continue;
                    Feature f = rows.get(j);
                    if (!f.settings().isEmpty() && inside(mx, my, gearRect(r))) { settingsFor = f; scroll = 0; return true; }
                    f.setEnabled(!f.isEnabled());
                    FeatureManager.save();
                    return true;
                }
            }
        } else if (event.button() == 1) {   // right-click a feature → its settings
            if (my >= contentTop() && my <= contentBottom()) {
                List<Feature> rows = rows();
                for (int j = 0; j < rows.size(); j++) {
                    if (inside(mx, my, rowRect(j))) {
                        Feature f = rows.get(j);
                        if (!f.settings().isEmpty()) { settingsFor = f; scroll = 0; }
                        return true;
                    }
                }
            }
        }
        return super.mouseClicked(event, doubleClick);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        if (settingsFor != null) {
            List<Feature.Setting<?>> list = settingsFor.settings();
            for (int k = 0; k < list.size(); k++) {
                Feature.Setting<?> s = list.get(k);
                if (s.kind == Feature.Setting.Kind.INT && inside((int) mx, (int) my, settingRect(k))) {
                    setInt(s, clamp(s.asInt() + (int) Math.signum(scrollY), s.min, s.max));
                    FeatureManager.save();
                    return true;
                }
            }
        }
        scroll -= (int) (scrollY * 22);
        return true;
    }

    private void applyClick(Feature.Setting<?> s, int mx, int[] sr) {
        switch (s.kind) {
            case INT -> {
                int[] tr = sliderTrack(sr);
                float frac = tr[2] <= 0 ? 0 : (float) (mx - tr[0]) / tr[2];
                setInt(s, clamp(s.min + Math.round(frac * (s.max - s.min)), s.min, s.max));
            }
            case TOGGLE -> setBool(s, !s.asBool());
            case ENUM -> cycleEnum(s);
            case COLOR -> cycleColor(s);
        }
    }

    private void renderSettings(GuiGraphics g, int mouseX, int mouseY, int px, int ct, int cb) {
        List<Feature.Setting<?>> list = settingsFor.settings();

        int[] back = backRect();
        boolean bh = inside(mouseX, mouseY, back);
        LuminaTheme.roundRect(g, back[0], back[1], back[2], back[3], 9, bh ? LuminaTheme.ROW_HOVER : LuminaTheme.TAB_IDLE);
        g.drawString(this.font, "← Back", back[0] + 8, back[1] + 5, bh ? LuminaTheme.CYAN : LuminaTheme.ACCENT_SOFT, false);
        g.drawString(this.font, settingsFor.name, back[0] + back[2] + 10, back[1] + 5, LuminaTheme.ACCENT_SOFT, false);

        int listTop = ct + 26, viewH = cb - listTop;
        int totalH = list.size() * 34;
        int maxScroll = Math.max(0, totalH - viewH + 4);
        scroll = Math.max(0, Math.min(scroll, maxScroll));

        if (list.isEmpty()) {
            String none = "No options for this feature";
            g.drawString(this.font, none, px + (PW - this.font.width(none)) / 2, listTop + 20, LuminaTheme.TEXT_FAINT, false);
            return;
        }

        g.enableScissor(px + 12, listTop, px + PW - 12, cb);
        for (int k = 0; k < list.size(); k++) {
            Feature.Setting<?> s = list.get(k);
            int[] sr = settingRect(k);
            if (sr[1] + sr[3] < listTop || sr[1] > cb) continue;
            boolean hover = mouseY >= listTop && mouseY < cb && inside(mouseX, mouseY, sr);
            if (hover) LuminaTheme.roundRect(g, sr[0], sr[1], sr[2], sr[3], 8, LuminaTheme.ROW_HOVER);
            g.drawString(this.font, s.label, sr[0] + 10, sr[1] + 3, LuminaTheme.TEXT, false);

            switch (s.kind) {
                case INT -> {
                    String val = String.valueOf(s.asInt());
                    g.drawString(this.font, val, sr[0] + sr[2] - 10 - this.font.width(val), sr[1] + 3, LuminaTheme.ACCENT_SOFT, false);
                    int[] tr = sliderTrack(sr);
                    LuminaTheme.roundRect(g, tr[0], tr[1], tr[2], 4, 2, 0xFF2A2238);
                    float frac = s.max > s.min ? (float) (s.asInt() - s.min) / (s.max - s.min) : 0f;
                    int fillW = Math.max(2, Math.round(tr[2] * frac));
                    LuminaTheme.roundRect(g, tr[0], tr[1], fillW, 4, 2, LuminaTheme.ACCENT);
                    LuminaTheme.roundRect(g, tr[0] + fillW - 3, tr[1] - 3, 6, 10, 3, LuminaTheme.CYAN);
                }
                case TOGGLE -> LuminaTheme.toggle(g, sr[0] + sr[2] - 12 - 32, sr[1] + 4, 32, 16, s.asBool(), hover);
                case COLOR -> {
                    int sw = 28, sx = sr[0] + sr[2] - 12 - sw, sy = sr[1] + 5;
                    LuminaTheme.roundRect(g, sx - 1, sy - 1, sw + 2, 14, 4, 0x66FFFFFF);
                    LuminaTheme.roundRect(g, sx, sy, sw, 12, 3, s.asInt());
                }
                case ENUM -> {
                    String v = s.asString();
                    g.drawString(this.font, v, sr[0] + sr[2] - 10 - this.font.width(v), sr[1] + 3, LuminaTheme.CYAN, false);
                }
            }
        }
        g.disableScissor();

        if (maxScroll > 0) {
            int barH = Math.max(16, viewH * viewH / totalH);
            int barY = listTop + (viewH - barH) * scroll / maxScroll;
            LuminaTheme.roundRect(g, px + PW - 9, barY, 3, barH, 1, LuminaTheme.ACCENT);
        }
    }

    private int[] backRect() { return new int[]{ px() + 16, contentTop(), 58, 18 }; }
    private int[] gearRect(int[] r) { return new int[]{ r[0] + r[2] - 64, r[1] + 4, 14, r[3] - 8 }; }
    private int[] settingRect(int k) { return new int[]{ px() + 16, contentTop() + 26 + k * 34 - scroll, PW - 32, 30 }; }
    private int[] sliderTrack(int[] sr) { return new int[]{ sr[0] + 10, sr[1] + 20, sr[2] - 20 }; }

    private static int clamp(int v, int lo, int hi) { return Math.max(lo, Math.min(hi, v)); }
    @SuppressWarnings("unchecked") private static void setInt(Feature.Setting<?> s, int v)  { ((Feature.Setting<Integer>) s).set(v); }
    @SuppressWarnings("unchecked") private static void setBool(Feature.Setting<?> s, boolean v) { ((Feature.Setting<Boolean>) s).set(v); }
    @SuppressWarnings("unchecked") private static void setStr(Feature.Setting<?> s, String v) { ((Feature.Setting<String>) s).set(v); }
    private static void cycleEnum(Feature.Setting<?> s) {
        String[] o = s.options; if (o == null || o.length == 0) return;
        int i = 0; for (int x = 0; x < o.length; x++) if (o[x].equals(s.asString())) i = x;
        setStr(s, o[(i + 1) % o.length]);
    }
    private static void cycleColor(Feature.Setting<?> s) {
        int cur = s.asInt(), idx = 0;
        for (int x = 0; x < COLOR_PRESETS.length; x++) if (COLOR_PRESETS[x] == cur) idx = x;
        setInt(s, COLOR_PRESETS[(idx + 1) % COLOR_PRESETS.length]);
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
