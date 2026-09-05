package com.hellovoid.liquidui.config;

import java.util.Objects;

public final class ConfigKey<T> {
    private final String name;
    private final T defaultValue;

    ConfigKey(String name, T defaultValue) {
        this.name = Objects.requireNonNull(name, "name");
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
    }

    public String name() { return name; }
    public T defaultValue() { return defaultValue; }
}
