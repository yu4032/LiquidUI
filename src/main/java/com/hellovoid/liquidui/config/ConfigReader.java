package com.hellovoid.liquidui.config;

import java.util.Objects;

public final class ConfigReader {
    private final ConfigSource source;

    public ConfigReader(ConfigSource source) {
        this.source = Objects.requireNonNull(source, "source");
    }

    public boolean get(ConfigKey<Boolean> key) {
        Objects.requireNonNull(key, "key");
        return source.getBoolean(key.name(), key.defaultValue());
    }
}
