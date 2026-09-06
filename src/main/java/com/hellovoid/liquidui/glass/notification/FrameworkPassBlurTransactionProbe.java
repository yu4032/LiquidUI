package com.hellovoid.liquidui.glass.notification;

import android.view.Surface;
import android.view.SurfaceControl;

import com.hellovoid.liquidui.Api101Bridge;
import com.hellovoid.liquidui.diagnostics.LiquidUiLog;

import java.lang.ref.WeakReference;
import java.lang.reflect.Method;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/** Read-only ownership observer for the live NotificationShade PassBlur endpoint. */
final class FrameworkPassBlurTransactionProbe {
    private static final String TAG = "[NotifGlass][FrameworkPB][TX]";
    private static final int MAX_STACK_EVENTS = 12;
    private static final int MAX_STACK_FRAMES = 14;
    private static final AtomicLong sequence = new AtomicLong();
    private static final AtomicInteger stackEvents = new AtomicInteger();

    private static volatile WeakReference<SurfaceControl> shadeRootRef = new WeakReference<>(null);
    private static volatile int shadeRootIdentity;
    private static volatile int shadeRootLayerId = -1;
    private static volatile String shadeRootName = "unknown";
    private static volatile int lastProducerIdentity = Integer.MIN_VALUE;
    private static volatile boolean lastUpdateKnown;
    private static volatile boolean lastUpdateEnabled;
    private static volatile int lastUpdateScaleBits;

    private FrameworkPassBlurTransactionProbe() {}

    static void registerShadeRoot(SurfaceControl root) {
        if (root == null || !root.isValid()) return;
        int identity = System.identityHashCode(root);
        int layerId = surfaceLayerId(root);
        String name = surfaceName(root);
        boolean changed = shadeRootIdentity != identity
                || (layerId >= 0 && shadeRootLayerId != layerId);
        shadeRootRef = new WeakReference<>(root);
        shadeRootIdentity = identity;
        shadeRootLayerId = layerId;
        shadeRootName = name;
        if (changed) {
            lastProducerIdentity = Integer.MIN_VALUE;
            lastUpdateKnown = false;
            stackEvents.set(0);
            log("shade root registered identity=" + hex(identity)
                    + " layerId=" + layerId + " name=" + name);
        }
    }

    static void observeSetPassBlurSurface(Object transaction, Object[] args) {
        if (args == null || args.length < 2 || !matchesShadeRoot(args[0])) return;
        Object producer = args[1];
        if (producer != null && !(producer instanceof Surface)) return;
        long seq = sequence.incrementAndGet();
        int producerIdentity = producer == null ? 0 : System.identityHashCode(producer);
        boolean changed = producerIdentity != lastProducerIdentity;
        lastProducerIdentity = producerIdentity;
        logEvent(seq, "SetPassBlurSurface",
                "producer=" + describe(producer), changed);
    }

    static void observeSetUpdateTextureFlag(Object transaction, Object[] args) {
        if (args == null || args.length < 3 || !matchesShadeRoot(args[0])) return;
        if (!(args[1] instanceof Boolean enabled) || !(args[2] instanceof Number scaleValue)) return;
        float scale = scaleValue.floatValue();
        int scaleBits = Float.floatToIntBits(scale);
        boolean changed = !lastUpdateKnown
                || lastUpdateEnabled != enabled
                || lastUpdateScaleBits != scaleBits;
        lastUpdateKnown = true;
        lastUpdateEnabled = enabled;
        lastUpdateScaleBits = scaleBits;
        long seq = sequence.incrementAndGet();
        logEvent(seq, "setUpdateTextureFlag",
                "enabled=" + enabled + " scale=" + scale, changed);
    }

    private static boolean matchesShadeRoot(Object candidate) {
        if (!(candidate instanceof SurfaceControl surface) || !surface.isValid()) return false;
        SurfaceControl root = shadeRootRef.get();
        if (root != null && root == surface) return true;
        int candidateLayer = surfaceLayerId(surface);
        if (shadeRootLayerId >= 0 && candidateLayer >= 0) {
            return candidateLayer == shadeRootLayerId;
        }
        return System.identityHashCode(surface) == shadeRootIdentity;
    }

    private static void logEvent(long seq, String operation, String details, boolean changed) {
        boolean withStack = changed || stackEvents.get() < MAX_STACK_EVENTS;
        String thread = Thread.currentThread().getName() + "#" + Thread.currentThread().getId();
        String prefix = "sequence=" + seq
                + " op=" + operation
                + " root=" + shadeRootName
                + " rootIdentity=" + hex(shadeRootIdentity)
                + " layerId=" + shadeRootLayerId
                + " thread=" + thread
                + " " + details;
        if (!withStack) {
            log(prefix);
            return;
        }
        if (stackEvents.getAndIncrement() >= MAX_STACK_EVENTS && !changed) {
            log(prefix);
            return;
        }
        StackTraceElement[] stack = Thread.currentThread().getStackTrace();
        StringBuilder out = new StringBuilder(prefix).append(" stack=");
        int emitted = 0;
        for (StackTraceElement frame : stack) {
            String owner = frame.getClassName();
            if (owner.equals(Thread.class.getName())
                    || owner.startsWith("com.hellovoid.liquidui.glass.notification.FrameworkPassBlur")) {
                continue;
            }
            if (emitted++ >= MAX_STACK_FRAMES) break;
            if (emitted > 1) out.append(" <- ");
            out.append(owner).append('.').append(frame.getMethodName())
                    .append(':').append(frame.getLineNumber());
        }
        log(out.toString());
    }

    private static int surfaceLayerId(SurfaceControl surface) {
        try {
            Method method = SurfaceControl.class.getDeclaredMethod("getLayerId");
            method.setAccessible(true);
            Object value = method.invoke(surface);
            return value instanceof Number ? ((Number) value).intValue() : -1;
        } catch (Throwable ignored) {
            return -1;
        }
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

    private static String describe(Object value) {
        if (value == null) return "null";
        return value.getClass().getName() + "@" + hex(System.identityHashCode(value));
    }

    private static String hex(int value) {
        return Integer.toHexString(value);
    }

    private static void log(String message) {
        try {
            Api101Bridge.log(LiquidUiLog.format(TAG + " " + message));
        } catch (Throwable ignored) {
            android.util.Log.i("LiquidUI", "[LUI]" + TAG + " " + message);
        }
    }
}
