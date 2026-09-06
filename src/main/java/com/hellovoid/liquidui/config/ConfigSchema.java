package com.hellovoid.liquidui.config;

import java.util.List;

public final class ConfigSchema {
    public static final ConfigKey<Boolean> ENABLED = new ConfigKey<>("enabled", true);
    public static final ConfigKey<Boolean> DIAGNOSTICS_ENABLED =
            new ConfigKey<>("diagnostics_enabled", false);
    public static final ConfigKey<Boolean> NOTIFICATION_GLASS_ENABLED =
            new ConfigKey<>("notification_glass_enabled", true);

    private static final List<ConfigKey<?>> ALL = List.of(
            ENABLED, DIAGNOSTICS_ENABLED, NOTIFICATION_GLASS_ENABLED);

    private ConfigSchema() {}

    public static List<ConfigKey<?>> all() { return ALL; }
}
