package net.squxso.lumina.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;

/**
 * A labelled ON/OFF toggle row.
 *
 * <p>The state is read live every frame through {@link BooleanSupplier}, so the
 * pill always reflects the backing field even if it changes elsewhere (e.g. the
 * auto-fisher being toggled by a keybind). Clicking runs {@code onToggle}.
 */
public class LuminaToggle extends ClickableWidget {

    private final BooleanSupplier state;
    private final Runnable onToggle;

    public LuminaToggle(int x, int y, int w, int h, String label,
                        BooleanSupplier state, Runnable onToggle) {
        super(x, y, w, h, Text.literal(label));
        this.state    = state;
        this.onToggle = onToggle;
    }

    @Override
    protected void renderWidget(DrawContext ctx, int mx, int my, float delta) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hovered = isMouseOver(mx, my);
        boolean on      = state.getAsBoolean();

        ctx.fill(x, y, x + w, y + h, hovered ? LuminaTheme.ROW_HOVER : LuminaTheme.ROW);
        ctx.fill(x, y, x + 2, y + h, on ? LuminaTheme.ON : LuminaTheme.ACCENT_DEEP);
        ctx.fill(x, y, x + w, y + 1, hovered ? LuminaTheme.ACCENT_DIM : 0x22FFFFFF);

        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        // Label on the left.
        ctx.drawTextWithShadow(tr, getMessage(), x + 8, y + (h - 8) / 2,
                hovered ? LuminaTheme.TEXT : LuminaTheme.TEXT_DIM);

        // ON/OFF pill on the right.
        String label = on ? "ON" : "OFF";
        int pillW = tr.getWidth(label) + 12;
        int pillX = x + w - pillW - 6;
        int pillY = y + (h - 12) / 2;
        int color = on ? LuminaTheme.ON : LuminaTheme.OFF;

        ctx.fill(pillX, pillY, pillX + pillW, pillY + 12, (color & 0x00FFFFFF) | 0x33000000);
        ctx.fill(pillX, pillY, pillX + pillW, pillY + 1, color); // top edge
        ctx.drawCenteredTextWithShadow(tr, Text.literal(label),
                pillX + pillW / 2, pillY + 2, color);
    }

    @Override
    public void onClick(Click click, boolean doubled) {
        onToggle.run();
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {}
}
