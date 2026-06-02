package net.squxso.lumina.feature;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import org.joml.Matrix3x2fStack;

/**
 * Base class for movable, scalable on-screen HUD elements.
 *
 * <p>Subclasses just draw at the origin {@code (0,0)} in {@link #render}; this
 * base applies the configured position + scale via the GUI matrix stack, so the
 * HUD editor can move/resize any element generically.
 */
public abstract class HudFeature extends Feature {

    private final Setting<Integer> posX;
    private final Setting<Integer> posY;
    private final Setting<Integer> scalePct;

    protected HudFeature(String id, String name, String description, int defX, int defY) {
        this(id, name, description, FeatureCategory.HUD, defX, defY);
    }

    protected HudFeature(String id, String name, String description, FeatureCategory category, int defX, int defY) {
        super(id, name, description, category, false);
        posX     = add(Setting.intRange("x", "X position", defX, 0, 8000));
        posY     = add(Setting.intRange("y", "Y position", defY, 0, 8000));
        scalePct = add(Setting.intRange("scale", "Scale %", 100, 50, 300));
    }

    /** Draw the element at the origin (0,0). Position/scale are applied by the base. */
    public abstract void render(GuiGraphics ctx, Minecraft mc);

    /** Unscaled width/height, used by the editor for the drag box. */
    public abstract int width(Minecraft mc);
    public abstract int height(Minecraft mc);

    public int posX() { return posX.asInt(); }
    public int posY() { return posY.asInt(); }
    public float scale() { return scalePct.asInt() / 100f; }
    public void setPos(int x, int y) { posX.set(Math.max(0, x)); posY.set(Math.max(0, y)); }
    public void addScale(int delta) { scalePct.set(Math.max(50, Math.min(300, scalePct.asInt() + delta))); }

    @Override
    public final void onRenderHud(GuiGraphics ctx, Minecraft mc) {
        Matrix3x2fStack m = ctx.pose();
        m.pushMatrix();
        m.translate((float) posX(), (float) posY());
        float s = scale();
        m.scale(s, s);
        render(ctx, mc);
        m.popMatrix();
    }
}
