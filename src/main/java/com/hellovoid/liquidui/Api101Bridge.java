package com.hellovoid.liquidui;

import android.content.SharedPreferences;
import android.util.Log;

import io.github.libxposed.api.XposedModule;

/** Process-local bridge to libxposed API 101. */
public final class Api101Bridge {
    private static volatile XposedModule module;

    private Api101Bridge() {}

    static void init(XposedModule value) {
        module = value;
    }

    public static XposedModule module() {
        XposedModule value = module;
        if (value == null) throw new IllegalStateException("API 101 module not initialized");
        return value;
    }

    public static SharedPreferences remotePreferences(String group) {
        return module().getRemotePreferences(group);
    }

    public static void log(String message) {
        try {
            module().log(Log.INFO, "LiquidUI", message);
        } catch (Throwable ignored) {
            Log.i("LiquidUI", message);
        }
    }

    public static void log(String message, Throwable error) {
        try {
            module().log(Log.ERROR, "LiquidUI", message, error);
        } catch (Throwable ignored) {
            Log.e("LiquidUI", message, error);
        }
    }
}
