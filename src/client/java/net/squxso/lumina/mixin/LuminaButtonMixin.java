package net.squxso.lumina.mixin;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.font.TextRenderer;
import net.minecraft.client.gui.DrawContext;
import net.minecraft.client.gui.widget.ClickableWidget;
import net.minecraft.client.gui.widget.PressableWidget;
import net.squxso.lumina.gui.LuminaTheme;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

/**
 * Re-skins <em>every</em> vanilla push button (anything extending
 * {@link PressableWidget} — the Done/Back/Options buttons, cycling option
 * toggles, server-list buttons, …) in the Lumina violet style, so vanilla
 * screens stop looking like grey stock Minecraft and match the rest of the
 * client's theme.
 *
 * <p>Lumina's own widgets extend {@code ClickableWidget} directly (not
 * {@code PressableWidget}), so the custom panels keep their bespoke look — this
 * only touches stock buttons.
 *
 * <p>{@code require = 0}: if the render signature changes between MC builds the
 * mixin quietly does nothing rather than crashing the game.
 */
@Mixin(PressableWidget.class)
public abstract class LuminaButtonMixin {

    @Inject(method = "renderWidget", at = @At("HEAD"), cancellable = true, require = 0)
    private void lumina$renderButton(DrawContext ctx, int mouseX, int mouseY, float delta, CallbackInfo ci) {
        if (!net.squxso.lumina.feature.FeatureManager.isEnabled("ui_theme")) return; // plain vanilla
        ClickableWidget self = (ClickableWidget) (Object) this;
        if (!self.visible) return;

        int x = self.getX(), y = self.getY(), w = self.getWidth(), h = self.getHeight();

        // Skip icon-only buttons (Language globe, Accessibility, etc.): they're
        // small/square and their long message text would overflow and overlap the
        // neighbouring buttons. Let vanilla draw those as plain icons.
        if (w < 40 || w <= h + 4) return;
        boolean hovered = self.isHovered();
        boolean enabled = self.active;

        // Body gradient.
        int top, bottom;
        if (!enabled)     { top = LuminaTheme.ROW;        bottom = LuminaTheme.PANEL_DEEP; }
        else if (hovered) { top = LuminaTheme.ROW_HOVER;  bottom = LuminaTheme.ROW_ACTIVE; }
        else              { top = LuminaTheme.ROW;        bottom = LuminaTheme.PANEL; }
        ctx.fillGradient(x, y, x + w, y + h, top, bottom);

        // 1px accent frame.
        int border = !enabled ? LuminaTheme.ACCENT_DEEP
                : hovered ? LuminaTheme.ACCENT : LuminaTheme.ACCENT_DIM;
        ctx.fill(x, y, x + w, y + 1, border);
        ctx.fill(x, y + h - 1, x + w, y + h, border);
        ctx.fill(x, y, x + 1, y + h, border);
        ctx.fill(x + w - 1, y, x + w, y + h, border);

        // Centred label.
        TextRenderer tr = MinecraftClient.getInstance().textRenderer;
        int color = enabled ? LuminaTheme.TEXT : LuminaTheme.TEXT_MUTED;
        ctx.drawCenteredTextWithShadow(tr, self.getMessage(), x + w / 2, y + (h - 8) / 2, color);

        ci.cancel(); // skip the vanilla button texture
    }
}
