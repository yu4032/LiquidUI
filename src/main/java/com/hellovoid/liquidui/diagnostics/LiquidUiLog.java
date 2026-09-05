package com.hellovoid.liquidui.diagnostics;

import java.util.Objects;

public final class LiquidUiLog {
    private LiquidUiLog() {}

    public static String format(String message) {
        return "[LUI] " + Objects.requireNonNull(message, "message");
    }
}
