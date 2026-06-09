package com.luminamc.game;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.luminamc.config.LuminaPaths;
import com.luminamc.instance.Instance;
import com.luminamc.shop.ShopCatalog;

import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Bridges the launcher's shop state into the in-game mod: writes the equipped
 * Lumina Cape flag into an instance's {@code config/lumina.json} so the mod
 * renders it. Read-modify-write — every other key the mod owns is preserved.
 */
public final class LuminaCosmetics {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().create();

    private LuminaCosmetics() {}

    /**
     * Sets {@code features.lumina_cape} in the instance config to match the
     * currently-equipped cape. Safe to call on every launch.
     */
    public static void writeEquipped(Instance inst, String equippedCape) {
        try {
            Path path = LuminaPaths.instanceLuminaConfig(inst.id);
            JsonObject root = new JsonObject();
            if (Files.exists(path)) {
                try (Reader r = Files.newBufferedReader(path)) {
                    JsonElement el = JsonParser.parseReader(r);
                    if (el != null && el.isJsonObject()) root = el.getAsJsonObject();
                } catch (Exception ignored) {
                    // corrupt → start fresh rather than fail the launch
                }
            }
            JsonObject features = root.has("features") && root.get("features").isJsonObject()
                    ? root.getAsJsonObject("features")
                    : new JsonObject();

            boolean capeOn = ShopCatalog.LUMINA_CAPE_ID.equals(equippedCape);
            features.addProperty("lumina_cape", capeOn);
            root.add("features", features);

            Files.createDirectories(path.getParent());
            try (Writer w = Files.newBufferedWriter(path)) {
                GSON.toJson(root, w);
            }
        } catch (Exception ignored) {
            // cosmetic sync is best-effort; never block a launch on it
        }
    }
}
