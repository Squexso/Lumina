package net.squxso.lumina.feature;

/** Groups features in the Right-Shift control panel. */
public enum FeatureCategory {
    HUD("HUD"),
    COMBAT("Combat"),
    MOVEMENT("Move"),
    MINING("Mine"),
    VISUAL("Visual"),
    CHAT("Chat"),
    MISC("Misc");

    public final String label;
    FeatureCategory(String label) { this.label = label; }
}
