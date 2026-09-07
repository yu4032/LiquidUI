package com.hellovoid.liquidui.config;

import java.util.Objects;

public final class ConfigKey<T> {
    public enum Kind { BOOLEAN, INT }

    private final String name;
    private final T defaultValue;
    private final Kind kind;
    private final Integer minInt;
    private final Integer maxInt;

    ConfigKey(String name, T defaultValue) {
        this(name, defaultValue, null, null);
    }

    ConfigKey(String name, T defaultValue, Integer minInt, Integer maxInt) {
        this.name = Objects.requireNonNull(name, "name");
        this.defaultValue = Objects.requireNonNull(defaultValue, "defaultValue");
        if (defaultValue instanceof Boolean) {
            kind = Kind.BOOLEAN;
            this.minInt = null;
            this.maxInt = null;
        } else if (defaultValue instanceof Integer) {
            kind = Kind.INT;
            this.minInt = minInt;
            this.maxInt = maxInt;
            if (minInt != null && maxInt != null && minInt > maxInt) {
                throw new IllegalArgumentException("minInt > maxInt for " + name);
            }
        } else {
            throw new IllegalArgumentException("Unsupported config value type for " + name);
        }
    }

    public String name() { return name; }
    public T defaultValue() { return defaultValue; }
    public Kind kind() { return kind; }
    public Integer minInt() { return minInt; }
    public Integer maxInt() { return maxInt; }

    int clampInt(int value) {
        if (minInt != null && value < minInt) value = minInt;
        if (maxInt != null && value > maxInt) value = maxInt;
        return value;
    }
}
