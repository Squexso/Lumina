package com.luminamc.features;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * The complete per-instance "built-in client features" model from the spec.
 * Stored inside each instance's {@code instance.json} and exported to the
 * Lumina mod's {@code config/lumina.json} via {@link #toLuminaConfig()} when an
 * instance launches, so the in-game mod renders exactly what the user toggled.
 */
public final class FeatureSettings {

    // ── FPS & performance ────────────────────────────────────────────────
    public boolean fpsBoost          = false;
    public boolean smoothFrameLimiter = false;
    public int     frameLimit        = 240;
    public boolean ramOptimizer      = true;  // controls G1GC JVM flag block
    /** Auto-install a trusted performance mod (Sodium/Embeddium) for the loader. */
    public boolean performanceMod    = true;  // recommended on — big FPS win

    // ── PVP ──────────────────────────────────────────────────────────────
    public boolean customCrosshair   = false;
    public Crosshair crosshair       = new Crosshair();
    public boolean hitColorEnabled   = false;
    public String  hitColor          = "#FF0000";
    public boolean reachDisplay      = false;
    public boolean fullbright        = false;
    public boolean cleanUiHideXp     = false;
    public boolean cleanUiHideFood   = false;
    public boolean toggleSprint      = false;
    public boolean blockHitAnimation = false;

    // ── HUD widgets (draggable, toggleable) ──────────────────────────────
    public List<HudElement> hud = new ArrayList<>();

    // ── Overlay menu ─────────────────────────────────────────────────────
    /** Key that opens the in-game LuminaMC overlay; default RIGHT SHIFT. */
    public String overlayKey = "RIGHT_SHIFT";

    public FeatureSettings() {}

    /** Builds the default feature set with every HUD widget defined but off. */
    public static FeatureSettings defaults() {
        FeatureSettings f = new FeatureSettings();
        f.hud.add(new HudElement("fps",        "FPS Counter",        false, 6,   6));
        f.hud.add(new HudElement("cps",        "CPS Counter",        false, 6,   22));
        f.hud.add(new HudElement("coords",     "Coordinates",        false, 6,   38));
        f.hud.add(new HudElement("direction",  "Direction (N/S/E/W)",false, 6,   54));
        f.hud.add(new HudElement("ping",       "Ping",               false, 6,   70));
        f.hud.add(new HudElement("keystrokes", "Keystrokes (WASD)",  false, 6,   90));
        f.hud.add(new HudElement("armor",      "Armor Status",       false, 6,   160));
        f.hud.add(new HudElement("potions",    "Potion Effects",     false, 6,   200));
        return f;
    }

    public HudElement hud(String id) {
        for (HudElement e : hud) if (e.id.equals(id)) return e;
        return null;
    }

    /**
     * Flattens this model into the key/value structure the Lumina Fabric mod
     * reads from {@code config/lumina.json}. Known mod keys map directly; the
     * full feature graph is also written under {@code luminamc} so newer mod
     * builds can consume widgets the current build does not yet understand.
     */
    public Map<String, Object> toLuminaConfig() {
        Map<String, Object> m = new LinkedHashMap<>();

        // Keys the current mod's LuminaConfig.Data understands today:
        m.put("fpsBoost", fpsBoost);
        m.put("showHud", hud.stream().anyMatch(h -> h.enabled));
        HudElement main = hud.isEmpty() ? null : hud.get(0);
        m.put("hudX", main != null ? main.x : 6);
        m.put("hudY", main != null ? main.y : 6);

        // Forward-compatible full graph for richer mod builds:
        Map<String, Object> ext = new LinkedHashMap<>();
        ext.put("smoothFrameLimiter", smoothFrameLimiter);
        ext.put("frameLimit", frameLimit);
        ext.put("customCrosshair", customCrosshair);
        ext.put("crosshairShape", crosshair.shape.name());
        ext.put("crosshairColor", crosshair.color);
        ext.put("crosshairSize", crosshair.size);
        ext.put("crosshairGap", crosshair.gap);
        ext.put("crosshairThickness", crosshair.thickness);
        ext.put("hitColorEnabled", hitColorEnabled);
        ext.put("hitColor", hitColor);
        ext.put("reachDisplay", reachDisplay);
        ext.put("fullbright", fullbright);
        ext.put("cleanUiHideXp", cleanUiHideXp);
        ext.put("cleanUiHideFood", cleanUiHideFood);
        ext.put("toggleSprint", toggleSprint);
        ext.put("blockHitAnimation", blockHitAnimation);
        ext.put("overlayKey", overlayKey);

        Map<String, Object> widgets = new LinkedHashMap<>();
        for (HudElement e : hud) {
            Map<String, Object> w = new LinkedHashMap<>();
            w.put("enabled", e.enabled);
            w.put("x", e.x);
            w.put("y", e.y);
            widgets.put(e.id, w);
        }
        ext.put("hud", widgets);
        m.put("luminamc", ext);
        return m;
    }
}
