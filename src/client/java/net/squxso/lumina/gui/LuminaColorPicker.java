package net.squxso.lumina.gui;

import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.function.IntConsumer;

/**
 * Visual HSV colour picker: a saturation/value field on top and a rainbow hue
 * bar below. Click or drag in either area to choose any colour — no presets,
 * no number-fiddling.
 */
public final class LuminaColorPicker extends ClickableWidget {

    private static final int HUE_H = 12, GAP = 4;

    private final IntConsumer onPick;
    private float hue, sat, val;

    public LuminaColorPicker(int x, int y, int w, int h, int initialArgb, IntConsumer onPick) {
        super(x, y, w, h, Text.literal("Color"));
        this.onPick = onPick;
        float[] hsb = java.awt.Color.RGBtoHSB((initialArgb >> 16) & 0xFF, (initialArgb >> 8) & 0xFF, initialArgb & 0xFF, null);
        this.hue = hsb[0];
        this.sat = hsb[1];
        this.val = hsb[2];
    }

    private int svH() { return getHeight() - HUE_H - GAP; }

    @Override
    protected void renderWidget(DrawContext ctx, int mx, int my, float delta) {
        int x = getX(), y = getY(), w = getWidth(), sv = svH();
        int hueColor = 0xFF000000 | (java.awt.Color.HSBtoRGB(hue, 1f, 1f) & 0xFFFFFF);

        // SV field: horizontal white→hue, then vertical transparent→black overlay.
        for (int i = 0; i < w; i++) {
            int col = lerp(0xFFFFFFFF, hueColor, i / (float) w);
            ctx.fill(x + i, y, x + i + 1, y + sv, col);
        }
        ctx.fillGradient(x, y, x + w, y + sv, 0x00000000, 0xFF000000);

        // SV cursor.
        int cxp = x + (int) (sat * w);
        int cyp = y + (int) ((1 - val) * sv);
        ctx.fill(cxp - 2, cyp - 2, cxp + 2, cyp + 2, 0xFF000000);
        ctx.fill(cxp - 1, cyp - 1, cxp + 1, cyp + 1, 0xFFFFFFFF);

        // Hue bar (rainbow).
        int hy = y + sv + GAP;
        for (int i = 0; i < w; i++) {
            int col = 0xFF000000 | (java.awt.Color.HSBtoRGB(i / (float) w, 1f, 1f) & 0xFFFFFF);
            ctx.fill(x + i, hy, x + i + 1, hy + HUE_H, col);
        }
        int hxp = x + (int) (hue * w);
        ctx.fill(hxp - 1, hy - 1, hxp + 1, hy + HUE_H + 1, 0xFFFFFFFF);
    }

    private void apply(double mxx, double myy) {
        int x = getX(), y = getY(), w = getWidth(), sv = svH();
        if (myy <= y + sv) {
            sat = clamp((float) ((mxx - x) / w));
            val = 1f - clamp((float) ((myy - y) / sv));
        } else {
            hue = clamp((float) ((mxx - x) / w));
        }
        int rgb = java.awt.Color.HSBtoRGB(hue, sat, val);
        onPick.accept(0xFF000000 | (rgb & 0xFFFFFF));
    }

    @Override public void onClick(Click click, boolean doubled) { apply(click.x(), click.y()); }
    @Override protected void onDrag(Click click, double dx, double dy) { apply(click.x(), click.y()); }
    @Override protected void appendClickableNarrations(NarrationMessageBuilder builder) {}

    private static float clamp(float v) { return Math.max(0f, Math.min(1f, v)); }

    private static int lerp(int a, int b, float t) {
        int ar = (a >> 16) & 0xFF, ag = (a >> 8) & 0xFF, ab = a & 0xFF;
        int br = (b >> 16) & 0xFF, bg = (b >> 8) & 0xFF, bb = b & 0xFF;
        int r = (int) (ar + (br - ar) * t), g = (int) (ag + (bg - ag) * t), bl = (int) (ab + (bb - ab) * t);
        return 0xFF000000 | (r << 16) | (g << 8) | bl;
    }
}
