package com.hellovoid.liquidui.target;

import java.util.Objects;

public abstract class StructuralProbe {
    private final String name;

    protected StructuralProbe(String name) {
        this.name = Objects.requireNonNull(name, "name");
    }

    public final String name() { return name; }

    public abstract boolean isSatisfied(ClassLoader classLoader) throws Throwable;
}
