package net.squxso.lumina.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.squxso.lumina.feature.Feature;
import net.squxso.lumina.feature.FeatureManager;
import net.squxso.lumina.feature.HudFeature;

import java.util.ArrayList;
import java.util.List;

/**
 * Free-placement HUD editor. Shows every enabled HUD element with a draggable
 * box; drag to move, scroll over an element to resize it. Esc saves and exits.
 * The main control panel stays fixed — this is a separate fullscreen editor.
 */
public final class LuminaHudEditor extends Screen {

    private HudFeature dragging;
    private double dragOffX, dragOffY;

    public LuminaHudEditor() { super(Text.literal("LuminaMC HUD Editor")); }

    private List<HudFeature> huds() {
        List<HudFeature> l = new ArrayList<>();
        for (Feature f : FeatureManager.all()) if (f instanceof HudFeature h && f.isEnabled()) l.add(h);
        return l;
    }

    @Override
    public void render(DrawContext ctx, int mouseX, int mouseY, float delta) {
        ctx.fill(0, 0, this.width, this.height, 0x99000000);     // dim
        super.render(ctx, mouseX, mouseY, delta);
        MinecraftClient mc = this.client;

        for (HudFeature h : huds()) {
            h.onRenderHud(ctx, mc);                              // draw the element itself
            int w = (int) (h.width(mc) * h.scale());
            int ht = (int) (h.height(mc) * h.scale());
            int x = h.posX(), y = h.posY();
            int border = (h == dragging) ? 0xFFE3A8FF : 0x99C36BFF;
            ctx.fill(x - 1, y - 1, x + w + 1, y, border);
            ctx.fill(x - 1, y + ht, x + w + 1, y + ht + 1, border);
            ctx.fill(x - 1, y - 1, x, y + ht + 1, border);
            ctx.fill(x + w, y - 1, x + w + 1, y + ht + 1, border);
            ctx.drawTextWithShadow(this.textRenderer,
                    Text.literal("§7" + h.name + " §8" + (int) (h.scale() * 100) + "%"), x, y - 10, 0xFFFFFFFF);
        }

        ctx.drawCenteredTextWithShadow(this.textRenderer,
                Text.literal("§d§lHUD Editor §r§7— drag to move · scroll to resize · §fEsc§7 to save"),
                this.width / 2, 8, 0xFFFFFFFF);
        if (huds().isEmpty())
            ctx.drawCenteredTextWithShadow(this.textRenderer,
                    Text.literal("§8Enable HUD elements in the panel (RShift) first"),
                    this.width / 2, this.height / 2, 0xFF888888);
    }

    private HudFeature at(double mx, double my) {
        MinecraftClient mc = this.client;
        for (HudFeature h : huds()) {
            int w = (int) (h.width(mc) * h.scale()), ht = (int) (h.height(mc) * h.scale());
            if (mx >= h.posX() - 1 && mx <= h.posX() + w + 1 && my >= h.posY() - 1 && my <= h.posY() + ht + 1) return h;
        }
        return null;
    }

    @Override
    public boolean mouseClicked(Click click, boolean doubled) {
        HudFeature h = at(click.x(), click.y());
        if (h != null) {
            dragging = h;
            dragOffX = click.x() - h.posX();
            dragOffY = click.y() - h.posY();
            return true;
        }
        return super.mouseClicked(click, doubled);
    }

    @Override
    public boolean mouseDragged(Click click, double deltaX, double deltaY) {
        if (dragging != null) {
            dragging.setPos((int) Math.round(click.x() - dragOffX), (int) Math.round(click.y() - dragOffY));
            return true;
        }
        return super.mouseDragged(click, deltaX, deltaY);
    }

    @Override
    public boolean mouseReleased(Click click) {
        if (dragging != null) { dragging = null; FeatureManager.save(); return true; }
        return super.mouseReleased(click);
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontal, double vertical) {
        HudFeature h = at(mouseX, mouseY);
        if (h != null) {
            h.addScale(vertical > 0 ? 10 : -10);
            FeatureManager.save();
            return true;
        }
        return super.mouseScrolled(mouseX, mouseY, horizontal, vertical);
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void close() { FeatureManager.save(); super.close(); }
}
