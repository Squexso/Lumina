package com.luminamc.instance;

import com.luminamc.features.FeatureSettings;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * One playable configuration: a Minecraft version, a mod loader, JVM/RAM
 * settings and the toggled client features. Serialized to
 * {@code ~/.luminamc/instances/<id>/instance.json}.
 */
public final class Instance {

    public String id;
    public String name;

    /** Supports both "1.x.x" and "26.x.x" version schemes. */
    public String mcVersion;

    public ModLoader loader        = ModLoader.VANILLA;
    public String    loaderVersion = null; // resolved loader build, e.g. Fabric "0.17.3"

    /** null/blank => use the global/auto-detected JDK. */
    public String javaPathOverride = null;

    public int ramMinMb = 1024;
    public int ramMaxMb = 4096;

    public List<String> extraJvmArgs = new ArrayList<>();

    public String iconName = "default";

    public FeatureSettings features = FeatureSettings.defaults();

    public long createdAt  = System.currentTimeMillis();
    public long lastPlayed = 0L;

    /** Total time (ms) this instance has been played through the launcher. */
    public long playMillis = 0L;

    /** Pinned instances are surfaced in the sidebar for quick access. */
    public boolean pinned = false;

    public Instance() {}

    public static Instance create(String name, String mcVersion, ModLoader loader) {
        Instance i = new Instance();
        i.id = UUID.randomUUID().toString().substring(0, 8);
        i.name = name;
        i.mcVersion = mcVersion;
        i.loader = loader;
        return i;
    }

    /** True for the "26.x.x" scheme (kept distinct from classic "1.x.x"). */
    public boolean isNextGenScheme() {
        String major = mcVersion == null ? "" : mcVersion.split("\\.")[0];
        return !major.equals("1") && !major.isEmpty();
    }
}
