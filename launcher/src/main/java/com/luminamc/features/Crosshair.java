package com.luminamc.features;

/** Custom crosshair configuration for the PVP feature set. */
public final class Crosshair {

    public enum Shape { CROSS, DOT, CIRCLE, T_SHAPE, NONE }

    public Shape  shape = Shape.CROSS;
    public String color = "#FFFFFF";  // hex RGB
    public int    size  = 6;          // half-length in pixels
    public int    gap   = 2;          // center gap in pixels
    public int    thickness = 1;
}
