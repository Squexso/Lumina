package net.squxso.lumina.feature;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiGraphics;
import net.squxso.lumina.feature.impl.*;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

/**
 * Central registry + persistence for all LuminaMC features.
 *
 * <p>The loader adapter calls {@link #load()} on start, forwards {@link #onClientTick}
 * and {@link #renderHud} from the engine, and calls {@link #save()} on stop. Because
 * everything here is vanilla-only (Mojmap), the same manager works on every loader.
 *
 * <p>Config lives in {@code config/lumina.json}.
 */
public final class FeatureManager {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG = "config/lumina.json";

    private static final List<Feature> FEATURES = new ArrayList<>();
    private static boolean initialised = false;

    private FeatureManager() {}

    /** Registers all built-in features (idempotent). Grown as features are ported. */
    public static synchronized void init() {
        if (initialised) return;
        initialised = true;
        FEATURES.add(new FpsHudFeature());
        FEATURES.add(new CoordinatesHudFeature());
        FEATURES.add(new DirectionHudFeature());
        FEATURES.add(new PingHudFeature());
        FEATURES.add(new KeystrokesHudFeature());
        FEATURES.add(new CpsHudFeature());
        FEATURES.add(new ArmorPotionHudFeature());
        // Combat tab — fair, informational/timing aids (no automation).
        FEATURES.add(new AimAssistFeature());
        FEATURES.add(new TargetInfoFeature());
        FEATURES.add(new ReachHudFeature());
        FEATURES.add(new AttackCooldownFeature());
        // Movement tab — fair quality-of-life (no fly/scaffold/packet tricks).
        FEATURES.add(new ToggleSprintFeature());
        FEATURES.add(new SpeedHudFeature());
        // Mining tab — fair info (no X-ray / block hiding).
        FEATURES.add(new DepthHudFeature());
    }

    public static List<Feature> all() {
        init();
        return FEATURES;
    }

    public static List<Feature> byCategory(FeatureCategory cat) {
        return all().stream().filter(f -> f.category == cat).toList();
    }

    /** Whether a feature is currently enabled (used by mixins/HUD). Safe before load. */
    public static boolean isEnabled(String id) {
        for (Feature f : all()) if (f.id.equals(id)) return f.isEnabled();
        return false;
    }

    public static Feature get(String id) {
        for (Feature f : all()) if (f.id.equals(id)) return f;
        return null;
    }

    // ── engine event forwarding ───────────────────────────────────────────

    public static void onClientTick(Minecraft mc) {
        if (mc.player == null) return;
        for (Feature f : all()) if (f.isEnabled()) {
            try { f.onClientTick(mc); } catch (Exception ignored) {}
        }
    }

    public static void renderHud(GuiGraphics ctx, Minecraft mc) {
        if (mc.player == null || mc.screen != null) return;
        if (mc.options != null && mc.options.hideGui) return;
        for (Feature f : all()) if (f.isEnabled()) {
            try { f.onRenderHud(ctx, mc); } catch (Exception ignored) {}
        }
    }

    // ── persistence ────────────────────────────────────────────────────────

    @SuppressWarnings("unchecked")
    public static void load() {
        init();
        try {
            Path path = path();
            if (!Files.exists(path)) return;
            try (Reader r = Files.newBufferedReader(path)) {
                JsonObject root = GSON.fromJson(r, JsonObject.class);
                if (root == null) return;
                JsonObject feats = root.has("features") ? root.getAsJsonObject("features") : null;
                JsonObject sets  = root.has("settings") ? root.getAsJsonObject("settings") : null;
                for (Feature f : FEATURES) {
                    if (feats != null && feats.has(f.id)) f.setEnabled(feats.get(f.id).getAsBoolean());
                    if (sets != null && sets.has(f.id)) applySettings(f, sets.getAsJsonObject(f.id));
                }
            }
        } catch (Exception ignored) {
            // corrupt/missing → defaults stand
        }
    }

    public static void save() {
        try {
            Path path = path();
            Files.createDirectories(path.getParent());
            JsonObject root = new JsonObject();
            JsonObject feats = new JsonObject();
            JsonObject sets  = new JsonObject();
            for (Feature f : FEATURES) {
                feats.addProperty(f.id, f.isEnabled());
                if (!f.settings().isEmpty()) {
                    JsonObject s = new JsonObject();
                    for (Feature.Setting<?> setting : f.settings()) writeSetting(s, setting);
                    sets.add(f.id, s);
                }
            }
            root.add("features", feats);
            root.add("settings", sets);
            try (Writer w = Files.newBufferedWriter(path)) { GSON.toJson(root, w); }
        } catch (Exception ignored) {}
    }

    @SuppressWarnings("unchecked")
    private static void applySettings(Feature f, JsonObject obj) {
        for (Feature.Setting<?> s : f.settings()) {
            if (!obj.has(s.key)) continue;
            try {
                switch (s.kind) {
                    case TOGGLE -> ((Feature.Setting<Boolean>) s).set(obj.get(s.key).getAsBoolean());
                    case INT, COLOR -> ((Feature.Setting<Integer>) s).set(obj.get(s.key).getAsInt());
                    case ENUM -> ((Feature.Setting<String>) s).set(obj.get(s.key).getAsString());
                }
            } catch (Exception ignored) {}
        }
    }

    private static void writeSetting(JsonObject obj, Feature.Setting<?> s) {
        switch (s.kind) {
            case TOGGLE -> obj.addProperty(s.key, s.asBool());
            case INT, COLOR -> obj.addProperty(s.key, s.asInt());
            case ENUM -> obj.addProperty(s.key, s.asString());
        }
    }

    private static Path path() {
        return Minecraft.getInstance().gameDirectory.toPath().resolve(CONFIG);
    }
}
