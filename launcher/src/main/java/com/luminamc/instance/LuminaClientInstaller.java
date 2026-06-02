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
 */
public final class LuminaClientInstaller {

    private LuminaClientInstaller() {}

    private static final String TARGET = "luminamc-client.jar";

    /** @return true if the client mod was installed for this (modded) instance. */
    public static boolean install(Instance inst) {
        if (inst == null || inst.id == null || inst.loader == null) return false;
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
}
