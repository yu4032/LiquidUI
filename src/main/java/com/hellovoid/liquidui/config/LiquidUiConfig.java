package com.hellovoid.liquidui.config;

import java.util.Objects;

public final class LiquidUiConfig {
    private final boolean enabled;
    private final boolean diagnosticsEnabled;
    private final boolean notificationGlassEnabled;
    private final boolean notificationDebugForceRedBackground;

    private LiquidUiConfig(
            boolean enabled,
            boolean diagnosticsEnabled,
            boolean notificationGlassEnabled,
            boolean notificationDebugForceRedBackground) {
        this.enabled = enabled;
        this.diagnosticsEnabled = diagnosticsEnabled;
        this.notificationGlassEnabled = notificationGlassEnabled;
        this.notificationDebugForceRedBackground = notificationDebugForceRedBackground;
    }

    public static LiquidUiConfig from(ConfigReader reader) {
        Objects.requireNonNull(reader, "reader");
        return new LiquidUiConfig(
                reader.get(ConfigSchema.ENABLED),
                reader.get(ConfigSchema.DIAGNOSTICS_ENABLED),
                reader.get(ConfigSchema.NOTIFICATION_GLASS_ENABLED),
                reader.get(ConfigSchema.NOTIFICATION_DEBUG_FORCE_RED_BACKGROUND));
    }

    public boolean enabled() { return enabled; }
    public boolean diagnosticsEnabled() { return diagnosticsEnabled; }
    public boolean notificationGlassEnabled() { return notificationGlassEnabled; }
    public boolean notificationDebugForceRedBackground() {
        return notificationDebugForceRedBackground;
    }
}
