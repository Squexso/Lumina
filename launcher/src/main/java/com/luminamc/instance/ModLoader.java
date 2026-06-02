package com.luminamc.instance;

/** Supported mod loaders. {@code VANILLA} means no loader. */
public enum ModLoader {
    VANILLA  ("Vanilla"),
    FABRIC   ("Fabric"),
    FORGE    ("Forge"),
    NEOFORGE ("NeoForge");

    public final String displayName;

    ModLoader(String displayName) {
        this.displayName = displayName;
    }

    /** Whether {@code mods/} jars are applicable for this loader. */
    public boolean supportsMods() {
        return this != VANILLA;
    }

    @Override
    public String toString() {
        return displayName;
    }
}
