package com.hellovoid.liquidui.config;

@FunctionalInterface
public interface ConfigSource {
    boolean getBoolean(String name, boolean fallback);
}
