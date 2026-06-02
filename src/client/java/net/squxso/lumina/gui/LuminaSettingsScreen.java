package net.squxso.lumina.gui;

import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.screen.Screen;
import net.minecraft.text.Text;
import net.squxso.lumina.feature.Feature;
import net.squxso.lumina.feature.FeatureManager;

/**
 * Per-feature settings editor (right-click a feature in the panel). Renders a
 * control per {@link Feature.Setting}: toggle, slider (INT), cycle button (ENUM),
 * and a full R/G/B colour picker with live swatch (COLOR).
 */
public final class LuminaSettingsScreen extends Screen {

    private static final int PW = 248, ROW = 22;

    private final Feature feature;
    private final Screen parent;

    public LuminaSettingsScreen(Feature feature, Screen parent) {
        super(Text.literal(feature.name));
        this.feature = feature;
        this.parent = parent;
    }

    private static final int COLOR_H = 74;   // visual picker height (in pixels)

    /** Display rows: COLOR takes COLOR_H pixels (≈ rows), everything else 1 ROW. */
    private int rows() {
        int r = 0;
        for (Feature.Setting<?> s : feature.settings())
            r += (s.kind == Feature.Setting.Kind.COLOR) ? (COLOR_H / ROW + 1) : 1;
        return r;
    }
    private int ph() { return 34 + rows() * ROW + 28; }
    private int px() { return this.width / 2 - PW / 2; }
    private int py() { return this.height / 2 - ph() / 2; }

    @Override
    @SuppressWarnings("unchecked")
    protected void init() {
        clearChildren();
        int px = px(), py = py(), w = PW - 16, y = py + 30;

        for (Feature.Setting<?> s : feature.settings()) {
            switch (s.kind) {
                case TOGGLE -> {
                    Feature.Setting<Boolean> ts = (Feature.Setting<Boolean>) s;
                    addDrawableChild(new LuminaToggleRow(px + 8, y, w, ROW - 4, s.label,
                            ts::asBool,
                            () -> { ts.set(!ts.asBool()); FeatureManager.save(); }));
                    y += ROW;
                }
                case INT -> {
                    Feature.Setting<Integer> is = (Feature.Setting<Integer>) s;
                    addDrawableChild(new LuminaSlider(px + 8, y, w, ROW - 4, s.label, is.min, is.max,
                            is::asInt, v -> { is.set(v); FeatureManager.save(); }));
                    y += ROW;
                }
                case ENUM -> {
                    Feature.Setting<String> es = (Feature.Setting<String>) s;
                    addDrawableChild(new LuminaButton(px + 8, y, w, ROW - 4,
                            () -> Text.literal(s.label + ": §f" + es.asString()),
                            () -> { cycleEnum(es); FeatureManager.save(); }));
                    y += ROW;
                }
                case COLOR -> {
                    Feature.Setting<Integer> cs = (Feature.Setting<Integer>) s;
                    addDrawableChild(new LuminaColorPicker(px + 8, y + 10, w, COLOR_H - 12, cs.asInt(),
                            argb -> { cs.set(argb); FeatureManager.save(); }));
                    y += COLOR_H;
                }
            }
        }

        addDrawableChild(new LuminaButton(px + PW / 2 - 40, py + ph() - 22, 80, 16, "← Back", this::close));
    }

    @Override
    public void renderBackground(DrawContext ctx, int mouseX, int mouseY, float delta) {
        int px = px(), py = py(), ph = ph();
        ctx.fill(px, py, px + PW, py + ph, 0xF01A1228);
        ctx.fill(px, py, px + PW, py + 26, 0xF0241638);
        ctx.fill(px, py, px + PW, py + 1, LuminaTheme.ACCENT);
        ctx.fill(px, py + ph - 1, px + PW, py + ph, 0x44C36BFF);
        ctx.fill(px, py, px + 1, py + ph, 0x44C36BFF);
        ctx.fill(px + PW - 1, py, px + PW, py + ph, 0x44C36BFF);
        ctx.drawTextWithShadow(this.textRenderer, Text.literal("§d" + feature.name + " §8settings"), px + 8, py + 9, 0xFFE3A8FF);

        // Label + current-colour swatch above each colour picker.
        int y = py + 30;
        for (Feature.Setting<?> s : feature.settings()) {
            if (s.kind == Feature.Setting.Kind.COLOR) {
                ctx.drawTextWithShadow(this.textRenderer, Text.literal("§7" + s.label), px + 8, y, 0xFFCFC4EA);
                int sw = px + PW - 8 - 14;
                ctx.fill(sw, y - 1, sw + 14, y + 9, 0xFF000000);
                ctx.fill(sw + 1, y, sw + 13, y + 8, 0xFF000000 | (s.asInt() & 0xFFFFFF));
                y += COLOR_H;
            } else {
                y += ROW;
            }
        }
    }

    private void cycleEnum(Feature.Setting<String> s) {
        if (s.options == null || s.options.length == 0) return;
        int idx = 0;
        for (int i = 0; i < s.options.length; i++) if (s.options[i].equals(s.asString())) { idx = i; break; }
        s.set(s.options[(idx + 1) % s.options.length]);
    }

    @Override public boolean shouldPause() { return false; }
    @Override public void close() { if (this.client != null) this.client.setScreen(parent); }
}
