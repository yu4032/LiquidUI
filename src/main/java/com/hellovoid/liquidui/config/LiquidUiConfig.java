package com.hellovoid.liquidui.config;

import java.util.Objects;

public final class LiquidUiConfig {
    private final boolean enabled;
    private final boolean diagnosticsEnabled;
    private final boolean notificationGlassEnabled;

    private LiquidUiConfig(
            boolean enabled, boolean diagnosticsEnabled, boolean notificationGlassEnabled) {
        this.enabled = enabled;
        this.diagnosticsEnabled = diagnosticsEnabled;
        this.notificationGlassEnabled = notificationGlassEnabled;
    }

    public static LiquidUiConfig from(ConfigReader reader) {
        Objects.requireNonNull(reader, "reader");
        return new LiquidUiConfig(
                reader.get(ConfigSchema.ENABLED),
                reader.get(ConfigSchema.DIAGNOSTICS_ENABLED),
                reader.get(ConfigSchema.NOTIFICATION_GLASS_ENABLED));
    }

    public boolean enabled() { return enabled; }
    public boolean diagnosticsEnabled() { return diagnosticsEnabled; }
    public boolean notificationGlassEnabled() { return notificationGlassEnabled; }
}
