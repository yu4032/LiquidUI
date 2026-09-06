package com.hellovoid.liquidui.glass.notification;

import android.view.Surface;
import android.view.SurfaceControl;
import android.view.View;

import com.hellovoid.liquidui.Api101Bridge;
import com.hellovoid.liquidui.diagnostics.LiquidUiLog;

import java.lang.reflect.Field;
import java.lang.reflect.Method;

/** Experimental observer endpoint attached to HyperOS's own NotificationShade PassBlur root. */
final class SystemUiPassBlurBridge {
    private static final String TAG = "[NotifGlass][PBGL]";
    // Exact supplied HyperOS runtime and libhyper_surface_context use quarter-scale PassBlur.
    private static final float SCALE = 0.25f;

    static final class Binding {
        final SurfaceControl hostRootSurface;
        final Method setPassBlurSurface;
        final Method setUpdateTextureFlag;
        final String hostRootName;
        final int viewRootIdentity;
        final int surfaceSequenceId;
        final int rootLayerId;
        final long endpointGeneration;
        boolean bound = true;
        boolean updatesEnabled = true;

        Binding(SurfaceControl hostRootSurface,
                Method setPassBlurSurface,
                Method setUpdateTextureFlag,
                String hostRootName,
                int viewRootIdentity,
                int surfaceSequenceId,
                int rootLayerId,
                long endpointGeneration) {
            this.hostRootSurface = hostRootSurface;
            this.setPassBlurSurface = setPassBlurSurface;
            this.setUpdateTextureFlag = setUpdateTextureFlag;
            this.hostRootName = hostRootName;
            this.viewRootIdentity = viewRootIdentity;
            this.surfaceSequenceId = surfaceSequenceId;
            this.rootLayerId = rootLayerId;
            this.endpointGeneration = endpointGeneration;
        }
    }

    private SystemUiPassBlurBridge() {}

    /** Resolve the current ViewRoot SurfaceControl so callers never own stale root state. */
    static Binding bind(View materialHost,
                        Surface producerSurface,
                        long endpointGeneration) {
        if (materialHost == null || producerSurface == null) return null;
        try {
            SurfaceControl rootSurface = resolveRootSurface(materialHost);
            return bind(materialHost, rootSurface, producerSurface, endpointGeneration);
        } catch (Throwable error) {
            log("bind unavailable root resolution " + error);
            return null;
        }
    }

    static Binding bind(View materialHost,
                        SurfaceControl rootSurface,
                        Surface producerSurface,
                        long endpointGeneration) {
        if (materialHost == null || rootSurface == null || producerSurface == null) return null;
        if (!rootSurface.isValid()) return null;
        try {
            Object viewRoot = getViewRootImpl(materialHost);
            if (viewRoot == null) {
                log("bind unavailable ViewRootImpl=null");
                return null;
            }
            Method getSurfaceControl = viewRoot.getClass().getDeclaredMethod("getSurfaceControl");
            getSurfaceControl.setAccessible(true);
            Object rootValue = getSurfaceControl.invoke(viewRoot);
            if (!(rootValue instanceof SurfaceControl hostRootSurface)
                    || !hostRootSurface.isValid()
                    || !isSameSurface(hostRootSurface, rootSurface)) {
                log("bind unavailable NotificationShade root changed");
                return null;
            }

            Class<?> tx = SurfaceControl.Transaction.class;
            Method setPassBlurSurface = tx.getMethod(
                    "SetPassBlurSurface", SurfaceControl.class, Surface.class);
            Method setUpdateTextureFlag = tx.getMethod(
                    "setUpdateTextureFlag", SurfaceControl.class, boolean.class, float.class);

            String hostRootName = surfaceName(hostRootSurface);
            try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
                setPassBlurSurface.invoke(transaction, hostRootSurface, producerSurface);
                setUpdateTextureFlag.invoke(transaction, hostRootSurface, true, SCALE);
                transaction.apply();
            }

            Binding binding = new Binding(
                    hostRootSurface,
                    setPassBlurSurface,
                    setUpdateTextureFlag,
                    hostRootName,
                    System.identityHashCode(viewRoot),
                    readSurfaceSequenceId(viewRoot),
                    surfaceLayerId(hostRootSurface),
                    endpointGeneration);
            log("bound root=" + hostRootName
                    + " rootLayer=" + binding.rootLayerId
                    + " surfaceSeq=" + binding.surfaceSequenceId
                    + " viewRoot=" + binding.viewRootIdentity
                    + " endpointGen=" + endpointGeneration
                    + " scale=" + SCALE
                    + " sourceAuthority=NotificationShadeViewRoot-native");
            return binding;
        } catch (Throwable error) {
            log("bind unavailable " + error);
            return null;
        }
    }

    static void resumeUpdates(Binding binding) { setUpdatesEnabled(binding, true); }
    static void pauseUpdates(Binding binding) { setUpdatesEnabled(binding, false); }

    private static void setUpdatesEnabled(Binding binding, boolean enabled) {
        if (binding == null || !binding.bound || !binding.hostRootSurface.isValid()) return;
        if (binding.updatesEnabled == enabled) return;
        try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
            binding.setUpdateTextureFlag.invoke(
                    transaction, binding.hostRootSurface, enabled, SCALE);
            transaction.apply();
            binding.updatesEnabled = enabled;
            log("updates=" + enabled + " root=" + binding.hostRootName
                    + " endpointGen=" + binding.endpointGeneration
                    + " scale=" + SCALE);
        } catch (Throwable error) {
            log("update toggle failed " + error);
        }
    }

    static void unbind(Binding binding) {
        if (binding == null || !binding.bound) return;
        try {
            if (binding.hostRootSurface.isValid()) {
                try (SurfaceControl.Transaction transaction = new SurfaceControl.Transaction()) {
                    setPassBlurSurfaceToNull(binding, transaction);
                    binding.setUpdateTextureFlag.invoke(
                            transaction, binding.hostRootSurface, false, SCALE);
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

    private static void setPassBlurSurfaceToNull(
            Binding binding, SurfaceControl.Transaction transaction) throws Exception {
        binding.setPassBlurSurface.invoke(transaction, binding.hostRootSurface, null);
    }

    static SurfaceControl resolveRootSurface(View view) throws Exception {
        Object viewRoot = getViewRootImpl(view);
        if (viewRoot == null) return null;
        Method getSurfaceControl = viewRoot.getClass().getDeclaredMethod("getSurfaceControl");
        getSurfaceControl.setAccessible(true);
        Object value = getSurfaceControl.invoke(viewRoot);
        return value instanceof SurfaceControl ? (SurfaceControl) value : null;
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

    private static boolean isSameSurface(SurfaceControl first, SurfaceControl second) {
        if (first == second) return true;
        if (first == null || second == null) return false;
        try {
            Method method = SurfaceControl.class.getMethod("isSameSurface", SurfaceControl.class);
            Object value = method.invoke(first, second);
            return value instanceof Boolean && (Boolean) value;
        } catch (Throwable ignored) {
            return first.equals(second);
        }
    }

    private static void log(String message) {
        String formatted = LiquidUiLog.format(TAG + " " + message);
        android.util.Log.i("LiquidUI", formatted);
        try { Api101Bridge.log(formatted); } catch (Throwable ignored) {}
    }
}
