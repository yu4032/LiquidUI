package com.hellovoid.liquidui.reflect;

import java.util.Objects;

public final class TargetClassResolver {
    private TargetClassResolver() {}

    public static Class<?> require(ClassLoader targetClassLoader, String className)
            throws ClassNotFoundException {
        Objects.requireNonNull(targetClassLoader, "targetClassLoader");
        Objects.requireNonNull(className, "className");
        return targetClassLoader.loadClass(className);
    }

    public static Class<?> find(ClassLoader targetClassLoader, String className) {
        try {
            return require(targetClassLoader, className);
        } catch (ClassNotFoundException ignored) {
            return null;
        }
    }
}
