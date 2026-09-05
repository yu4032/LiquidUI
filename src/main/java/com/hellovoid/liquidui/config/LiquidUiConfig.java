package com.hellovoid.liquidui.config;

import java.util.Objects;

public final class LiquidUiConfig {
    private final boolean enabled;
    private final boolean diagnosticsEnabled;

    private LiquidUiConfig(boolean enabled, boolean diagnosticsEnabled) {
        this.enabled = enabled;
        this.diagnosticsEnabled = diagnosticsEnabled;
    }

    public static LiquidUiConfig from(ConfigReader reader) {
        Objects.requireNonNull(reader, "reader");
        return new LiquidUiConfig(
                reader.get(ConfigSchema.ENABLED),
                reader.get(ConfigSchema.DIAGNOSTICS_ENABLED));
    }

    public boolean enabled() { return enabled; }
    public boolean diagnosticsEnabled() { return diagnosticsEnabled; }
}
