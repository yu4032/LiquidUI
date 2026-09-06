package com.hellovoid.liquidui.glass.notification;

import android.view.Surface;
import android.view.SurfaceControl;
import android.view.View;

import com.hellovoid.liquidui.Api101Bridge;
import com.hellovoid.liquidui.diagnostics.LiquidUiLog;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Binds a WM Shell RootTaskDisplayArea source into a caller-owned PassBlur producer surface. */
final class SystemUiPassBlurBridge {
    private static final String TAG = "[NotifGlass][PBGL]";
    private static final float SCALE = 1.0f;

    static final class Binding {
        final SurfaceControl sourceSurface;
        final SurfaceControl hostRootSurface;
        final Method setPassBlurSurface;
        final Method setUpdateTextureFlag;
        final String sourceName;
        final String hostRootName;
        final int viewRootIdentity;
        final int surfaceSequenceId;
        final int sourceLayerId;
        final long sourceGeneration;
        final long endpointGeneration;
        boolean bound = true;
        boolean updatesEnabled = true;

        Binding(SurfaceControl sourceSurface,
                SurfaceControl hostRootSurface,
                Method setPassBlurSurface,
                Method setUpdateTextureFlag,
                String sourceName,
                String hostRootName,
                int viewRootIdentity,
                int surfaceSequenceId,
                int sourceLayerId,
                long sourceGeneration,
                long endpointGeneration) {
            this.sourceSurface = sourceSurface;
            this.hostRootSurface = hostRootSurface;
            this.setPassBlurSurface = setPassBlurSurface;
            this.setUpdateTextureFlag = setUpdateTextureFlag;
            this.sourceName = sourceName;
            this.hostRootName = hostRootName;
            this.viewRootIdentity = viewRootIdentity;
            this.surfaceSequenceId = surfaceSequenceId;
            this.sourceLayerId = sourceLayerId;
            this.sourceGeneration = sourceGeneration;
            this.endpointGeneration = endpointGeneration;
        }
    }

    private SystemUiPassBlurBridge() {}

