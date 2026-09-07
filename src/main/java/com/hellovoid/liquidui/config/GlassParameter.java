package com.hellovoid.liquidui.config;

/** Active zero-copy Prismal controls exposed by LiquidUI. Raw values are integer-backed prefs. */
public enum GlassParameter {
    BLUR("blur", 0, 0, 600, 10f, Category.BASIC, "px"),
    THICKNESS("thickness", 220, 10, 600, 10f, Category.BASIC, "dp"),
    IOR("ior", 155, 100, 200, 100f, Category.BASIC, ""),
    NORMAL_STRENGTH("normal_strength", 135, 0, 300, 100f, Category.BASIC, ""),
    DOME("dome", 135, 0, 200, 100f, Category.BASIC, ""),

    LENS_REFRACTION("lens_refraction", 220, 0, 600, 100f, Category.REFRACTION, ""),
    LENS_DEPTH("lens_depth", 100, 0, 100, 100f, Category.REFRACTION, ""),
    CHROMATIC("chromatic", 42, 0, 80, 1f, Category.REFRACTION, ""),
    REFRACTION_INSET("refraction_inset", 200, 0, 800, 10f, Category.REFRACTION, "px"),
    DISPLACEMENT_SCALE("displacement_scale", 170, 0, 400, 100f, Category.REFRACTION, ""),
    HEIGHT_TRANSITION_WIDTH("height_transition_width", 200, 10, 1200, 10f, Category.REFRACTION, "dp"),
    SMIN_SMOOTHING("smin_smoothing", 18, 0, 240, 10f, Category.REFRACTION, "px"),
    EDGE_REFRACTION_FALLOFF("edge_refraction_falloff", 400, 0, 2000, 100f, Category.REFRACTION, ""),
    FRESNEL_REFLECT("fresnel_reflect", 100, 0, 500, 100f, Category.REFRACTION, ""),
    DISPERSION_R("dispersion_r", 100, 0, 400, 100f, Category.REFRACTION, ""),
    DISPERSION_B("dispersion_b", 100, 0, 400, 100f, Category.REFRACTION, ""),
    BACKDROP_SCALE_X("backdrop_scale_x", 100, 25, 400, 100f, Category.REFRACTION, ""),
    BACKDROP_SCALE_Y("backdrop_scale_y", 100, 25, 400, 100f, Category.REFRACTION, ""),
    PARALLAX_SCALE("parallax_scale", 0, 0, 400, 100f, Category.REFRACTION, ""),

    VIBRANCY("vibrancy", 130, 0, 300, 100f, Category.COLOR, ""),
    BRIGHTNESS("brightness", 100, 50, 200, 100f, Category.COLOR, ""),
    TINT_R("tint_r", 0, 0, 255, 255f, Category.COLOR, ""),
    TINT_G("tint_g", 0, 0, 255, 255f, Category.COLOR, ""),
    TINT_B("tint_b", 0, 0, 255, 255f, Category.COLOR, ""),
    TINT_ALPHA("tint_alpha", 0, 0, 255, 255f, Category.COLOR, ""),
    TRANSMITTANCE("transmittance", 100, 0, 100, 100f, Category.COLOR, ""),

    HIGHLIGHT_WIDTH("highlight_width", 0, 0, 3000, 10f, Category.LIGHTING, "px"),
    PLAIN_HIGHLIGHT("plain_highlight", 0, 0, 100, 100f, Category.LIGHTING, ""),
    LIGHT_DIR_X("light_dir_x", -50, -200, 200, 100f, Category.LIGHTING, ""),
    LIGHT_DIR_Y("light_dir_y", -80, -200, 200, 100f, Category.LIGHTING, ""),
    SPECULAR_STRENGTH("specular_strength", 0, 0, 300, 100f, Category.LIGHTING, ""),
    SPECULAR_SHARPNESS("specular_sharpness", 88, 1, 200, 1f, Category.LIGHTING, ""),
    RIM_LIGHT("rim_light", 0, 0, 300, 100f, Category.LIGHTING, ""),
    CAUSTICS("caustics", 0, 0, 100, 100f, Category.LIGHTING, ""),
    SHADOW_R("shadow_r", 0, 0, 255, 255f, Category.LIGHTING, ""),
    SHADOW_G("shadow_g", 0, 0, 255, 255f, Category.LIGHTING, ""),
    SHADOW_B("shadow_b", 0, 0, 255, 255f, Category.LIGHTING, ""),
    SHADOW_ALPHA("shadow_alpha", 0, 0, 255, 255f, Category.LIGHTING, ""),
    SHADOW_SOFTNESS("shadow_softness", 0, 0, 2000, 100f, Category.LIGHTING, ""),

    SHOW_NORMALS("show_normals", 0, 0, 1, 1f, Category.DEBUG, "");

    public enum Category { BASIC, REFRACTION, COLOR, LIGHTING, DEBUG }

    private final String suffix;
    private final int defaultRaw;
    private final int minRaw;
    private final int maxRaw;
    private final float scale;
    private final Category category;
    private final String unit;

    GlassParameter(
            String suffix,
            int defaultRaw,
            int minRaw,
            int maxRaw,
            float scale,
            Category category,
            String unit) {
        this.suffix = suffix;
        this.defaultRaw = defaultRaw;
        this.minRaw = minRaw;
        this.maxRaw = maxRaw;
        this.scale = scale;
        this.category = category;
        this.unit = unit;
    }

    public String suffix() { return suffix; }
    public int defaultRaw() { return defaultRaw; }
    public int minRaw() { return minRaw; }
    public int maxRaw() { return maxRaw; }
    public float scale() { return scale; }
    public Category category() { return category; }
    public String unit() { return unit; }

    public int clampRaw(int value) {
        return Math.max(minRaw, Math.min(maxRaw, value));
    }

    public float normalize(int raw) {
        return clampRaw(raw) / scale;
    }
}
