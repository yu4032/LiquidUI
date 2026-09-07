package com.hellovoid.liquidui.glass.controlcenter;

import android.content.res.Configuration;
import android.view.View;
import android.widget.ImageView;

import com.hellovoid.liquidui.glass.core.SystemUiGlassCore;
import com.hellovoid.liquidui.hook.AfterMethodHookBackend;
import com.hellovoid.liquidui.hook.HookInstallResult;
import com.hellovoid.liquidui.hook.SystemUiHook;
import com.hellovoid.liquidui.reflect.TargetClassResolver;
import com.hellovoid.liquidui.target.SystemUiTargetProfile;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Verified built-in Quick Settings tile + brightness hook.
 *
 * <p>The HyperOS new Control Center lives in the external com.miui.systemui.plugin APK and is
 * deliberately not guessed here. This hook covers only the exact built-in classic-QS contracts
 * documented for systemui-001.</p>
 */
public final class ControlCenterGlassHook implements SystemUiHook {
    public static final String HOOK_ID = "control-center.classic-qs-liquid-glass";
    private static final String TARGET_PROFILE = "systemui-001";

    private static final String QS_PANEL = "com.android.systemui.qs.MiuiQSPanel";
    private static final String TILE_RECORD = "com.android.systemui.qs.MiuiQSPanel$TileRecord";
    private static final String TILE_BASE = "com.android.systemui.qs.tileimpl.MiuiQSTileBaseView";
    private static final String ICON_IMPL = "com.android.systemui.qs.tileimpl.MiuiQSIconViewImpl";
    private static final String TILE_STATE = "com.android.systemui.plugins.qs.QSTile$State";
    private static final String BRIGHTNESS_SLIDER =
            "com.android.systemui.settings.brightness.BrightnessSliderView";
    private static final String QS_CONTAINER = "com.android.systemui.qs.MiuiQSContainer";

    private final AfterMethodHookBackend afterBackend;
    private final SystemUiGlassCore glassCore;

