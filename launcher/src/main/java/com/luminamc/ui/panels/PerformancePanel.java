package com.luminamc.ui.panels;

import com.luminamc.features.Crosshair;
import com.luminamc.features.FeatureSettings;
import com.luminamc.features.HudElement;
import com.luminamc.instance.Instance;
import com.luminamc.ui.AppContext;
import com.luminamc.ui.FxUi;
import com.luminamc.ui.components.ToggleSwitch;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.Node;
import javafx.scene.control.*;
import javafx.scene.layout.*;
import javafx.scene.paint.Color;

import java.util.ArrayList;
import java.util.List;

/**
 * Performance tab: the per-instance built-in client features (mirrors the in-game
 * overlay). Organised into searchable, filterable categories so a specific
 * setting is quick to find.
 */
public final class PerformancePanel extends VBox {

    private final AppContext ctx;
    private final Instance inst;
    private final FeatureSettings f;

    // Search / filter state.
    private final TextField search = new TextField();
    private String activeCategory = "All";
    private record Row(Node node, String keywords, String category, VBox card) {}
    private final List<Row> rows = new ArrayList<>();
    private final List<VBox> allCards = new ArrayList<>();
    private final Label noResults = FxUi.muted("No settings match your search.");

    private static final String[] CATEGORIES = {"All", "FPS", "HUD", "PVP", "Overlay"};

    public PerformancePanel(AppContext ctx, Instance inst) {
        this.ctx = ctx;
        this.inst = inst;
        this.f = inst.features;

        setSpacing(16);
        setPadding(new Insets(24));

        getChildren().addAll(
                FxUi.h1("Performance & Features"),
                FxUi.muted("Per-instance client tweaks, applied to config/lumina.json at launch and "
                        + "toggleable live via the in-game overlay (Right Shift)."),
                searchBar(),
                perfModCard(), fpsCard(), hudCard(), pvpCard(), overlayCard(),
                noResults);

        noResults.setVisible(false);
        noResults.setManaged(false);
        applyFilter();
    }

    // ── search + category chips ──────────────────────────────────────────

    private VBox searchBar() {
        search.setPromptText("🔍  Search settings…  (e.g. crosshair, fps, ping)");
        search.getStyleClass().add("perf-search");
        search.textProperty().addListener((o, a, b) -> applyFilter());

        HBox chips = new HBox(8);
        chips.setAlignment(Pos.CENTER_LEFT);
        ToggleGroup group = new ToggleGroup();
        for (String cat : CATEGORIES) {
            ToggleButton tb = new ToggleButton(cat);
            tb.getStyleClass().add("chip");
            tb.setToggleGroup(group);
            tb.setSelected(cat.equals(activeCategory));
            tb.setOnAction(e -> {
                if (!tb.isSelected()) { tb.setSelected(true); return; }
                activeCategory = cat;
                applyFilter();
            });
            chips.getChildren().add(tb);
        }
        return new VBox(10, search, chips);
    }

    /** Registers a row under a card + category so search/filter can show/hide it. */
    private void register(VBox card, String category, Node row, String keywords) {
        card.getChildren().add(row);
        rows.add(new Row(row, keywords.toLowerCase(), category, card));
    }

    private void applyFilter() {
        String q = search.getText() == null ? "" : search.getText().toLowerCase().trim();
        for (VBox card : allCards) card.getProperties().put("visibleRows", 0);

        for (Row r : rows) {
            boolean catOk = activeCategory.equals("All") || r.category.equals(activeCategory);
            boolean textOk = q.isEmpty() || r.keywords.contains(q);
            boolean show = catOk && textOk;
            r.node.setVisible(show);
            r.node.setManaged(show);
            if (show) {
                int n = (int) r.card.getProperties().getOrDefault("visibleRows", 0);
                r.card.getProperties().put("visibleRows", n + 1);
            }
        }

        boolean anyVisible = false;
        for (VBox card : allCards) {
            boolean cardVisible = (int) card.getProperties().getOrDefault("visibleRows", 0) > 0;
            card.setVisible(cardVisible);
            card.setManaged(cardVisible);
            anyVisible |= cardVisible;
        }
        noResults.setVisible(!anyVisible);
        noResults.setManaged(!anyVisible);
    }

    private VBox card(String title) {
        VBox c = FxUi.card(FxUi.sectionTitle(title));
        allCards.add(c);
        return c;
    }

    // ── Performance Mod (Sodium / Embeddium) ─────────────────────────────

