package net.squxso.lumina.feature;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import net.minecraft.client.Minecraft;
import net.minecraft.client.Options;
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

    // Equipped accessory the launcher wrote into config/lumina.json (wings/halo/aura).
    private static String accessoryType;            // null when none equipped
    private static int accessoryColor = 0xFFFFFFFF; // ARGB tint

    /** The equipped accessory type ("wings"/"halo"/"aura"), or {@code null}. */
    public static String accessoryType() { return accessoryType; }
    /** The equipped accessory tint colour (ARGB). */
    public static int accessoryColor() { return accessoryColor; }

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
        FEATURES.add(new ClockHudFeature());
        FEATURES.add(new DayTimeHudFeature());
        FEATURES.add(new BiomeHudFeature());
        FEATURES.add(new LightLevelHudFeature());
        FEATURES.add(new NetherCoordsHudFeature());
        FEATURES.add(new ZoomFeature());
        FEATURES.add(new BrightnessFeature());
        FEATURES.add(new FullbrightFeature());
        FEATURES.add(new NoBobbingFeature());
        FEATURES.add(new ChatTimestampsFeature());
        // Cosmetics — launcher-driven, hidden from the panel tabs (see COSMETIC category).
        FEATURES.add(new CapeCosmeticFeature());
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
        FEATURES.add(new TextHudFeature("mining_coords", "Mining Coords", "Compact X / Y / Z for branch mining.", FeatureCategory.MINING, 6, 150, MiningTexts::coords));
        FEATURES.add(new TextHudFeature("bedrock_dist", "Bedrock Distance", "Blocks down to bedrock (Y -64).", FeatureCategory.MINING, 6, 162, MiningTexts::bedrock));
        FEATURES.add(new TextHudFeature("ore_sheet", "Ore Cheat Sheet", "Best Y-levels for every ore.", FeatureCategory.MINING, 6, 174, MiningTexts::oreSheet));
        FEATURES.add(new TextHudFeature("lava_warn", "Lava Warning", "Warns at lava-pool depth.", FeatureCategory.MINING, 6, 186, MiningTexts::lava));
        FEATURES.add(new TextHudFeature("tool_dura", "Tool Durability", "Main-hand tool durability + low warning.", FeatureCategory.MINING, 6, 198, MiningTexts::tool));
        FEATURES.add(new TextHudFeature("block_info", "Block Info", "Name of the block you're looking at.", FeatureCategory.MINING, 6, 210, MiningTexts::block));
        FEATURES.add(new TextHudFeature("inv_full", "Inventory Warning", "Warns when your inventory is (nearly) full.", FeatureCategory.MINING, 6, 222, MiningTexts::inventory));
        FEATURES.add(new TextHudFeature("spawn_warn", "Spawn Warning", "Warns when light is low enough for mobs.", FeatureCategory.MINING, 6, 234, MiningTexts::spawn));
        FEATURES.add(new TextHudFeature("tunnel_align", "Tunnel Aligner", "Facing + angle to dig straight tunnels.", FeatureCategory.MINING, 6, 246, MiningTexts::tunnel));

        // Misc tab — fair client tweaks via vanilla options (saved/restored on toggle).
        FEATURES.add(new DoubleOptionFeature("no_hurtcam", "No Hurt Camera", "No camera tilt when you take damage.", FeatureCategory.MISC, Options::damageTiltStrength, 0.0));
        FEATURES.add(new DoubleOptionFeature("no_nausea", "No Nausea", "Remove the nausea / portal screen warp.", FeatureCategory.MISC, Options::screenEffectScale, 0.0));
        FEATURES.add(new DoubleOptionFeature("stable_fov", "Stable FOV", "FOV stays put during speed effects.", FeatureCategory.MISC, Options::fovEffectScale, 0.0));
        FEATURES.add(new BoolOptionFeature("no_vignette", "No Vignette", "Remove the dark screen-edge vignette.", FeatureCategory.MISC, Options::vignette, false));
        FEATURES.add(new BoolOptionFeature("no_shadows", "No Entity Shadows", "Hide the round entity shadows.", FeatureCategory.MISC, Options::entityShadows, false));
        FEATURES.add(new BoolOptionFeature("no_autojump", "Auto-Jump Off", "Disable auto-jumping over blocks.", FeatureCategory.MISC, Options::autoJump, false));
        FEATURES.add(new BoolOptionFeature("no_lightning_flash", "No Lightning Flash", "Skip the bright lightning flash.", FeatureCategory.MISC, Options::hideLightningFlash, true));

        // Chat tab — fair chat tweaks.
        FEATURES.add(new DoubleOptionFeature("no_chat_bg", "No Chat Background", "Make the chat background transparent.", FeatureCategory.CHAT, Options::textBackgroundOpacity, 0.0));
        FEATURES.add(new DoubleOptionFeature("compact_chat", "Compact Chat", "Tighter chat line spacing.", FeatureCategory.CHAT, Options::chatLineSpacing, 0.0));
        FEATURES.add(new DoubleOptionFeature("wide_chat", "Wide Chat", "Maximise the chat width.", FeatureCategory.CHAT, Options::chatWidth, 1.0));
        FEATURES.add(new DoubleOptionFeature("tall_chat", "Tall Chat", "Maximise the focused chat height.", FeatureCategory.CHAT, Options::chatHeightFocused, 1.0));
        FEATURES.add(new BoolOptionFeature("no_suggestions", "No Command Suggestions", "Hide the command auto-suggestions.", FeatureCategory.CHAT, Options::autoSuggestions, false));
        FEATURES.add(new BoolOptionFeature("no_chat_links", "No Chat Links", "Don't turn URLs into clickable links.", FeatureCategory.CHAT, Options::chatLinks, false));
        FEATURES.add(new BoolOptionFeature("no_link_prompt", "No Link Warning", "Open links without the confirm prompt.", FeatureCategory.CHAT, Options::chatLinksPrompt, false));
        FEATURES.add(new BoolOptionFeature("no_chat_colors", "No Chat Colors", "Strip color codes from chat.", FeatureCategory.CHAT, Options::chatColors, false));
        FEATURES.add(new BoolOptionFeature("chat_drafts", "Save Chat Drafts", "Keep your half-typed message when closing chat.", FeatureCategory.CHAT, Options::saveChatDrafts, true));

        // Combat tab — fair stat readouts (no automation).
        FEATURES.add(new TextHudFeature("hp_hud", "Health", "Your exact health.", FeatureCategory.COMBAT, 6, 60, CombatTexts::health));
        FEATURES.add(new TextHudFeature("hunger_hud", "Hunger", "Your food level.", FeatureCategory.COMBAT, 6, 72, CombatTexts::hunger));
        FEATURES.add(new TextHudFeature("sat_hud", "Saturation", "Hidden saturation value.", FeatureCategory.COMBAT, 6, 84, CombatTexts::saturation));
        FEATURES.add(new TextHudFeature("armor_hud", "Armor Points", "Total armour points.", FeatureCategory.COMBAT, 6, 96, CombatTexts::armor));
        FEATURES.add(new TextHudFeature("held_hud", "Held Item", "Your main-hand item + count.", FeatureCategory.COMBAT, 6, 108, CombatTexts::held));
        FEATURES.add(new TextHudFeature("xp_hud", "XP Level", "Your experience level.", FeatureCategory.COMBAT, 6, 120, CombatTexts::xp));

        // Movement tab — fair readouts + vanilla movement toggles.
        FEATURES.add(new TextHudFeature("fall_hud", "Fall Distance", "How far you're falling.", FeatureCategory.MOVEMENT, 6, 60, MovementTexts::fall));
        FEATURES.add(new TextHudFeature("vspeed_hud", "Vertical Speed", "Your up/down speed.", FeatureCategory.MOVEMENT, 6, 72, MovementTexts::vspeed));
        FEATURES.add(new TextHudFeature("sprint_hud", "Sprint Status", "Shows when you're sprinting.", FeatureCategory.MOVEMENT, 6, 84, MovementTexts::sprint));
        FEATURES.add(new TextHudFeature("sneak_hud", "Sneak Status", "Shows when you're sneaking.", FeatureCategory.MOVEMENT, 6, 96, MovementTexts::sneak));
        FEATURES.add(new TextHudFeature("heading_hud", "Heading", "Facing angle for straight lines.", FeatureCategory.MOVEMENT, 6, 108, MovementTexts::heading));
        FEATURES.add(new BoolOptionFeature("toggle_sneak", "Toggle Sneak", "Sneak is a toggle, not hold-to-sneak.", FeatureCategory.MOVEMENT, Options::toggleCrouch, true));
        FEATURES.add(new BoolOptionFeature("toggle_sprint_key", "Sprint = Toggle", "Sprint key is a toggle, not hold.", FeatureCategory.MOVEMENT, Options::toggleSprint, true));
        FEATURES.add(new BoolOptionFeature("minecart_rotate", "Rotate with Minecart", "Camera follows minecart turns.", FeatureCategory.MOVEMENT, Options::rotateWithMinecart, true));

        // Visual tab — fair rendering tweaks.
        FEATURES.add(new DoubleOptionFeature("no_glint", "No Enchant Glint", "Remove the enchantment shimmer.", FeatureCategory.VISUAL, Options::glintStrength, 0.0));
        FEATURES.add(new DoubleOptionFeature("static_glint", "Static Glint", "Stop the glint from moving.", FeatureCategory.VISUAL, Options::glintSpeed, 0.0));
        FEATURES.add(new DoubleOptionFeature("less_darkness", "Less Darkness", "Reduce the Warden darkness effect.", FeatureCategory.VISUAL, Options::darknessEffectScale, 0.0));
        FEATURES.add(new BoolOptionFeature("high_contrast", "High Contrast UI", "High-contrast interface.", FeatureCategory.VISUAL, Options::highContrast, true));
        FEATURES.add(new BoolOptionFeature("vsync_off", "VSync Off", "Disable VSync (uncap FPS).", FeatureCategory.VISUAL, Options::enableVsync, false));
        FEATURES.add(new BoolOptionFeature("no_splash", "No Splash Text", "Hide the yellow title splash.", FeatureCategory.VISUAL, Options::hideSplashTexts, true));
        FEATURES.add(new BoolOptionFeature("cutout_leaves", "Cutout Leaves", "Sharper, see-through leaf edges.", FeatureCategory.VISUAL, Options::cutoutLeaves, true));
        FEATURES.add(new BoolOptionFeature("unicode_font", "Unicode Font", "Force the unicode font.", FeatureCategory.VISUAL, Options::forceUnicodeFont, true));
        FEATURES.add(new BoolOptionFeature("dark_loading", "Dark Loading Screen", "Dark Mojang loading screen.", FeatureCategory.VISUAL, Options::darkMojangStudiosBackground, true));
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
                if (root.has("cosmetics") && root.get("cosmetics").isJsonObject()) {
                    JsonObject cos = root.getAsJsonObject("cosmetics");
                    accessoryType = cos.has("accessory") && !cos.get("accessory").isJsonNull()
                            ? cos.get("accessory").getAsString() : null;
                    if (cos.has("accessoryColor")) {
                        try {
                            int rgb = Integer.parseInt(cos.get("accessoryColor").getAsString().replace("#", ""), 16);
                            accessoryColor = 0xFF000000 | (rgb & 0xFFFFFF);
                        } catch (Exception ignored) {}
                    }
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
