package com.hellovoid.liquidui.glass.media;

import android.os.Bundle;

import com.hellovoid.liquidui.glass.core.SystemUiGlassCore;
import com.hellovoid.liquidui.hook.AfterMethodHookBackend;
import com.hellovoid.liquidui.hook.HookInstallResult;
import com.hellovoid.liquidui.hook.SystemUiHook;
import com.hellovoid.liquidui.reflect.TargetClassResolver;
import com.hellovoid.liquidui.target.SystemUiTargetProfile;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/** Verified built-in media-output dialog hook for systemui-001. */
public final class MediaOutputDialogGlassHook implements SystemUiHook {
    public static final String HOOK_ID = "media.output-dialog-liquid-glass";
    private static final String TARGET_PROFILE = "systemui-001";
    private static final String BASE_DIALOG =
            "com.android.systemui.media.dialog.MediaOutputBaseDialog";

    private final AfterMethodHookBackend afterBackend;
    private final SystemUiGlassCore glassCore;

    public MediaOutputDialogGlassHook(
            AfterMethodHookBackend afterBackend,
            SystemUiGlassCore glassCore) {
        this.afterBackend = Objects.requireNonNull(afterBackend, "afterBackend");
        this.glassCore = Objects.requireNonNull(glassCore, "glassCore");
    }

    @Override
    public String id() {
        return HOOK_ID;
    }

    @Override
    public HookInstallResult install(ClassLoader classLoader, SystemUiTargetProfile profile) {
        Objects.requireNonNull(classLoader, "classLoader");
        Objects.requireNonNull(profile, "profile");
        if (!TARGET_PROFILE.equals(profile.id())) {
            return HookInstallResult.unsupported(HOOK_ID, "profile=" + profile.id());
        }

        final Method onCreate;
        final Method stop;
        final Method updateDialogBackgroundColor;
        final MediaOutputDialogGlassAdapter adapter;

        try {
            Class<?> baseDialogClass = TargetClassResolver.require(classLoader, BASE_DIALOG);
            onCreate = accessible(baseDialogClass.getDeclaredMethod("onCreate", Bundle.class));
            stop = accessible(baseDialogClass.getDeclaredMethod("stop"));
            updateDialogBackgroundColor = accessible(
                    baseDialogClass.getDeclaredMethod("updateDialogBackgroundColor"));
            Field dialogView = accessible(baseDialogClass.getDeclaredField("mDialogView"));
            Field deviceList = accessible(baseDialogClass.getDeclaredField("mDeviceListLayout"));

            adapter = new MediaOutputDialogGlassAdapter(
                    glassCore,
                    new MediaOutputDialogNativeMaterialController(),
                    dialogView,
                    deviceList);
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException error) {
            return HookInstallResult.unsupported(HOOK_ID,
                    "media output dialog glass contract missing: " + error);
        } catch (Throwable error) {
            return HookInstallResult.failed(HOOK_ID,
                    "media output dialog contract resolution failed", error);
        }

        List<Runnable> rollbacks = new ArrayList<>();
        try {
            // onCreate has inflated media_output_dialog and installed mDialogView as Window content.
            rollbacks.add(afterBackend.intercept(
                    onCreate,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        try {
                            adapter.register(thisObject);
                        } catch (Throwable error) {
                            android.util.Log.e("LiquidUI",
                                    "[LUI][MediaOutputGlass] register failed", error);
                        }
                    })::unhook);

            // Dynamic media color schemes retint the root and replace the list ColorDrawable here.
            rollbacks.add(afterBackend.intercept(
                    updateDialogBackgroundColor,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        try {
                            adapter.onBackgroundUpdated(thisObject);
                        } catch (Throwable error) {
                            android.util.Log.e("LiquidUI",
                                    "[LUI][MediaOutputGlass] background refresh failed", error);
                        }
                    })::unhook);

            // SystemUIDialog.onStop() calls this exact override on every dialog stop/dismiss path.
            rollbacks.add(afterBackend.intercept(
                    stop,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        try {
                            adapter.stop(thisObject);
                        } catch (Throwable error) {
                            android.util.Log.e("LiquidUI",
                                    "[LUI][MediaOutputGlass] stop cleanup failed", error);
                        }
                    })::unhook);

            android.util.Log.i("LiquidUI",
                    "[LUI][MediaOutputGlass] installed verified media-output dialog adapter");
            return HookInstallResult.installed(HOOK_ID);
        } catch (Throwable error) {
            for (int index = rollbacks.size() - 1; index >= 0; index--) {
                try { rollbacks.get(index).run(); } catch (Throwable rollback) {
                    error.addSuppressed(rollback);
                }
            }
            try { adapter.close(); } catch (Throwable rollback) { error.addSuppressed(rollback); }
            return HookInstallResult.failed(HOOK_ID,
                    "media output dialog hook registration failed", error);
        }
    }

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T value) {
        value.setAccessible(true);
        return value;
    }
}
