package com.luminamc.features;

/** A single draggable HUD widget with an on/off state and a screen anchor. */
public final class HudElement {

    public String  id;        // stable key, e.g. "fps"
    public String  label;     // display name, e.g. "FPS Counter"
    public boolean enabled;
    public int     x;         // pixel offset from the anchor corner
    public int     y;

    public HudElement() {}

    public HudElement(String id, String label, boolean enabled, int x, int y) {
        this.id = id;
        this.label = label;
        this.enabled = enabled;
        this.x = x;
        this.y = y;
    }
}
