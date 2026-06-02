package net.squxso.lumina.feature;

import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;

import java.util.ArrayList;
import java.util.List;

/**
 * Base class for every LuminaMC client feature.
 *
 * <p>Designed to be <b>loader-agnostic</b>: this class and its subclasses only
 * touch vanilla Minecraft types, so the exact same code can sit in a shared
 * (Architectury "common") module and be driven by thin Fabric / Forge / NeoForge
 * adapters that forward the engine events ({@link #onClientTick}, {@link #onRenderHud})
 * and persist the {@link FeatureManager} config.
 *
 * <p>Each feature owns its enabled-state and an optional list of typed
 * {@link Setting}s, which the Right-Shift panel renders generically.
 */
public abstract class Feature {

    public final String id;
    public final String name;
    public final String description;
    public final FeatureCategory category;
    private boolean enabled;

    private final List<Setting<?>> settings = new ArrayList<>();

    protected Feature(String id, String name, String description, FeatureCategory category, boolean enabledByDefault) {
        this.id = id;
        this.name = name;
        this.description = description;
        this.category = category;
        this.enabled = enabledByDefault;
    }

    public boolean isEnabled() { return enabled; }

    public void setEnabled(boolean enabled) {
        if (this.enabled == enabled) return;
        this.enabled = enabled;
        if (enabled) onEnabled(); else onDisabled();
    }

    public List<Setting<?>> settings() { return settings; }

    protected <T> Setting<T> add(Setting<T> setting) { settings.add(setting); return setting; }

    // ── lifecycle / engine hooks (override as needed) ─────────────────────

    /** Called once per client tick while enabled. */
    public void onClientTick(MinecraftClient mc) {}

    /** Called every frame while enabled and no full-screen GUI is open. */
    public void onRenderHud(DrawContext ctx, MinecraftClient mc) {}

    protected void onEnabled() {}
    protected void onDisabled() {}

    // ── lightweight typed settings the panel can render ───────────────────

    /** A single adjustable value with a label. Kind drives how the panel shows it. */
    public static final class Setting<T> {
        public enum Kind { TOGGLE, INT, COLOR, ENUM }
        public final String key, label;
        public final Kind kind;
        public final int min, max;          // for INT
        public final String[] options;      // for ENUM
        private T value;

        private Setting(String key, String label, Kind kind, T value, int min, int max, String[] options) {
            this.key = key; this.label = label; this.kind = kind;
            this.value = value; this.min = min; this.max = max; this.options = options;
        }
        public static Setting<Boolean> toggle(String key, String label, boolean v) {
            return new Setting<>(key, label, Kind.TOGGLE, v, 0, 0, null);
        }
        public static Setting<Integer> intRange(String key, String label, int v, int min, int max) {
            return new Setting<>(key, label, Kind.INT, v, min, max, null);
        }
        public static Setting<Integer> color(String key, String label, int argb) {
            return new Setting<>(key, label, Kind.COLOR, argb, 0, 0, null);
        }
        public static Setting<String> enumValue(String key, String label, String v, String... options) {
            return new Setting<>(key, label, Kind.ENUM, v, 0, 0, options);
        }
        @SuppressWarnings("unchecked")
        public T get() { return value; }
        public void set(T v) { this.value = v; }
        public int asInt() { return value instanceof Integer i ? i : 0; }
        public boolean asBool() { return value instanceof Boolean b && b; }
        public String asString() { return String.valueOf(value); }
    }
}
