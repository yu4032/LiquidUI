package com.hellovoid.liquidui.config;

import java.util.Objects;

public final class ConfigReader {
    @FunctionalInterface
    public interface IntSource {
        int getInt(String name, int fallback);
    }

    private final ConfigSource booleanSource;
    private final IntSource intSource;

    public ConfigReader(ConfigSource source) {
        this(source, (name, fallback) -> fallback);
    }

    public ConfigReader(ConfigSource booleanSource, IntSource intSource) {
        this.booleanSource = Objects.requireNonNull(booleanSource, "booleanSource");
        this.intSource = Objects.requireNonNull(intSource, "intSource");
    }

    @SuppressWarnings("unchecked")
    public <T> T get(ConfigKey<T> key) {
        Objects.requireNonNull(key, "key");
        return switch (key.kind()) {
            case BOOLEAN -> (T) Boolean.valueOf(booleanSource.getBoolean(
                    key.name(), (Boolean) key.defaultValue()));
            case INT -> {
                int value = intSource.getInt(key.name(), (Integer) key.defaultValue());
                yield (T) Integer.valueOf(key.clampInt(value));
            }
        };
    }
}
