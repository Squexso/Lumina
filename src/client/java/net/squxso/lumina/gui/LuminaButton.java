package net.squxso.lumina.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.function.BooleanSupplier;
import java.util.function.Supplier;

/**
 * A flat, custom-rendered button for the Lumina client look.
 *
 * <p>Replaces vanilla {@code ButtonWidget} so the whole UI shares one style.
 * It can optionally show an "active" highlight (used for the selected tab) and
 * can render a live label via a supplier (used for the keybind button, whose
 * text changes when a key is captured on another thread).
 */
public class LuminaButton extends ClickableWidget {

    private final Runnable action;
    private final BooleanSupplier active;   // nullable — drives the active highlight
    private final Supplier<Text> liveLabel; // nullable — overrides the static label

    public LuminaButton(int x, int y, int w, int h, String label, Runnable action) {
        this(x, y, w, h, Text.literal(label), action, null, null);
    }

    public LuminaButton(int x, int y, int w, int h, String label, Runnable action, BooleanSupplier active) {
        this(x, y, w, h, Text.literal(label), action, active, null);
    }

    public LuminaButton(int x, int y, int w, int h, Supplier<Text> liveLabel, Runnable action) {
        this(x, y, w, h, Text.empty(), action, null, liveLabel);
    }

    private LuminaButton(int x, int y, int w, int h, Text label, Runnable action,
                         BooleanSupplier active, Supplier<Text> liveLabel) {
        super(x, y, w, h, label);
        this.action    = action;
        this.active    = active;
        this.liveLabel = liveLabel;
    }

    @Override
    protected void renderWidget(DrawContext ctx, int mx, int my, float delta) {
        int x = getX(), y = getY(), w = getWidth(), h = getHeight();
        boolean hovered = isMouseOver(mx, my);
        boolean on      = active != null && active.getAsBoolean();

        int bg = on ? LuminaTheme.ROW_ACTIVE : hovered ? LuminaTheme.ROW_HOVER : LuminaTheme.ROW;
        ctx.fill(x, y, x + w, y + h, bg);

        // Left accent bar — solid when active, faint on hover.
        if (on)            ctx.fill(x, y, x + 2, y + h, LuminaTheme.ACCENT);
        else if (hovered)  ctx.fill(x, y, x + 2, y + h, LuminaTheme.ACCENT_DIM);

        // Top hairline border.
        ctx.fill(x, y, x + w, y + 1, on || hovered ? LuminaTheme.ACCENT_DIM : 0x22FFFFFF);

        int textColor = on ? LuminaTheme.TEXT : hovered ? LuminaTheme.TEXT : LuminaTheme.TEXT_DIM;
        Text label = liveLabel != null ? liveLabel.get() : getMessage();
        ctx.drawCenteredTextWithShadow(
                MinecraftClient.getInstance().textRenderer,
                label, x + w / 2, y + (h - 8) / 2, textColor);
    }

    @Override
    public void onClick(Click click, boolean doubled) {
        action.run();
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {}
}
