package com.hellovoid.liquidui.glass.notification;

import android.view.Surface;
import android.view.SurfaceControl;
import android.view.View;

import com.hellovoid.liquidui.hook.BeforeMethodHookBackend;
import com.hellovoid.liquidui.hook.HookInstallResult;
import com.hellovoid.liquidui.hook.SystemUiHook;
import com.hellovoid.liquidui.reflect.TargetClassResolver;
import com.hellovoid.liquidui.target.SystemUiTargetProfile;

import java.lang.reflect.Method;
import java.util.Objects;

/** Read-only diagnostic hook for HyperOS/framework-owned NotificationShade PassBlur consumers. */
public final class FrameworkPassBlurProbeHook implements SystemUiHook {
    public static final String HOOK_ID = "notification.framework-passblur-probe";
    private static final String TARGET_PROFILE = "systemui-001";
    private static final String FRAMEWORK_VIEW = "android.view.View";
    private static final String NOTIFICATION_PANEL = "com.android.systemui.shade.NotificationPanelView";

    private final BeforeMethodHookBackend backend;

    public FrameworkPassBlurProbeHook(BeforeMethodHookBackend backend) {
        this.backend = Objects.requireNonNull(backend, "backend");
    }

    @Override public String id() { return HOOK_ID; }

    @Override
    public HookInstallResult install(ClassLoader classLoader, SystemUiTargetProfile profile) {
        Objects.requireNonNull(classLoader, "classLoader");
        Objects.requireNonNull(profile, "profile");
        if (!TARGET_PROFILE.equals(profile.id())) {
            return HookInstallResult.unsupported(HOOK_ID, "profile=" + profile.id());
        }
        try {
            Class<?> viewClass = TargetClassResolver.require(classLoader, FRAMEWORK_VIEW);
            Class<?> notificationPanelClass = TargetClassResolver.require(classLoader, NOTIFICATION_PANEL);
            Method setPassWindowBlurEnabled = viewClass.getDeclaredMethod(
                    "setPassWindowBlurEnabled", boolean.class);
            setPassWindowBlurEnabled.setAccessible(true);
            backend.intercept(
                    setPassWindowBlurEnabled,
                    BeforeMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        if (!notificationPanelClass.isInstance(thisObject)
                                || args.length == 0
                                || !(args[0] instanceof Boolean requested)
                                || !requested
                                || !(thisObject instanceof View panelView)) {
                            return;
                        }
                        FrameworkPassBlurProbe.inspectOnce(panelView);
                    });

            Method setPassBlurSurface = SurfaceControl.Transaction.class.getMethod(
                    "SetPassBlurSurface", SurfaceControl.class, Surface.class);
            Method setUpdateTextureFlag = SurfaceControl.Transaction.class.getMethod(
                    "setUpdateTextureFlag", SurfaceControl.class, boolean.class, float.class);
            backend.intercept(
                    setPassBlurSurface,
                    BeforeMethodHookBackend.PRIORITY_HIGHEST,
                    FrameworkPassBlurTransactionProbe::observeSetPassBlurSurface);
            backend.intercept(
                    setUpdateTextureFlag,
                    BeforeMethodHookBackend.PRIORITY_HIGHEST,
                    FrameworkPassBlurTransactionProbe::observeSetUpdateTextureFlag);
            return HookInstallResult.installed(HOOK_ID);
        } catch (ClassNotFoundException | NoSuchMethodException error) {
            return HookInstallResult.unsupported(HOOK_ID, "framework PassBlur probe contract missing: " + error);
        } catch (Throwable error) {
            return HookInstallResult.failed(HOOK_ID, "framework PassBlur probe registration failed", error);
        }
    }
}