    private VBox perfModCard() {
        VBox card = card("⚡ Performance Mod");
        card.getStyleClass().add("highlight-card");   // make the recommendation stand out

        // What gets installed for this instance's loader.
        String modName = switch (inst.loader) {
            case FABRIC   -> "Sodium";
            case NEOFORGE -> "Sodium (NeoForge)";
            case FORGE    -> "Embeddium / Rubidium";
            default       -> null;
        };

        Label badge = new Label("RECOMMENDED");
        badge.getStyleClass().add("badge");
        Label headline = new Label("Boost your FPS — often 2–3× more frames");
        headline.getStyleClass().add("row-label");
        HBox head = new HBox(10, headline, badge);
        head.setAlignment(Pos.CENTER_LEFT);
        register(card, "FPS", head, "performance mod sodium embeddium fps boost recommended");

        if (modName == null) {
            register(card, "FPS",
                    FxUi.muted("This is a vanilla instance — performance mods need Fabric, Forge or NeoForge. "
                            + "Switch the loader in Settings to enable this."),
                    "performance mod vanilla");
            return card;
        }

        register(card, "FPS",
                FxUi.toggleRow("Auto-install performance mod",
                        "Picks the best free, open-source performance mod for whatever loader & version "
                        + "this instance uses — installed automatically. Nothing to configure.",
                        f.performanceMod, v -> { f.performanceMod = v; save(); }),
                "performance mod sodium embeddium rubidium auto install fps");

        register(card, "FPS",
                FxUi.muted("Works on every version (not just this one): Fabric & NeoForge → Sodium, "
                        + "Forge → Embeddium/Rubidium. This instance (" + inst.loader.displayName + " "
                        + inst.mcVersion + ") → " + modName + "."),
                "performance mod version all sodium embeddium");

        register(card, "FPS",
                FxUi.muted("Note: very old versions (≤ 1.15) only have OptiFine, which has no public API and "
                        + "can't be installed automatically — those are skipped (game still launches normally)."),
                "optifine old versions note");
        return card;
    }

    // ── FPS & Performance ────────────────────────────────────────────────

    private VBox fpsCard() {
        VBox card = card("FPS & Performance");

        register(card, "FPS",
                FxUi.toggleRow("FPS Boost", "Disable extra animations & particles, lower render distance.",
                        f.fpsBoost, v -> { f.fpsBoost = v; save(); }),
                "fps boost performance animations particles render distance lag");

        register(card, "FPS",
                FxUi.toggleRow("Smooth frame limiter", "Even out frame pacing at the cap below.",
                        f.smoothFrameLimiter, v -> { f.smoothFrameLimiter = v; save(); }),
                "smooth frame limiter pacing fps cap");

        Slider limit = new Slider(30, 360, f.frameLimit);
        limit.setMajorTickUnit(60);
        Label limitVal = new Label(f.frameLimit + " fps");
        limit.valueProperty().addListener((o, a, b) -> {
            f.frameLimit = (int) Math.round(b.doubleValue());
            limitVal.setText(f.frameLimit + " fps");
            save();
        });
        HBox.setHgrow(limit, Priority.ALWAYS);
        HBox limitRow = new HBox(12, limit, limitVal);
        limitRow.setAlignment(Pos.CENTER_LEFT);
        limitRow.getStyleClass().add("toggle-row");
        register(card, "FPS", limitRow, "frame limit cap fps max framerate");

        register(card, "FPS",
                FxUi.toggleRow("RAM optimizer", "Add G1GC tuning JVM flags at launch.",
                        f.ramOptimizer, v -> { f.ramOptimizer = v; save(); }),
                "ram optimizer memory g1gc jvm garbage collector");
        return card;
    }

    // ── HUD Widgets ──────────────────────────────────────────────────────

    private VBox hudCard() {
        VBox card = card("HUD Widgets");
        for (HudElement e : f.hud) {
            Label name = new Label(e.label);
            name.getStyleClass().add("row-label");

            Spinner<Integer> x = posSpinner(e.x);
            Spinner<Integer> y = posSpinner(e.y);
            x.valueProperty().addListener((o, a, b) -> { e.x = b; save(); });
            y.valueProperty().addListener((o, a, b) -> { e.y = b; save(); });

            ToggleSwitch sw = new ToggleSwitch(e.enabled);
            sw.selectedProperty().addListener((o, a, b) -> { e.enabled = b; save(); });

            HBox row = new HBox(10, name, FxUi.hgrow(), small("X"), x, small("Y"), y, sw);
            row.setAlignment(Pos.CENTER_LEFT);
            row.getStyleClass().add("toggle-row");
            register(card, "HUD", row, e.label + " hud widget overlay " + e.id);
        }
        return card;
    }

    // ── PVP & Visuals ────────────────────────────────────────────────────

