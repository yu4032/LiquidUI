package com.hellovoid.liquidui.glass.media;

import android.view.View;

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

/** Verified Miui notification media-card hook for systemui-001. */
public final class MediaGlassHook implements SystemUiHook {
    public static final String HOOK_ID = "media.notification-card-liquid-glass";
    private static final String TARGET_PROFILE = "systemui-001";
    private static final String MEDIA_CONTROLLER =
            "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewControllerImpl";
    private static final String MEDIA_HOLDER =
            "com.android.systemui.statusbar.notification.mediacontrol.MiuiMediaViewHolder";

    private final AfterMethodHookBackend afterBackend;
    private final SystemUiGlassCore glassCore;

    public MediaGlassHook(
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

        final Method attach;
        final Method detach;
        final Method updateMediaBackground;
        final Field player;
        final Field mediaBg;
        final MediaGlassAdapter adapter;

        try {
            Class<?> controllerClass = TargetClassResolver.require(classLoader, MEDIA_CONTROLLER);
            Class<?> holderClass = TargetClassResolver.require(classLoader, MEDIA_HOLDER);

            attach = accessible(controllerClass.getDeclaredMethod("attach", holderClass));
            detach = accessible(controllerClass.getDeclaredMethod("detach"));
            updateMediaBackground = accessible(
                    controllerClass.getDeclaredMethod("updateMediaBackground"));
            player = accessible(holderClass.getDeclaredField("player"));
            mediaBg = accessible(holderClass.getDeclaredField("mediaBg"));

            Method setMiViewBlurMode = View.class.getMethod("setMiViewBlurMode", int.class);
            Method clearMiBackgroundBlendColor =
                    View.class.getMethod("clearMiBackgroundBlendColor");
            Method getMiBackgroundBlendColor = View.class.getMethod(
                    "getMiBackgroundBlendColor", ArrayList.class, ArrayList.class);
            Method setMiBackgroundBlendColors =
                    View.class.getMethod("setMiBackgroundBlendColors", ArrayList.class);

            MediaNativeMaterialController material = new MediaNativeMaterialController(
                    setMiViewBlurMode,
                    clearMiBackgroundBlendColor,
                    getMiBackgroundBlendColor,
                    setMiBackgroundBlendColors);
            adapter = new MediaGlassAdapter(glassCore, material, player, mediaBg);
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException error) {
            return HookInstallResult.unsupported(HOOK_ID,
                    "Miui media glass contract missing: " + error);
        } catch (Throwable error) {
            return HookInstallResult.failed(HOOK_ID,
                    "Miui media glass contract resolution failed", error);
        }

        List<Runnable> rollbacks = new ArrayList<>();
        try {
            // attach(holder) is the authoritative point where the controller accepts the player,
            // immediately after its initial native updateMediaBackground().
            rollbacks.add(afterBackend.intercept(
                    attach,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        try {
                            if (args.length < 1 || args[0] == null) return;
                            adapter.attach(thisObject, args[0]);
                        } catch (Throwable error) {
                            android.util.Log.e("LiquidUI",
                                    "[LUI][MediaGlass] attach failed", error);
                        }
                    })::unhook);

            // UI-mode, MiBlur, status-bar-state and full-AOD changes rebuild/reblend mediaBg here.
            rollbacks.add(afterBackend.intercept(
                    updateMediaBackground,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        try {
                            adapter.onBackgroundUpdated(thisObject);
                        } catch (Throwable error) {
                            android.util.Log.e("LiquidUI",
                                    "[LUI][MediaGlass] background refresh failed", error);
                        }
                    })::unhook);

            // detach() is the controller's explicit teardown and clears its holder afterwards.
            rollbacks.add(afterBackend.intercept(
                    detach,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        try {
                            adapter.detach(thisObject);
                        } catch (Throwable error) {
                            android.util.Log.e("LiquidUI",
                                    "[LUI][MediaGlass] detach failed", error);
                        }
                    })::unhook);

            android.util.Log.i("LiquidUI",
                    "[LUI][MediaGlass] installed verified Miui notification media adapter");
            return HookInstallResult.installed(HOOK_ID);
        } catch (Throwable error) {
            for (int index = rollbacks.size() - 1; index >= 0; index--) {
                try { rollbacks.get(index).run(); } catch (Throwable rollback) {
                    error.addSuppressed(rollback);
                }
            }
            try { adapter.close(); } catch (Throwable rollback) { error.addSuppressed(rollback); }
            return HookInstallResult.failed(HOOK_ID,
                    "Miui media glass hook registration failed", error);
        }
    }

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T value) {
        value.setAccessible(true);
        return value;
    }
}
