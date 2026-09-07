package com.hellovoid.liquidui.config;

import com.hellovoid.liquidui.glass.core.GlassMaterialProfile;

import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public final class ConfigSchema {
    public static final ConfigKey<Boolean> ENABLED = new ConfigKey<>("enabled", true);
    public static final ConfigKey<Boolean> DIAGNOSTICS_ENABLED =
            new ConfigKey<>("diagnostics_enabled", false);
    public static final ConfigKey<Boolean> NOTIFICATION_GLASS_ENABLED =
            new ConfigKey<>("notification_glass_enabled", true);

    public static final ConfigKey<Boolean> SAMPLING_AUTO_ENABLED =
            new ConfigKey<>("glass_sampling_auto_enabled", false);
    public static final ConfigKey<Integer> SAMPLING_EXTRA_TOP =
            new ConfigKey<>("glass_sampling_extra_top", 0, -256, 256);
    public static final ConfigKey<Integer> SAMPLING_EXTRA_BOTTOM =
            new ConfigKey<>("glass_sampling_extra_bottom", 0, -256, 256);
    public static final ConfigKey<Integer> SAMPLING_EXTRA_LEFT =
            new ConfigKey<>("glass_sampling_extra_left", 0, -256, 256);
    public static final ConfigKey<Integer> SAMPLING_EXTRA_RIGHT =
            new ConfigKey<>("glass_sampling_extra_right", 0, -256, 256);

    private static final Map<GlassParameter, ConfigKey<Integer>> GLOBAL_GLASS =
            new EnumMap<>(GlassParameter.class);
    private static final Map<GlassMaterialProfile, ConfigKey<Boolean>> PROFILE_OVERRIDES =
            new EnumMap<>(GlassMaterialProfile.class);
    private static final Map<GlassMaterialProfile, Map<GlassParameter, ConfigKey<Integer>>>
            PROFILE_GLASS = new EnumMap<>(GlassMaterialProfile.class);
    private static final List<ConfigKey<?>> ALL;

    static {
        List<ConfigKey<?>> all = new ArrayList<>();
        all.add(ENABLED);
        all.add(DIAGNOSTICS_ENABLED);
        all.add(NOTIFICATION_GLASS_ENABLED);

        for (GlassParameter parameter : GlassParameter.values()) {
            ConfigKey<Integer> key = intKey(
                    "glass_global_" + parameter.suffix(), parameter);
            GLOBAL_GLASS.put(parameter, key);
            all.add(key);
        }

        all.add(SAMPLING_AUTO_ENABLED);
        all.add(SAMPLING_EXTRA_TOP);
        all.add(SAMPLING_EXTRA_BOTTOM);
        all.add(SAMPLING_EXTRA_LEFT);
        all.add(SAMPLING_EXTRA_RIGHT);

        for (GlassMaterialProfile profile : GlassMaterialProfile.values()) {
            String prefix = "glass_" + profile.name().toLowerCase(Locale.ROOT);
            ConfigKey<Boolean> override = new ConfigKey<>(prefix + "_override", false);
            PROFILE_OVERRIDES.put(profile, override);
            all.add(override);

            Map<GlassParameter, ConfigKey<Integer>> values =
                    new EnumMap<>(GlassParameter.class);
            for (GlassParameter parameter : GlassParameter.values()) {
                ConfigKey<Integer> key = intKey(prefix + "_" + parameter.suffix(), parameter);
                values.put(parameter, key);
                all.add(key);
            }
            PROFILE_GLASS.put(profile, Collections.unmodifiableMap(values));
        }
        ALL = Collections.unmodifiableList(all);
    }

    private ConfigSchema() {}

    public static List<ConfigKey<?>> all() { return ALL; }

    public static ConfigKey<Integer> globalGlassKey(GlassParameter parameter) {
        ConfigKey<Integer> key = GLOBAL_GLASS.get(parameter);
        if (key == null) throw new IllegalArgumentException("Unknown glass parameter " + parameter);
        return key;
    }

    public static ConfigKey<Boolean> profileOverrideKey(GlassMaterialProfile profile) {
        ConfigKey<Boolean> key = PROFILE_OVERRIDES.get(profile);
        if (key == null) throw new IllegalArgumentException("Unknown glass profile " + profile);
        return key;
    }

    public static ConfigKey<Integer> profileGlassKey(
            GlassMaterialProfile profile, GlassParameter parameter) {
        Map<GlassParameter, ConfigKey<Integer>> profileKeys = PROFILE_GLASS.get(profile);
        if (profileKeys == null) throw new IllegalArgumentException("Unknown glass profile " + profile);
        ConfigKey<Integer> key = profileKeys.get(parameter);
        if (key == null) throw new IllegalArgumentException("Unknown glass parameter " + parameter);
        return key;
    }

    public static boolean isGlassStyleKey(String name) {
        return name != null && name.startsWith("glass_");
    }

    private static ConfigKey<Integer> intKey(String name, GlassParameter parameter) {
        return new ConfigKey<>(
                name,
                parameter.defaultRaw(),
                parameter.minRaw(),
                parameter.maxRaw());
    }
}