    private VBox pvpCard() {
        VBox card = card("PVP & Visuals");

        register(card, "PVP",
                FxUi.toggleRow("Custom crosshair", "Override the vanilla crosshair.",
                        f.customCrosshair, v -> { f.customCrosshair = v; save(); }),
                "custom crosshair aim");

        ComboBox<Crosshair.Shape> shape = new ComboBox<>();
        shape.getItems().setAll(Crosshair.Shape.values());
        shape.getSelectionModel().select(f.crosshair.shape);
        shape.valueProperty().addListener((o, a, b) -> { f.crosshair.shape = b; save(); });
        ColorPicker chColor = new ColorPicker(web(f.crosshair.color));
        chColor.valueProperty().addListener((o, a, b) -> { f.crosshair.color = hex(b); save(); });
        Spinner<Integer> chSize = new Spinner<>(1, 20, f.crosshair.size);
        chSize.setPrefWidth(80);
        chSize.valueProperty().addListener((o, a, b) -> { f.crosshair.size = b; save(); });
        HBox crosshairRow = new HBox(10, small("Shape"), shape, small("Color"), chColor, small("Size"), chSize);
        crosshairRow.setAlignment(Pos.CENTER_LEFT);
        crosshairRow.getStyleClass().add("toggle-row");
        register(card, "PVP", crosshairRow, "crosshair shape color size aim");

        register(card, "PVP",
                FxUi.toggleRow("Hit color", "Flash entities a custom color when damaged.",
                        f.hitColorEnabled, v -> { f.hitColorEnabled = v; save(); }),
                "hit color flash damage entities red");
        ColorPicker hit = new ColorPicker(web(f.hitColor));
        hit.valueProperty().addListener((o, a, b) -> { f.hitColor = hex(b); save(); });
        HBox hitRow = new HBox(10, small("Hit flash color"), hit);
        hitRow.setAlignment(Pos.CENTER_LEFT);
        hitRow.getStyleClass().add("toggle-row");
        register(card, "PVP", hitRow, "hit flash color");

        register(card, "PVP",
                FxUi.toggleRow("Reach display", "Show block distance to your target.",
                        f.reachDisplay, v -> { f.reachDisplay = v; save(); }),
                "reach display distance target blocks");
        register(card, "PVP",
                FxUi.toggleRow("Fullbright", "Maximum brightness, ignoring gamma.",
                        f.fullbright, v -> { f.fullbright = v; save(); }),
                "fullbright brightness gamma night vision dark");
        register(card, "PVP",
                FxUi.toggleRow("Clean UI — hide XP bar", "",
                        f.cleanUiHideXp, v -> { f.cleanUiHideXp = v; save(); }),
                "clean ui hide xp experience bar");
        register(card, "PVP",
                FxUi.toggleRow("Clean UI — hide food bar", "",
                        f.cleanUiHideFood, v -> { f.cleanUiHideFood = v; save(); }),
                "clean ui hide food hunger bar");
        register(card, "PVP",
                FxUi.toggleRow("Toggle sprint", "Auto-sprint without holding the key.",
                        f.toggleSprint, v -> { f.toggleSprint = v; save(); }),
                "toggle sprint auto run");
        register(card, "PVP",
                FxUi.toggleRow("Block hit animation", "Classic swing animation when hitting blocks.",
                        f.blockHitAnimation, v -> { f.blockHitAnimation = v; save(); }),
                "block hit animation swing old 1.8");
        return card;
    }

    // ── Overlay ──────────────────────────────────────────────────────────

    private VBox overlayCard() {
        VBox card = card("Quick-toggle overlay");
        Label key = new Label(f.overlayKey);
        key.getStyleClass().add("badge");
        HBox row = new HBox(10, new Label("Open overlay key:"), key);
        row.setAlignment(Pos.CENTER_LEFT);
        register(card, "Overlay", row, "overlay key right shift open panel keybind");
        register(card, "Overlay",
                FxUi.muted("Press this key in-game to open the LuminaMC overlay and toggle features live, "
                        + "no restart needed. Rebind it on the global Settings page."),
                "overlay keybind rebind");
        return card;
    }

    // ── helpers ─────────────────────────────────────────────────────────

    private Spinner<Integer> posSpinner(int value) {
        Spinner<Integer> s = new Spinner<>(-2000, 2000, value, 2);
        s.setPrefWidth(78);
        return s;
    }

    private Label small(String s) {
        Label l = new Label(s);
        l.getStyleClass().add("field-label");
        return l;
    }

    private static Color web(String hex) {
        try { return Color.web(hex); } catch (Exception e) { return Color.WHITE; }
    }

    private static String hex(Color c) {
        return String.format("#%02X%02X%02X",
                (int) Math.round(c.getRed() * 255),
                (int) Math.round(c.getGreen() * 255),
                (int) Math.round(c.getBlue() * 255));
    }

    private void save() {
        ctx.instances.save(inst);
    }
}
