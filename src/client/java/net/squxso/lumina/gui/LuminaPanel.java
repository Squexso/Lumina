package net.squxso.lumina.gui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.OrderedText;
import net.minecraft.text.Text;
import net.squxso.lumina.feature.Feature;
import net.squxso.lumina.feature.FeatureCategory;
import net.squxso.lumina.feature.FeatureManager;

import java.util.List;

/**
 * The LuminaMC control panel (Right Shift) — compact, minimalist dark theme with
 * lilac accents. It floats over the game (no full-screen dim) and shows a
 * description of whichever feature the mouse is hovering.
 */
public final class LuminaPanel extends Screen {

    private static final int PW = 268;
    private static final int HEADER = 28;
    private static final int TAB_H = 16;
    private static final int ROW_H = 18;
    private static final int PAD = 8;
    private static final int DESC_H = 30;

    private FeatureCategory active = FeatureCategory.HUD;
    private int firstRowY;

    public LuminaPanel() {
        super(Text.literal("LuminaMC"));
    }

    // Fixed height (sized for the largest category) — the window never resizes.
    private static final int PH = 300;

    private int rows() { return FeatureManager.byCategory(active).size(); }
    private int ph()  { return PH; }
    private int px()  { return this.width / 2 - PW / 2; }
    private int py()  { return this.height / 2 - ph() / 2; }

    @Override
    protected void init() {
        clearChildren();
        int px = px(), py = py();

        FeatureCategory[] cats = FeatureCategory.values();
        int gap = 3, tabW = (PW - PAD * 2 - gap * (cats.length - 1)) / cats.length;
        int tabY = py + HEADER, tx = px + PAD;
        for (FeatureCategory cat : cats) {
            FeatureCategory c = cat;
            addDrawableChild(new LuminaButton(tx, tabY, tabW, TAB_H, cat.label,
                    () -> { active = c; clearChildren(); init(); },
                    () -> active == c));
            tx += tabW + gap;
        }

        int rowX = px + PAD, rowW = PW - PAD * 2, y = py + HEADER + TAB_H + 8;
        firstRowY = y;
        for (Feature f : FeatureManager.byCategory(active)) {
            Feature ff = f;
            addDrawableChild(new LuminaToggleRow(rowX, y, rowW, ROW_H - 2, ff.name,
                    ff::isEnabled,
                    () -> { ff.setEnabled(!ff.isEnabled()); FeatureManager.save(); }));
            y += ROW_H;
        }

        // Edit-HUD button — opens the free-placement editor.
        addDrawableChild(new LuminaButton(px + PAD, py + ph() - 44, PW - PAD * 2, 16,
                "✎  Edit HUD layout",
                () -> { if (this.client != null) this.client.setScreen(new LuminaHudEditor()); }));

        addDrawableChild(new LuminaButton(px + PW / 2 - 45, py + ph() - 22, 90, 16, "Close", this::close));
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        // No super call → no full-screen dim; the panel floats over the live game.
        int px = px(), py = py(), ph = ph();

        ctx.fill(px, py, px + PW, py + ph, 0xE61A1228);                  // body
        ctx.fill(px, py, px + PW, py + HEADER, 0xF0241638);             // header
        ctx.fill(px, py, px + PW, py + 1, LuminaTheme.ACCENT);
        ctx.fill(px, py + HEADER - 1, px + PW, py + HEADER, 0x55C36BFF);
        ctx.fill(px, py, px + 1, py + ph, 0x33C36BFF);
        ctx.fill(px + PW - 1, py, px + PW, py + ph, 0x33C36BFF);
        ctx.fill(px, py + ph - 1, px + PW, py + ph, 0x33C36BFF);

        ctx.drawTextWithShadow(this.textRenderer, Text.literal("§d§lLUMINA§r §8MC"), px + PAD, py + 6, 0xFFE3A8FF);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("§8RShift"),
                px + PW - PAD - this.textRenderer.getWidth("RShift"), py + 6, 0xFF7C5DA6);

        // Description box: fixed position above the buttons (panel size never changes).
        int descY = py + ph() - 52 - DESC_H;
        ctx.fill(px + PAD, descY, px + PW - PAD, descY + DESC_H, 0x44000000);
        Feature hovered = hoveredFeature(mouseX, mouseY);
        if (hovered != null) {
            List<OrderedText> lines = this.textRenderer.wrapLines(Text.literal(hovered.description), PW - PAD * 2 - 6);
            int ty = descY + 4;
            for (int i = 0; i < Math.min(lines.size(), 3); i++) {
                ctx.drawTextWithShadow(this.textRenderer, lines.get(i), px + PAD + 3, ty, 0xFFB7AED0);
                ty += 9;
            }
        } else {
            ctx.drawTextWithShadow(this.textRenderer, Text.literal("§8hover for info · right-click for options"),
                    px + PAD + 3, descY + 4, 0xFF6E6486);
        }
    }

    private Feature hoveredFeature(int mouseX, int mouseY) {
        int px = px();
        if (mouseX < px + PAD || mouseX > px + PW - PAD) return null;
        var list = FeatureManager.byCategory(active);
        int idx = (mouseY - firstRowY) / ROW_H;
        if (idx >= 0 && idx < list.size() && mouseY >= firstRowY) return list.get(idx);
        return null;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        // Right-click a feature row → open its detailed settings (sliders/colors/…).
        if (click.button() == 1) {
            Feature f = hoveredFeature((int) click.x(), (int) click.y());
            if (f != null && !f.settings().isEmpty() && this.client != null) {
                this.client.setScreen(new LuminaSettingsScreen(f, this));
                return true;
            }
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean shouldPause() { return false; }
}
