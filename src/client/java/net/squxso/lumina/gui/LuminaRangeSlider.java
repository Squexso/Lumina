package net.squxso.lumina.gui;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.Click;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.narration.NarrationMessageBuilder;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.text.Text;

import java.util.function.BiConsumer;

/**
 * A dual-handle slider that picks a MIN/MAX range (used for the random fishing
 * delay). Both handles are draggable and the filled band between them shows the
 * selected window. Values snap to {@link #STEP}ms for tidy numbers.
 *
 * <p><b>Why this is a widget and not hand-rolled in the screen:</b> in MC 1.21.6+
 * the mouse callbacks changed to {@code mouseClicked(Click, boolean)} /
 * {@code mouseDragged(Click, ...)}. {@link ClickableWidget} already implements
 * those and forwards to the {@code onClick}/{@code onDrag}/{@code onRelease} hooks
 * we override below, and the parent {@code Screen} keeps routing drag events to
 * the focused widget even when the cursor leaves its bounds. Doing it as a child
 * widget is what makes dragging actually work.
 */
public class LuminaRangeSlider extends ClickableWidget {

    private static final int PAD   = 8;  // horizontal inset of the track inside the widget
    private static final int STEP  = 10; // value granularity in ms
    private static final int GAP   = 50; // minimum distance kept between min and max

    private final int absMin, absMax;
    private int valMin, valMax;
    private final BiConsumer<Integer, Integer> onChange;

    /** Which handle the current drag is moving: -1 none, 0 min, 1 max. */
    private int dragging = -1;

    public LuminaRangeSlider(int x, int y, int w, int h,
                             int initMin, int initMax, int absMin, int absMax,
                             BiConsumer<Integer, Integer> onChange) {
        super(x, y, w, h, Text.empty());
        this.absMin   = absMin;
        this.absMax   = absMax;
        // Initialize valMax first so clampMin can reference it correctly.
        this.valMax   = Math.min(absMax, Math.max(initMax, absMin + GAP));
        this.valMin   = Math.max(absMin, Math.min(initMin, this.valMax - GAP));
        this.onChange = onChange;
    }

    // ── Geometry helpers ─────────────────────────────────────────────────
    private int trackLeft()  { return getX() + PAD; }
    private int trackWidth() { return getWidth() - 2 * PAD; }

    /** Value → pixel X of its handle centre. */
    private int handleX(int value) {
        double pct = (double) (value - absMin) / (absMax - absMin);
        return trackLeft() + (int) Math.round(pct * trackWidth());
    }

    /** Pixel X → snapped value within [absMin, absMax]. */
    private int valueAt(double mouseX) {
        double pct = (mouseX - trackLeft()) / trackWidth();
        pct = Math.max(0.0, Math.min(1.0, pct));
        int raw = absMin + (int) Math.round(pct * (absMax - absMin));
        return Math.round((float) raw / STEP) * STEP;
    }

    private int clampMin(int v) { return Math.max(absMin, Math.min(v, valMax - GAP)); }
    private int clampMax(int v) { return Math.min(absMax, Math.max(v, valMin + GAP)); }

    // ── Rendering ────────────────────────────────────────────────────────
    @Override
    protected void renderWidget(DrawContext ctx, int mx, int my, float delta) {
        int mid = getY() + getHeight() - 7;          // track sits near the bottom; bubbles ride above
        int left = trackLeft(), right = trackLeft() + trackWidth();
        int xMin = handleX(valMin), xMax = handleX(valMax);
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;

        // Track groove (recessed look: dark fill + faint top hairline).
        ctx.fill(left, mid - 2, right, mid + 2, LuminaTheme.TRACK);
        ctx.fill(left, mid - 2, right, mid - 1, 0x33000000);

        // Tick marks across the rail.
        for (int i = 0; i <= 10; i++) {
            int tx = left + (int) ((double) i / 10 * trackWidth());
            ctx.fill(tx, mid - 4, tx + 1, mid + 4, 0x33C9A6FF);
        }

        // Selected band as a gradient between the two handles.
        ctx.fillGradient(xMin, mid - 2, xMax, mid + 2, LuminaTheme.ACCENT2, LuminaTheme.ACCENT);

        // Handles + value bubbles riding above each one.
        boolean hotMin = dragging == 0 || hovering(mx, my, xMin, mid);
        boolean hotMax = dragging == 1 || hovering(mx, my, xMax, mid);
        drawHandle(ctx, xMin, mid, hotMin);
        drawHandle(ctx, xMax, mid, hotMax);
        drawBubble(ctx, tr, xMin, getY() - 1, valMin, hotMin);
        drawBubble(ctx, tr, xMax, getY() - 1, valMax, hotMax);
    }

    private void drawHandle(DrawContext ctx, int hx, int mid, boolean hot) {
        int c = hot ? LuminaTheme.THUMB_HOT : LuminaTheme.THUMB;
        ctx.fill(hx - 3, mid - 7, hx + 3, mid + 7, c);                 // body
        ctx.fill(hx - 4, mid - 8, hx + 4, mid - 7, LuminaTheme.ACCENT); // top cap glow
        ctx.fill(hx - 4, mid + 7, hx + 4, mid + 8, LuminaTheme.ACCENT_DIM);
    }

    /** Draws a small rounded value label centred over a handle, clamped on-screen. */
    private void drawBubble(DrawContext ctx, TextRenderer tr, int hx, int top, int value, boolean hot) {
        String s = value + "ms";
        int w = tr.getWidth(s) + 8;
        int bx = Math.max(getX(), Math.min(hx - w / 2, getX() + getWidth() - w));
        ctx.fill(bx, top, bx + w, top + 12, hot ? LuminaTheme.ROW_ACTIVE : LuminaTheme.ROW_HOVER);
        ctx.fill(bx, top, bx + w, top + 1, hot ? LuminaTheme.ACCENT_HOT : LuminaTheme.ACCENT);
        ctx.drawCenteredTextWithShadow(tr, Text.literal(s), bx + w / 2, top + 2,
                hot ? LuminaTheme.TEXT : LuminaTheme.TEXT_DIM);
    }

    private boolean hovering(int mx, int my, int hx, int mid) {
        return Math.abs(mx - hx) <= 5 && Math.abs(my - mid) <= 8;
    }

    // ── Input ────────────────────────────────────────────────────────────
    @Override
    public void onClick(Click click, boolean doubled) {
        // Pick whichever handle is closer to the click, then move it there.
        int distMin = Math.abs((int) click.x() - handleX(valMin));
        int distMax = Math.abs((int) click.x() - handleX(valMax));
        dragging = distMin <= distMax ? 0 : 1;
        moveActiveHandle(click.x());
    }

    @Override
    protected void onDrag(Click click, double deltaX, double deltaY) {
        moveActiveHandle(click.x());
    }

    @Override
    public void onRelease(Click click) {
        dragging = -1;
    }

    private void moveActiveHandle(double mouseX) {
        int v = valueAt(mouseX);
        if (dragging == 0)      valMin = clampMin(v);
        else if (dragging == 1) valMax = clampMax(v);
        else return;
        onChange.accept(valMin, valMax);
    }

    @Override
    protected void appendClickableNarrations(NarrationMessageBuilder builder) {}
}
