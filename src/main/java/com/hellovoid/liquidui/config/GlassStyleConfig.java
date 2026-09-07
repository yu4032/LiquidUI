package com.hellovoid.liquidui.config;

import com.hellovoid.liquidui.glass.core.GlassMaterialProfile;

import java.util.Collections;
import java.util.EnumMap;
import java.util.Map;
import java.util.Objects;

/** Immutable Android-free glass style state published into the shared SystemUI glass core. */
public final class GlassStyleConfig {
    public static final class ResolvedStyle {
        private final Map<GlassParameter, Float> values;

        private ResolvedStyle(Map<GlassParameter, Float> values) {
            this.values = Collections.unmodifiableMap(new EnumMap<>(values));
        }

        public float value(GlassParameter parameter) {
            Float value = values.get(Objects.requireNonNull(parameter, "parameter"));
            if (value == null) throw new IllegalArgumentException("Missing glass parameter " + parameter);
            return value;
        }

        public Map<GlassParameter, Float> values() { return values; }
    }

    private final ResolvedStyle global;
    private final Map<GlassMaterialProfile, ResolvedStyle> profileStyles;
    private final Map<GlassMaterialProfile, Boolean> overrides;
    private final boolean samplingAutoEnabled;
    private final int samplingExtraTopPx;
    private final int samplingExtraBottomPx;
    private final int samplingExtraLeftPx;
    private final int samplingExtraRightPx;

    private GlassStyleConfig(
            ResolvedStyle global,
            Map<GlassMaterialProfile, ResolvedStyle> profileStyles,
            Map<GlassMaterialProfile, Boolean> overrides,
            boolean samplingAutoEnabled,
            int samplingExtraTopPx,
            int samplingExtraBottomPx,
            int samplingExtraLeftPx,
            int samplingExtraRightPx) {
        this.global = global;
        this.profileStyles = Collections.unmodifiableMap(new EnumMap<>(profileStyles));
        this.overrides = Collections.unmodifiableMap(new EnumMap<>(overrides));
        this.samplingAutoEnabled = samplingAutoEnabled;
        this.samplingExtraTopPx = samplingExtraTopPx;
        this.samplingExtraBottomPx = samplingExtraBottomPx;
        this.samplingExtraLeftPx = samplingExtraLeftPx;
        this.samplingExtraRightPx = samplingExtraRightPx;
    }

    public static GlassStyleConfig defaults() {
        return read(new ConfigReader(
                (name, fallback) -> fallback,
                (name, fallback) -> fallback));
    }

    public static GlassStyleConfig read(ConfigReader reader) {
        Objects.requireNonNull(reader, "reader");
        ResolvedStyle global = readStyle(reader, null);
        Map<GlassMaterialProfile, ResolvedStyle> profiles =
                new EnumMap<>(GlassMaterialProfile.class);
        Map<GlassMaterialProfile, Boolean> overrides =
                new EnumMap<>(GlassMaterialProfile.class);
        for (GlassMaterialProfile profile : GlassMaterialProfile.values()) {
            overrides.put(profile, reader.get(ConfigSchema.profileOverrideKey(profile)));
            profiles.put(profile, readStyle(reader, profile));
        }
        return new GlassStyleConfig(
                global,
                profiles,
                overrides,
                reader.get(ConfigSchema.SAMPLING_AUTO_ENABLED),
                reader.get(ConfigSchema.SAMPLING_EXTRA_TOP),
                reader.get(ConfigSchema.SAMPLING_EXTRA_BOTTOM),
                reader.get(ConfigSchema.SAMPLING_EXTRA_LEFT),
                reader.get(ConfigSchema.SAMPLING_EXTRA_RIGHT));
    }

    private static ResolvedStyle readStyle(
            ConfigReader reader, GlassMaterialProfile profile) {
        Map<GlassParameter, Float> values = new EnumMap<>(GlassParameter.class);
        for (GlassParameter parameter : GlassParameter.values()) {
            ConfigKey<Integer> key = profile == null
                    ? ConfigSchema.globalGlassKey(parameter)
                    : ConfigSchema.profileGlassKey(profile, parameter);
            int raw = reader.get(key);
            values.put(parameter, parameter.normalize(raw));
        }
        return new ResolvedStyle(values);
    }

    public ResolvedStyle global() { return global; }

    public ResolvedStyle resolved(GlassMaterialProfile profile) {
        Objects.requireNonNull(profile, "profile");
        return overrides(profile) ? profileStyles.get(profile) : global;
    }

    public boolean overrides(GlassMaterialProfile profile) {
        return Boolean.TRUE.equals(overrides.get(Objects.requireNonNull(profile, "profile")));
    }

    public boolean samplingAutoEnabled() { return samplingAutoEnabled; }
    public int samplingExtraTopPx() { return samplingExtraTopPx; }
    public int samplingExtraBottomPx() { return samplingExtraBottomPx; }
    public int samplingExtraLeftPx() { return samplingExtraLeftPx; }
    public int samplingExtraRightPx() { return samplingExtraRightPx; }
}
