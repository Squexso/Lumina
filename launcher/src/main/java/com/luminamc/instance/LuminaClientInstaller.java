package com.luminamc.instance;

import com.luminamc.config.LuminaPaths;

import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;

/**
 * Drops the matching LuminaMC client mod (the Right-Shift HUD/control panel) into an
 * instance's {@code mods/} folder so it's available without any manual copying.
 *
 * <p>The three loader jars are bundled with the launcher under
 * {@code resources/lumina-client/}. Vanilla instances are skipped (no loader = no mods).
 * Runs on every launch and is idempotent — it always refreshes the bundled version.
 *
 * <p>The client mod is built for one exact Minecraft version ({@link #SUPPORTED_MC}).
 * For any other version it is <b>not</b> installed, and any previously-installed copy is
 * removed — otherwise an incompatible mod would stop that version from starting.
 */
public final class LuminaClientInstaller {

    private LuminaClientInstaller() {}

    private static final String TARGET = "luminamc-client.jar";

    /** The exact Minecraft version the bundled client mod targets. */
    public static final String SUPPORTED_MC = "1.21.11";

    /** Whether the client mod can be installed into this instance (right loader + version). */
    public static boolean supports(Instance inst) {
        return inst != null && inst.loader != null && inst.loader != ModLoader.VANILLA
                && SUPPORTED_MC.equals(inst.mcVersion);
    }

    /** @return true if the client mod was installed for this (modded) instance. */
    public static boolean install(Instance inst) {
        if (inst == null || inst.id == null || inst.loader == null) return false;

        // Only the matching Minecraft version gets the mod; otherwise strip any stale copy
        // so an incompatible jar can never block the game from launching.
        if (!SUPPORTED_MC.equals(inst.mcVersion)) {
            removeInstalled(inst);
            return false;
        }

        String resource = switch (inst.loader) {
            case FABRIC   -> "/lumina-client/lumina-fabric.jar";
            case FORGE    -> "/lumina-client/lumina-forge.jar";
            case NEOFORGE -> "/lumina-client/lumina-neoforge.jar";
            case VANILLA  -> null;
        };
        if (resource == null) return false;

        try (InputStream in = LuminaClientInstaller.class.getResourceAsStream(resource)) {
            if (in == null) return false; // not bundled in this build
            Path mods = LuminaPaths.instanceMods(inst.id);
            Files.createDirectories(mods);
            Files.copy(in, mods.resolve(TARGET), StandardCopyOption.REPLACE_EXISTING);
            return true;
        } catch (Exception e) {
            return false; // best-effort — launch must continue regardless
        }
    }

    /** Deletes a previously-installed client mod from an instance (best-effort). */
    public static void removeInstalled(Instance inst) {
        if (inst == null || inst.id == null) return;
        try {
            Files.deleteIfExists(LuminaPaths.instanceMods(inst.id).resolve(TARGET));
        } catch (Exception ignored) {
            // best-effort
        }
    }
}