    static Binding bind(View materialHost,
                        SurfaceControl sourceSurface,
                        long sourceGeneration,
                        Surface producerSurface,
                        long endpointGeneration) {
        if (materialHost == null || sourceSurface == null || producerSurface == null) return null;
        if (!sourceSurface.isValid()) return null;
        try {
            Object viewRoot = getViewRootImpl(materialHost);
            if (viewRoot == null) {
                log("bind unavailable ViewRootImpl=null");
                return null;
            }
            Method getSurfaceControl = viewRoot.getClass().getDeclaredMethod("getSurfaceControl");
            getSurfaceControl.setAccessible(true);
            Object hostValue = getSurfaceControl.invoke(viewRoot);
            if (!(hostValue instanceof SurfaceControl hostRootSurface) || !hostRootSurface.isValid()) {
                log("bind unavailable host root invalid");
                return null;
            }

            Class<?> tx = SurfaceControl.Transaction.class;
            Method setPassBlurSurface = tx.getMethod(
                    "SetPassBlurSurface", SurfaceControl.class, Surface.class);
            Method setUpdateTextureFlag = tx.getMethod(
                    "setUpdateTextureFlag", SurfaceControl.class, boolean.class, float.class);

            String sourceName = surfaceName(sourceSurface);
            String hostRootName = surfaceName(hostRootSurface);
            try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
                setPassBlurSurface.invoke(transaction, sourceSurface, producerSurface);
                setUpdateTextureFlag.invoke(transaction, sourceSurface, true, SCALE);
                transaction.apply();
            }

            Binding binding = new Binding(
                    sourceSurface,
                    hostRootSurface,
                    setPassBlurSurface,
                    setUpdateTextureFlag,
                    sourceName,
                    hostRootName,
                    System.identityHashCode(viewRoot),
                    readSurfaceSequenceId(viewRoot),
                    surfaceLayerId(sourceSurface),
                    sourceGeneration,
                    endpointGeneration);
            log("bound source=" + sourceName
                    + " sourceLayer=" + binding.sourceLayerId
                    + " sourceGen=" + sourceGeneration
                    + " hostRoot=" + hostRootName
                    + " surfaceSeq=" + binding.surfaceSequenceId
                    + " viewRoot=" + binding.viewRootIdentity
                    + " endpointGen=" + endpointGeneration
                    + " sourceAuthority=RootTaskDisplayArea");
            return binding;
        } catch (Throwable error) {
            log("bind unavailable " + error);
            return null;
        }
    }

    static void resumeUpdates(Binding binding) { setUpdatesEnabled(binding, true); }
    static void pauseUpdates(Binding binding) { setUpdatesEnabled(binding, false); }

    private static void setUpdatesEnabled(Binding binding, boolean enabled) {
        if (binding == null || !binding.bound || !binding.sourceSurface.isValid()) return;
        if (binding.updatesEnabled == enabled) return;
        try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
            binding.setUpdateTextureFlag.invoke(
                    transaction, binding.sourceSurface, enabled, SCALE);
            transaction.apply();
            binding.updatesEnabled = enabled;
            log("updates=" + enabled + " source=" + binding.sourceName
                    + " sourceGen=" + binding.sourceGeneration
                    + " endpointGen=" + binding.endpointGeneration);
        } catch (Throwable error) {
            log("update toggle failed " + error);
        }
    }

    static void unbind(Binding binding) {
        if (binding == null || !binding.bound) return;
        try {
            if (binding.sourceSurface.isValid()) {
                try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
                    binding.setPassBlurSurface.invoke(transaction, binding.sourceSurface, null);
                    binding.setUpdateTextureFlag.invoke(transaction, binding.sourceSurface, false, SCALE);
                    transaction.apply();
                }
            }
        } catch (Throwable error) {
            log("unbind failed " + error);
        } finally {
            binding.bound = false;
            binding.updatesEnabled = false;
        }
    }

    static int readSurfaceSequenceId(Object viewRoot) {
        if (viewRoot == null) return -1;
        Class<?> type = viewRoot.getClass();
        while (type != null) {
            try {
                Method method = type.getDeclaredMethod("getSurfaceSequenceId");
                method.setAccessible(true);
                Object value = method.invoke(viewRoot);
                return value instanceof Number ? ((Number) value).intValue() : -1;
            } catch (NoSuchMethodException ignored) {
                type = type.getSuperclass();
            } catch (Throwable error) {
                break;
            }
        }
        type = viewRoot.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField("mSurfaceSequenceId");
                field.setAccessible(true);
                Object value = field.get(viewRoot);
                return value instanceof Number ? ((Number) value).intValue() : -1;
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            } catch (Throwable error) {
                return -1;
            }
        }
        return -1;
    }

    static int surfaceLayerId(SurfaceControl surface) {
        if (surface == null) return -1;
        try {
            Method method = SurfaceControl.class.getDeclaredMethod("getLayerId");
            method.setAccessible(true);
            Object value = method.invoke(surface);
            return value instanceof Number ? ((Number) value).intValue() : -1;
        } catch (Throwable ignored) {
            return -1;
        }
    }

    static Object getViewRootImpl(View view) throws Exception {
        Method method = View.class.getDeclaredMethod("getViewRootImpl");
        method.setAccessible(true);
        return method.invoke(view);
    }

    private static String surfaceName(SurfaceControl surface) {
        try {
            Method method = SurfaceControl.class.getDeclaredMethod("getName");
            method.setAccessible(true);
            Object value = method.invoke(surface);
            return value instanceof String ? (String) value : String.valueOf(surface);
        } catch (Throwable ignored) {
            return String.valueOf(surface);
        }
    }

    private static void log(String message) {
        try {
            Api101Bridge.log(LiquidUiLog.format(TAG + " " + message));
        } catch (Throwable ignored) {
            android.util.Log.i("LiquidUI", "[LUI]" + TAG + " " + message);
        }
    }
}
