package net.squxso.lumina.logic;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import net.minecraft.client.MinecraftClient;

import java.io.*;
import java.nio.file.*;

/**
 * Persists Lumina settings to {@code .minecraft/config/lumina.json} using
 * the Gson library that ships with Minecraft (no extra dependency).
 *
 * <p>Call {@link #load()} once on client start and {@link #save()} on client
 * stop. All values fall back to their in-code defaults if the file is absent
 * or unreadable.
 */
public final class LuminaConfig {

    private LuminaConfig() {}

    private static final Gson   GSON        = new GsonBuilder().setPrettyPrinting().create();
    private static final String CONFIG_FILE = "config/lumina.json";

    // ── Internal data holder (matches JSON field names 1-to-1) ────────────
    private static class Data {
        boolean showCatchMessages = true;
        boolean softLook          = true;
        boolean humanPauses       = true;
        boolean showHud           = true;
        boolean fpsBoost          = false;
        boolean zoomEnabled       = true;
        boolean autoWitherBlade   = false;
        boolean autoYetiSword     = false;
        boolean autoInkWand       = false;
        boolean autoTotem         = false;
        boolean autoSell          = false;
        int     delayMin          = 200;
        int     delayMax          = 400;
        float   zoomFactor        = 0.5f;
        int     hudX              = 6;
        int     hudY              = 6;
        int     panelOffsetX      = 0;
        int     panelOffsetY      = 0;
    }

    // ── Public API ────────────────────────────────────────────────────────

    /** Reads {@code config/lumina.json} and applies every value to the live state. */
    public static void load() {
        try {
            Path path = configPath();
            if (!Files.exists(path)) return;
            try (Reader r = Files.newBufferedReader(path)) {
                Data d = GSON.fromJson(r, Data.class);
                if (d == null) return;

                LuminaLogic.showCatchMessages = d.showCatchMessages;
                LuminaLogic.softLook          = d.softLook;
                LuminaLogic.humanPauses       = d.humanPauses;
                LuminaLogic.showHud           = d.showHud;
                LuminaLogic.fpsBoost          = d.fpsBoost;
                LuminaLogic.zoomEnabled       = d.zoomEnabled;
                LuminaLogic.autoWitherBlade   = d.autoWitherBlade;
                LuminaLogic.autoYetiSword     = d.autoYetiSword;
                LuminaLogic.autoInkWand       = d.autoInkWand;
                LuminaLogic.autoTotem         = d.autoTotem;
                LuminaLogic.autoSell          = d.autoSell;
                LuminaLogic.setDelay(d.delayMin, d.delayMax);
                LuminaTweaks.zoomFactor       = d.zoomFactor;
                LuminaLayout.hudX             = d.hudX;
                LuminaLayout.hudY             = d.hudY;
                LuminaLayout.panelOffsetX     = d.panelOffsetX;
                LuminaLayout.panelOffsetY     = d.panelOffsetY;
            }
        } catch (Exception ignored) {
            // Missing or corrupt file — defaults remain in place.
        }
    }

    /** Writes current state to {@code config/lumina.json}. */
    public static void save() {
        try {
            Path path = configPath();
            Files.createDirectories(path.getParent());

            Data d = new Data();
            d.showCatchMessages = LuminaLogic.showCatchMessages;
            d.softLook          = LuminaLogic.softLook;
            d.humanPauses       = LuminaLogic.humanPauses;
            d.showHud           = LuminaLogic.showHud;
            d.fpsBoost          = LuminaLogic.fpsBoost;
            d.zoomEnabled       = LuminaLogic.zoomEnabled;
            d.autoWitherBlade   = LuminaLogic.autoWitherBlade;
            d.autoYetiSword     = LuminaLogic.autoYetiSword;
            d.autoInkWand       = LuminaLogic.autoInkWand;
            d.autoTotem         = LuminaLogic.autoTotem;
            d.autoSell          = LuminaLogic.autoSell;
            d.delayMin          = LuminaLogic.getDelayMin();
            d.delayMax          = LuminaLogic.getDelayMax();
            d.zoomFactor        = LuminaTweaks.zoomFactor;
            d.hudX              = LuminaLayout.hudX;
            d.hudY              = LuminaLayout.hudY;
            d.panelOffsetX      = LuminaLayout.panelOffsetX;
            d.panelOffsetY      = LuminaLayout.panelOffsetY;

            try (Writer w = Files.newBufferedWriter(path)) {
                GSON.toJson(d, w);
            }
        } catch (Exception ignored) {
            // Disk write failure — non-fatal.
        }
    }

    // ── Helpers ───────────────────────────────────────────────────────────

    private static Path configPath() {
        return MinecraftClient.getInstance().runDirectory.toPath().resolve(CONFIG_FILE);
    }
}
