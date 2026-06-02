package net.squxso.lumina.client;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.minecraft.client.gui.screens.Screen;
import net.minecraft.client.input.MouseButtonEvent;
import net.minecraft.network.chat.Component;
import net.squxso.lumina.feature.Feature;
import net.squxso.lumina.feature.FeatureManager;
import net.squxso.lumina.feature.HudFeature;

import java.util.ArrayList;
import java.util.List;

/**
 * Drag-and-drop HUD layout editor: every enabled HUD element is shown where it will
 * appear in-game. Click an element to pick it up (it follows the cursor), click again
 * to drop it; scroll to scale; Esc saves. Elements with no live data (e.g. Target Info
 * when not aiming at a mob) show a labelled placeholder so they can still be placed.
 */
public final class LuminaHudEditor extends Screen {

    private HudFeature grabbed;
    private int grabOffX, grabOffY;

    public LuminaHudEditor() { super(Component.literal("HUD Editor")); }

    @Override public boolean isPauseScreen() { return false; }

    private List<HudFeature> huds() {
        List<HudFeature> l = new ArrayList<>();
        for (Feature f : FeatureManager.all())
            if (f.isEnabled() && f instanceof HudFeature h) l.add(h);
        return l;
    }

    /** [x, y, w, h, emptyFlag] — bounding box of a HUD element (placeholder size if no data). */
    private int[] box(Minecraft mc, HudFeature h) {
        int rw = h.width(mc), rh = h.height(mc);
        boolean empty = rw <= 0 || rh <= 0;
        int w = empty ? Math.max(46, this.font.width(h.name) + 10) : Math.max(8, (int) (rw * h.scale()));
        int hh = empty ? 13 : Math.max(8, (int) (rh * h.scale()));
        return new int[]{ h.posX(), h.posY(), w, hh, empty ? 1 : 0 };
    }

    @Override
    public void render(GuiGraphics g, int mx, int my, float pt) {
        g.fill(0, 0, this.width, this.height, 0xCC0A0613);
        g.fill(this.width / 2, 0, this.width / 2 + 1, this.height, 0x14FFFFFF); // centre guide

        Minecraft mc = Minecraft.getInstance();
        if (grabbed != null) {
            int nx = Math.max(0, Math.min(mx - grabOffX, this.width - 6));
            int ny = Math.max(0, Math.min(my - grabOffY, this.height - 6));
            grabbed.setPos(nx, ny);
        }

        for (HudFeature h : huds()) {
            int[] b = box(mc, h);
            if (b[4] == 1) {
                LuminaTheme.card(g, b[0], b[1], b[2], b[3], 4);
                g.drawString(this.font, h.name, b[0] + 5, b[1] + 3, LuminaTheme.TEXT_MUTED, false);
            } else {
                h.onRenderHud(g, mc);
            }
            boolean sel = h == grabbed;
            boolean hover = !sel && mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3];
            int col = sel ? LuminaTheme.CYAN : (hover ? LuminaTheme.ACCENT : 0x66B7A2FF);
            outline(g, b[0] - 2, b[1] - 2, b[2] + 4, b[3] + 4, col);
            if (sel || hover) g.drawString(this.font, h.name, b[0] - 1, b[1] - 11, col, false);
        }

        String title = "HUD Editor";
        g.drawString(this.font, title, this.width / 2 - this.font.width(title) / 2, 10, LuminaTheme.ACCENT_SOFT, false);
        String hint = grabbed == null
                ? "Click an element to pick it up  ·  Scroll to scale  ·  Esc to save"
                : "Click to place  ·  Scroll to scale  ·  Esc to save";
        g.drawString(this.font, hint, this.width / 2 - this.font.width(hint) / 2, this.height - 16, LuminaTheme.TEXT_MUTED, false);
    }

    private static void outline(GuiGraphics g, int x, int y, int w, int h, int c) {
        g.fill(x, y, x + w, y + 1, c);
        g.fill(x, y + h - 1, x + w, y + h, c);
        g.fill(x, y, x + 1, y + h, c);
        g.fill(x + w - 1, y, x + w, y + h, c);
    }

    @Override
    public boolean mouseClicked(MouseButtonEvent e, boolean dbl) {
        if (e.button() == 0) {
            int mx = (int) e.x(), my = (int) e.y();
            if (grabbed != null) { grabbed = null; FeatureManager.save(); return true; }
            Minecraft mc = Minecraft.getInstance();
            List<HudFeature> hs = huds();
            for (int i = hs.size() - 1; i >= 0; i--) {
                HudFeature h = hs.get(i);
                int[] b = box(mc, h);
                if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) {
                    grabbed = h; grabOffX = mx - h.posX(); grabOffY = my - h.posY(); return true;
                }
            }
        }
        return super.mouseClicked(e, dbl);
    }

    @Override
    public boolean mouseScrolled(double mx, double my, double scrollX, double scrollY) {
        Minecraft mc = Minecraft.getInstance();
        HudFeature target = grabbed;
        if (target == null) {
            List<HudFeature> hs = huds();
            for (int i = hs.size() - 1; i >= 0; i--) {
                HudFeature h = hs.get(i);
                int[] b = box(mc, h);
                if (mx >= b[0] && mx < b[0] + b[2] && my >= b[1] && my < b[1] + b[3]) { target = h; break; }
            }
        }
        if (target != null) { target.addScale((int) (scrollY * 10)); FeatureManager.save(); return true; }
        return false;
    }

    @Override
    public void onClose() {
        FeatureManager.save();
        super.onClose();
    }
}
