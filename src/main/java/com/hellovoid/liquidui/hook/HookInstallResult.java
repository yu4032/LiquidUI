package com.hellovoid.liquidui.hook;

import java.util.Objects;

public final class HookInstallResult {
    private final String hookId;
    private final HookInstallStatus status;
    private final String detail;
    private final Throwable error;

    private HookInstallResult(String hookId, HookInstallStatus status, String detail, Throwable error) {
        this.hookId = Objects.requireNonNull(hookId, "hookId");
        this.status = Objects.requireNonNull(status, "status");
        this.detail = detail == null ? "" : detail;
        this.error = error;
    }

    public static HookInstallResult installed(String hookId) {
        return new HookInstallResult(hookId, HookInstallStatus.INSTALLED, "installed", null);
    }

    public static HookInstallResult unsupported(String hookId, String detail) {
        return new HookInstallResult(hookId, HookInstallStatus.UNSUPPORTED, detail, null);
    }

    public static HookInstallResult failed(String hookId, String detail, Throwable error) {
        return new HookInstallResult(hookId, HookInstallStatus.FAILED, detail,
                Objects.requireNonNull(error, "error"));
    }

    public static HookInstallResult disabled(String hookId) {
        return new HookInstallResult(hookId, HookInstallStatus.DISABLED, "disabled", null);
    }

    public String hookId() { return hookId; }
    public HookInstallStatus status() { return status; }
    public String detail() { return detail; }
    public Throwable error() { return error; }
    public boolean hasError() { return error != null; }
}
