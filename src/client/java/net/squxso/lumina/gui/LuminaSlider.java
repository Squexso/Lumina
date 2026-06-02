package net.squxso.lumina.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.function.IntConsumer;
import java.util.function.IntSupplier;

/** Generic single-value slider driven by a getter/setter (works for ints and colour channels). */
public final class LuminaSlider extends ClickableWidget {

    private final String label;
    private final int min, max;
    private final IntSupplier getter;
    private final IntConsumer setter;

    public LuminaSlider(int x, int y, int w, int h, String label, int min, int max,
                        IntSupplier getter, IntConsumer setter) {
        super(x, y, w, h, Text.literal(label));
        this.label = label;
        this.min = min;
        this.max = max;
        this.getter = getter;
        this.setter = setter;
    }

    @Override
    protected void renderWidget(DrawContext ctx, int mx, int my, float delta) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hover = isMouseOver(mx, my);
        ctx.fill(x, y, x + w, y + h, hover ? LuminaTheme.ROW_HOVER : LuminaTheme.ROW);

        int ty = y + h - 5;
        ctx.fill(x + 4, ty, x + w - 4, ty + 2, LuminaTheme.TRACK);
        float frac = (float) (getter.getAsInt() - min) / Math.max(1, max - min);
        int hx = x + 4 + (int) ((w - 8) * frac);
        ctx.fill(x + 4, ty, hx, ty + 2, LuminaTheme.ACCENT);
        ctx.fill(hx - 1, ty - 2, hx + 2, ty + 4, LuminaTheme.THUMB);

        ctx.drawTextWithShadow(MinecraftClient.getInstance().textRenderer,
                Text.literal("§7" + label + ": §f" + getter.getAsInt()), x + 5, y + 2, LuminaTheme.TEXT);
    }

    private void apply(double mouseX) {
        float frac = (float) ((mouseX - (getX() + 4)) / (getWidth() - 8));
        frac = Math.max(0f, Math.min(1f, frac));
        setter.accept(min + Math.round(frac * (max - min)));
    }

    @Override public void onClick(Click click, boolean doubled) { apply(click.x()); }
    @Override protected void onDrag(Click click, double dx, double dy) { apply(click.x()); }
    @Override protected void appendClickableNarrations(NarrationMessageBuilder builder) {}
}
