package com.hellovoid.liquidui.config;

/** Prismal feature gates. Defaults remain disabled to preserve Phase 0 refraction-only rendering. */
public enum GlassHighlight {
    SKY_HAZE("sky_haze"),
    SPECULAR("specular"),
    RIM_LIT("lit_rim"),
    OPPOSITE_RIM("opposite_rim"),
    CORNER_RIM("corner_rim"),
    FACE_SHEEN("face_sheen"),
    PLAIN_HIGHLIGHT("plain_highlight"),
    CAUSTICS("caustics"),
    PRESS_GLOW("press_glow");

    private final String suffix;

    GlassHighlight(String suffix) {
        this.suffix = suffix;
    }

    public String suffix() { return suffix; }
}
