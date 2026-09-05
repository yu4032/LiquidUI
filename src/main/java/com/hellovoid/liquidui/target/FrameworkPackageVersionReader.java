package com.hellovoid.liquidui.target;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Objects;

/** Reads installed package identity from Android's boot-framework package-manager authority. */
public final class FrameworkPackageVersionReader implements PackageVersionReader {
    public static final FrameworkPackageVersionReader INSTANCE = new FrameworkPackageVersionReader();

    private static final String APP_GLOBALS = "android.app.AppGlobals";
    private static final String I_PACKAGE_MANAGER = "android.content.pm.IPackageManager";
    private static final String USER_HANDLE = "android.os.UserHandle";

    private FrameworkPackageVersionReader() {}

    @Override
    public PackageVersion read(String packageName) throws Exception {
        Objects.requireNonNull(packageName, "packageName");
        ClassLoader bootClassLoader = ClassLoader.getSystemClassLoader();

        Class<?> appGlobalsClass = bootClassLoader.loadClass(APP_GLOBALS);
        Method getPackageManager = appGlobalsClass.getDeclaredMethod("getPackageManager");
        getPackageManager.setAccessible(true);
        Object packageManager = getPackageManager.invoke(null);
        if (packageManager == null) {
            throw new IllegalStateException("AppGlobals.getPackageManager() returned null");
        }

        Class<?> userHandleClass = bootClassLoader.loadClass(USER_HANDLE);
        Method myUserId = userHandleClass.getDeclaredMethod("myUserId");
        myUserId.setAccessible(true);
        int userId = ((Number) myUserId.invoke(null)).intValue();

        Class<?> packageManagerInterface = bootClassLoader.loadClass(I_PACKAGE_MANAGER);
        Method getPackageInfo = packageManagerInterface.getMethod(
                "getPackageInfo", String.class, long.class, int.class);
        Object packageInfo = getPackageInfo.invoke(packageManager, packageName, 0L, userId);
        if (packageInfo == null) {
            throw new IllegalStateException("PackageInfo unavailable for " + packageName);
        }

        Method getLongVersionCode = packageInfo.getClass().getMethod("getLongVersionCode");
        long versionCode = ((Number) getLongVersionCode.invoke(packageInfo)).longValue();
        Field versionNameField = packageInfo.getClass().getField("versionName");
        Object versionNameValue = versionNameField.get(packageInfo);
        if (!(versionNameValue instanceof String) || ((String) versionNameValue).isEmpty()) {
            throw new IllegalStateException("versionName unavailable for " + packageName);
        }
        return new PackageVersion(versionCode, (String) versionNameValue);
    }
}
