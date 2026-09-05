package com.hellovoid.liquidui.target;

import java.util.Objects;

public final class TargetResolution {
    private final TargetResolutionStatus status;
    private final SystemUiTargetProfile profile;
    private final String detail;
    private final Throwable error;

    private TargetResolution(TargetResolutionStatus status, SystemUiTargetProfile profile,
            String detail, Throwable error) {
        this.status = Objects.requireNonNull(status, "status");
        this.profile = profile;
        this.detail = detail == null ? "" : detail;
        this.error = error;
    }

    public static TargetResolution supported(SystemUiTargetProfile profile) {
        return new TargetResolution(TargetResolutionStatus.SUPPORTED,
                Objects.requireNonNull(profile, "profile"), "supported " + profile.id(), null);
    }

    public static TargetResolution unsupported(String detail) {
        return new TargetResolution(TargetResolutionStatus.UNSUPPORTED, null, detail, null);
    }

    public static TargetResolution failed(String detail, Throwable error) {
        return new TargetResolution(TargetResolutionStatus.FAILED, null, detail,
                Objects.requireNonNull(error, "error"));
    }

    public TargetResolutionStatus status() { return status; }
    public SystemUiTargetProfile profile() { return profile; }
    public String detail() { return detail; }
    public Throwable error() { return error; }
    public boolean hasError() { return error != null; }
    public boolean isSupported() { return status == TargetResolutionStatus.SUPPORTED; }
}
