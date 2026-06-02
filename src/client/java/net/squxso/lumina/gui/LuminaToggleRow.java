package net.squxso.lumina.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;

/**
 * A feature row in the Lumina panel: the feature name on the left and a clean
 * sliding ON/OFF switch on the right. Replaces the old centred "● dot" look so
 * the whole panel reads like a modern settings list.
 */
public class LuminaToggleRow extends ClickableWidget {

    private final String label;
    private final BooleanSupplier state;
    private final Runnable onToggle;

    public LuminaToggleRow(int x, int y, int w, int h, String label,
                           BooleanSupplier state, Runnable onToggle) {
        super(x, y, w, h, Text.literal(label));
        this.label = label;
        this.state = state;
        this.onToggle = onToggle;
    }

    @Override
    protected void renderWidget(DrawContext ctx, int mx, int my, float delta) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hovered = isMouseOver(mx, my);
        boolean on = state.getAsBoolean();

        // Row surface + accent cues.
        ctx.fill(x, y, x + w, y + h, hovered ? LuminaTheme.ROW_HOVER : LuminaTheme.ROW);
        if (on)           ctx.fill(x, y, x + 2, y + h, LuminaTheme.ACCENT);
        else if (hovered) ctx.fill(x, y, x + 2, y + h, LuminaTheme.ACCENT_DIM);
        ctx.fill(x, y, x + w, y + 1, (hovered || on) ? LuminaTheme.ACCENT_DIM : 0x18FFFFFF);

        var tr = MinecraftClient.getInstance().textRenderer;
        ctx.drawTextWithShadow(tr, Text.literal(label), x + 7, y + (h - 8) / 2 + 1,
                on ? LuminaTheme.TEXT : LuminaTheme.TEXT_DIM);

        // Sliding switch on the right.
        int tw = 18, th = 10;
        int tx = x + w - tw - 6;
        int ty = y + (h - th) / 2;
        ctx.fill(tx, ty, tx + tw, ty + th, on ? LuminaTheme.ACCENT_DIM : LuminaTheme.TRACK);
        ctx.fill(tx, ty, tx + tw, ty + 1, 0x22FFFFFF);                 // faux-rounded top sheen
        ctx.fill(tx, ty + th - 1, tx + tw, ty + th, 0x33000000);       // bottom shade

        int knob = 8;
        int kx = on ? tx + tw - knob - 1 : tx + 1;
        int ky = ty + (th - knob) / 2;
        ctx.fill(kx, ky, kx + knob, ky + knob, on ? LuminaTheme.THUMB_HOT : LuminaTheme.TEXT_MUTED);
    }

    @Override
    public void onClick(Click click, boolean doubled) {
        onToggle.run();
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {}
}
