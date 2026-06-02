package net.squxso.lumina.feature.impl;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import net.squxso.lumina.feature.Feature;
import net.squxso.lumina.feature.FeatureCategory;

/**
 * Custom crosshair drawn at screen centre. The vanilla crosshair is hidden by
 * {@code LuminaCrosshairMixin} while this feature is on, so they never overlap.
 */
public final class CrosshairFeature extends Feature {

    private final Setting<String>  shape   = add(Setting.enumValue("shape", "Shape", "CROSS", "CROSS", "DOT", "CROSS+DOT", "CIRCLE", "T"));
    private final Setting<Integer> size    = add(Setting.intRange("size", "Length", 6, 1, 20));
    private final Setting<Integer> gap      = add(Setting.intRange("gap", "Gap", 3, 0, 12));
    private final Setting<Integer> thick    = add(Setting.intRange("thickness", "Thickness", 2, 1, 5));
    private final Setting<Integer> dotSize  = add(Setting.intRange("dot", "Dot size", 2, 1, 8));
    private final Setting<Integer> color    = add(Setting.color("color", "Color", 0xFFFFFFFF));
    private final Setting<Boolean> outline  = add(Setting.toggle("outline", "Black outline", true));

    public CrosshairFeature() {
        super("crosshair", "Custom Crosshair", "Replace the vanilla crosshair with your own shape, size and color.",
                FeatureCategory.VISUAL, false);
    }

    @Override
    public void onRenderHud(DrawContext ctx, MinecraftClient mc) {
        int cx = mc.getWindow().getScaledWidth() / 2;
        int cy = mc.getWindow().getScaledHeight() / 2;
        int s = size.asInt(), g = gap.asInt(), t = thick.asInt(), c = color.asInt();
        int h = t / 2;                       // half-thickness for centering
        boolean ol = outline.asBool();
        String sh = shape.asString();

        boolean cross = sh.equals("CROSS") || sh.equals("CROSS+DOT") || sh.equals("T");
        if (cross) {
            // left + right arms
            seg(ctx, cx - g - s, cy - h, cx - g,     cy - h + t, c, ol);
            seg(ctx, cx + g,     cy - h, cx + g + s, cy - h + t, c, ol);
            // top arm (skip for T)
            if (!sh.equals("T")) seg(ctx, cx - h, cy - g - s, cx - h + t, cy - g, c, ol);
            // bottom arm
            seg(ctx, cx - h, cy + g, cx - h + t, cy + g + s, c, ol);
        }
        if (sh.equals("CIRCLE")) {
            int r = s;
            seg(ctx, cx - r, cy - r, cx + r, cy - r + t, c, ol);   // top
            seg(ctx, cx - r, cy + r - t, cx + r, cy + r, c, ol);   // bottom
            seg(ctx, cx - r, cy - r, cx - r + t, cy + r, c, ol);   // left
            seg(ctx, cx + r - t, cy - r, cx + r, cy + r, c, ol);   // right
        }
        if (sh.equals("DOT") || sh.equals("CROSS+DOT")) {
            int d = dotSize.asInt(), dh = d / 2;
            seg(ctx, cx - dh, cy - dh, cx - dh + d, cy - dh + d, c, ol);
        }
    }

    /** Draws a rect, with an optional 1px black outline for contrast. */
    private static void seg(DrawContext ctx, int x1, int y1, int x2, int y2, int color, boolean outline) {
        if (outline) ctx.fill(x1 - 1, y1 - 1, x2 + 1, y2 + 1, 0xFF000000);
        ctx.fill(x1, y1, x2, y2, color);
    }
}
