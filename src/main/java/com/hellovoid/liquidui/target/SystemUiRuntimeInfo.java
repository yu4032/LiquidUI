package com.hellovoid.liquidui.target;

import java.util.Objects;

public final class SystemUiRuntimeInfo {
    private final String packageName;
    private final long versionCode;
    private final String versionName;
    private final int sdkInt;

    public SystemUiRuntimeInfo(String packageName, long versionCode, String versionName, int sdkInt) {
        this.packageName = Objects.requireNonNull(packageName, "packageName");
        this.versionCode = versionCode;
        this.versionName = Objects.requireNonNull(versionName, "versionName");
        this.sdkInt = sdkInt;
    }

    public String packageName() { return packageName; }
    public long versionCode() { return versionCode; }
    public String versionName() { return versionName; }
    public int sdkInt() { return sdkInt; }
}