    public ControlCenterGlassHook(
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

        final Method setTiles;
        final Method updateIcon;
        final Method brightnessFinishInflate;
        final Method qsConfigurationChanged;
        final Field recordsField;
        final Field tileViewField;
        final Field mIconFrame;
        final Field mIcon;
        final Field imageField;
        final Field mAnimator;
        final Field stateField;
        final Field brightnessSliderField;
        final Field brightnessViewField;
        final ControlCenterGlassAdapter adapter;

        try {
            Class<?> panelClass = TargetClassResolver.require(classLoader, QS_PANEL);
            Class<?> tileRecordClass = TargetClassResolver.require(classLoader, TILE_RECORD);
            Class<?> tileBaseClass = TargetClassResolver.require(classLoader, TILE_BASE);
            Class<?> iconImplClass = TargetClassResolver.require(classLoader, ICON_IMPL);
            Class<?> tileStateClass = TargetClassResolver.require(classLoader, TILE_STATE);
            Class<?> brightnessSliderClass = TargetClassResolver.require(classLoader, BRIGHTNESS_SLIDER);
            Class<?> qsContainerClass = TargetClassResolver.require(classLoader, QS_CONTAINER);

            setTiles = accessible(panelClass.getDeclaredMethod(
                    "setTiles", Collection.class, boolean.class));
            updateIcon = accessible(iconImplClass.getDeclaredMethod("updateIcon",
                    ImageView.class, tileStateClass, boolean.class, boolean.class));
            brightnessFinishInflate = accessible(
                    brightnessSliderClass.getDeclaredMethod("onFinishInflate"));
            qsConfigurationChanged = accessible(qsContainerClass.getDeclaredMethod(
                    "onConfigurationChanged", Configuration.class));

            recordsField = accessible(panelClass.getDeclaredField("mRecords"));
            tileViewField = accessible(tileRecordClass.getDeclaredField("tileView"));
            mIconFrame = accessible(tileBaseClass.getDeclaredField("mIconFrame"));
            mIcon = accessible(tileBaseClass.getDeclaredField("mIcon"));
            imageField = accessible(iconImplClass.getDeclaredField("mIcon"));
            mAnimator = accessible(iconImplClass.getDeclaredField("mAnimator"));
            stateField = accessible(tileStateClass.getField("state"));
            brightnessSliderField = accessible(brightnessSliderClass.getDeclaredField("mSlider"));
            brightnessViewField = accessible(qsContainerClass.getDeclaredField("brightnessView"));

            ControlCenterNativeMaterialController material =
                    new ControlCenterNativeMaterialController(imageField, mAnimator);
            BrightnessSliderNativeMaterialController brightnessMaterial =
                    new BrightnessSliderNativeMaterialController();
            adapter = new ControlCenterGlassAdapter(
                    glassCore,
                    material,
                    brightnessMaterial,
                    recordsField,
                    tileViewField,
                    mIconFrame,
                    mIcon,
                    brightnessSliderField,
                    brightnessViewField);
        } catch (ClassNotFoundException | NoSuchMethodException | NoSuchFieldException error) {
            return HookInstallResult.unsupported(HOOK_ID,
                    "classic QS glass contract missing: " + error);
        } catch (Throwable error) {
            return HookInstallResult.failed(HOOK_ID,
                    "classic QS glass contract resolution failed", error);
        }

        List<Runnable> rollbacks = new ArrayList<>();
        try {
            // Authoritative membership reconciliation: this method removes all previous records,
            // creates the replacement MiuiQSTileViews, and adds them to the active tile layout.
            rollbacks.add(afterBackend.intercept(
                    setTiles,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        try {
                            adapter.reconcilePanel(thisObject);
                        } catch (Throwable error) {
                            android.util.Log.e("LiquidUI",
                                    "[LUI][QSGlass] setTiles reconcile failed", error);
                        }
                    })::unhook);

            // updateIcon replaces the native LayerDrawable on every meaningful tile-state change.
            // Re-apply only native background suppression; foreground icon remains vendor-owned.
            rollbacks.add(afterBackend.intercept(
                    updateIcon,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        try {
                            if (!(thisObject instanceof View)) return;
                            if (args.length < 2 || args[1] == null) return;
                            adapter.onIconUpdated(thisObject, stateField.getInt(args[1]) == 2);
                        } catch (Throwable error) {
                            android.util.Log.e("LiquidUI",
                                    "[LUI][QSGlass] icon material refresh failed", error);
                        }
                    })::unhook);

            // mSlider is final only after BrightnessSliderView finished inflating its exact layout.
            rollbacks.add(afterBackend.intercept(
                    brightnessFinishInflate,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        try {
                            adapter.registerBrightnessView(thisObject);
                        } catch (Throwable error) {
                            android.util.Log.e("LiquidUI",
                                    "[LUI][QSGlass] brightness registration failed", error);
                        }
                    })::unhook);

            // UI-mode changes replace the whole progress LayerDrawable. Refresh only this exact
            // container's brightness host after the vendor replacement has completed.
            rollbacks.add(afterBackend.intercept(
                    qsConfigurationChanged,
                    AfterMethodHookBackend.PRIORITY_HIGHEST,
                    (thisObject, args) -> {
                        try {
                            adapter.refreshBrightnessContainer(thisObject);
                        } catch (Throwable error) {
                            android.util.Log.e("LiquidUI",
                                    "[LUI][QSGlass] brightness config refresh failed", error);
                        }
                    })::unhook);

            android.util.Log.i("LiquidUI",
                    "[LUI][QSGlass] installed verified classic-QS tile + brightness adapters");
            return HookInstallResult.installed(HOOK_ID);
        } catch (Throwable error) {
            for (int index = rollbacks.size() - 1; index >= 0; index--) {
                try { rollbacks.get(index).run(); } catch (Throwable rollback) {
                    error.addSuppressed(rollback);
                }
            }
            try { adapter.close(); } catch (Throwable rollback) { error.addSuppressed(rollback); }
            return HookInstallResult.failed(HOOK_ID,
                    "classic QS glass hook registration failed", error);
        }
    }

    private static <T extends java.lang.reflect.AccessibleObject> T accessible(T value) {
        value.setAccessible(true);
        return value;
    }
}
