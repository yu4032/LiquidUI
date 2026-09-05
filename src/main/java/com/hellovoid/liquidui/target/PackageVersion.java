package com.hellovoid.liquidui.target;

import java.util.Objects;

public final class PackageVersion {
    private final long versionCode;
    private final String versionName;

    public PackageVersion(long versionCode, String versionName) {
        this.versionCode = versionCode;
        this.versionName = Objects.requireNonNull(versionName, "versionName");
    }

    public long versionCode() { return versionCode; }
    public String versionName() { return versionName; }
}
