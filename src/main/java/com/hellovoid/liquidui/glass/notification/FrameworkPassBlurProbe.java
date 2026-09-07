package com.hellovoid.liquidui.glass.notification;

import android.graphics.SurfaceTexture;
import android.view.SurfaceControl;
import android.view.TextureView;
import android.view.View;

import com.hellovoid.liquidui.Api101Bridge;
import com.hellovoid.liquidui.diagnostics.LiquidUiLog;

import java.lang.ref.Reference;
import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Read-only runtime probe for HyperOS/framework-owned PassBlur consumer objects.
 *
 * <p>This intentionally does not call addTextureView, producer-binding transactions or any
 * mutating blur API. It only inspects a live NotificationShade ViewRoot object graph so device
 * logs can reveal the framework-owned consumer and its TextureView/SurfaceTexture holders without
 * disturbing the proven RTDA->Prismal path.</p>
 */
final class FrameworkPassBlurProbe {
    private static final String TAG = "[NotifGlass][FrameworkPB]";
    private static final int MAX_OBJECTS = 56;
    private static final int MAX_INTERESTING_FIELDS_PER_OBJECT = 48;
    private static final int MAX_INTERESTING_METHODS_PER_OBJECT = 48;
    private static final int MAX_FINGERPRINT_FIELDS = 24;
    private static final AtomicLong probeGeneration = new AtomicLong(1L);
    private static final Map<Integer, InspectionStamp> INSPECTIONS =
            Collections.synchronizedMap(new HashMap<>());
    private static volatile WeakReference<View> panelRef = new WeakReference<>(null);

    private static final class InspectionStamp {
        final long generation;
        final int fingerprint;

        InspectionStamp(long generation, int fingerprint) {
            this.generation = generation;
            this.fingerprint = fingerprint;
        }
    }

    private FrameworkPassBlurProbe() {}

    static void inspectOnce(View notificationPanelView) {
        if (notificationPanelView == null) return;
        panelRef = new WeakReference<>(notificationPanelView);
        registerShadeRootFromView(notificationPanelView);
        notificationPanelView.post(() -> inspect(notificationPanelView, true));
    }

    static void inspectIfGenerationChanged(View notificationPanelView) {
        if (notificationPanelView == null) return;
        panelRef = new WeakReference<>(notificationPanelView);
        // Root registration is deliberately synchronous. A session/material-host trigger runs
        // before LiquidUI's diagnostic producer can bind, so transaction observations cannot miss
        // the first ownership handoff while the heavier object-graph walk remains posted.
        registerShadeRootFromView(notificationPanelView);
        notificationPanelView.post(() -> inspect(notificationPanelView, false));
    }

    static void onMatchingShadeTransaction() {
        probeGeneration.incrementAndGet();
        View panel = panelRef.get();
        if (panel != null) inspectIfGenerationChanged(panel);
    }

    private static void registerShadeRootFromView(View view) {
        if (view == null || !view.isAttachedToWindow()) return;
        try {
            Method getViewRootImpl = View.class.getDeclaredMethod("getViewRootImpl");
            getViewRootImpl.setAccessible(true);
            Object viewRoot = getViewRootImpl.invoke(view);
            if (viewRoot != null) registerShadeRoot(viewRoot);
        } catch (Throwable error) {
            log("shade root pre-registration failed " + error.getClass().getSimpleName());
        }
    }

    private static void inspect(View panel, boolean force) {
        Set<Object> visited = Collections.newSetFromMap(new IdentityHashMap<>());
        AtomicInteger objectBudget = new AtomicInteger(MAX_OBJECTS);
        try {
            Method getViewRootImpl = View.class.getDeclaredMethod("getViewRootImpl");
            getViewRootImpl.setAccessible(true);
            Object viewRoot = getViewRootImpl.invoke(panel);
            if (viewRoot == null) {
                log("defer ViewRootImpl=null panel=" + describe(panel));
                return;
            }
            registerShadeRoot(viewRoot);
            int rootIdentity = System.identityHashCode(viewRoot);
            long generation = probeGeneration.get();
            int fingerprint = inspectionFingerprint(viewRoot);
            InspectionStamp previous = INSPECTIONS.get(rootIdentity);
            if (!force && previous != null
                    && previous.generation == generation
                    && previous.fingerprint == fingerprint) {
                return;
            }
            INSPECTIONS.put(rootIdentity, new InspectionStamp(generation, fingerprint));
            log("begin root=" + Integer.toHexString(rootIdentity)
                    + " generation=" + generation
                    + " fingerprint=" + Integer.toHexString(fingerprint)
                    + " panel=" + describe(panel));
            inspectObject("panel", panel, 0, visited, objectBudget);
            log("getViewRootImpl=" + describe(viewRoot));
            inspectObject("viewRoot", viewRoot, 0, visited, objectBudget);
            Object renderer = readFieldByName(viewRoot, "mThreadedRenderer");
            log("mThreadedRenderer=" + describe(renderer));
            inspectObject("mThreadedRenderer", renderer, 0, visited, objectBudget);
        } catch (Throwable error) {
            log("root probe failed " + error.getClass().getSimpleName() + ":" + error.getMessage());
        }
        log("end visited=" + visited.size() + " remainingBudget=" + objectBudget.get());
    }

    private static int inspectionFingerprint(Object viewRoot) {
        int fingerprint = 17;
        fingerprint = 31 * fingerprint + System.identityHashCode(viewRoot);
        Object renderer = readFieldByName(viewRoot, "mThreadedRenderer");
        fingerprint = 31 * fingerprint + System.identityHashCode(renderer);
        fingerprint = mixInterestingChildren(fingerprint, viewRoot);
        if (renderer != null) fingerprint = mixInterestingChildren(fingerprint, renderer);
        return fingerprint;
    }

    private static int mixInterestingChildren(int seed, Object value) {
        int fingerprint = seed;
        int seen = 0;
        for (Class<?> cursor = value.getClass(); cursor != null && seen < MAX_FINGERPRINT_FIELDS;
                cursor = cursor.getSuperclass()) {
            Field[] fields;
            try {
                fields = cursor.getDeclaredFields();
            } catch (Throwable ignored) {
                continue;
            }
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                String fieldName = field.getName();
                String fieldType = field.getType().getName();
                if (!interesting(fieldName) && !interesting(fieldType)) continue;
                if (seen++ >= MAX_FINGERPRINT_FIELDS) break;
                try {
                    field.setAccessible(true);
                    Object child = field.get(value);
                    if (child instanceof Reference<?> reference) child = reference.get();
                    fingerprint = 31 * fingerprint + System.identityHashCode(child);
                } catch (Throwable ignored) {
                    fingerprint = 31 * fingerprint + fieldName.hashCode();
                }
            }
        }
        return fingerprint;
    }

    private static void registerShadeRoot(Object viewRoot) {
        if (viewRoot == null) return;
        try {
            Method getSurfaceControl = viewRoot.getClass().getDeclaredMethod("getSurfaceControl");
            getSurfaceControl.setAccessible(true);
            Object surface = getSurfaceControl.invoke(viewRoot);
            if (surface instanceof SurfaceControl root && root.isValid()) {
                FrameworkPassBlurTransactionProbe.registerShadeRoot(root);
            }
        } catch (Throwable error) {
            log("shade root registration failed " + error.getClass().getSimpleName());
        }
    }

    private static void inspectObject(
            String path,
            Object value,
            int depth,
            Set<Object> visited,
            AtomicInteger objectBudget) {
        if (value == null || depth > 2 || objectBudget.getAndDecrement() <= 0) return;
        if (!visited.add(value)) return;

        Class<?> type = value.getClass();
        boolean objectInteresting = interesting(type.getName())
                || value instanceof TextureView
                || value instanceof SurfaceTexture;
        if (objectInteresting || depth == 0) {
            log("object path=" + path + " value=" + describe(value));
        }
        inspectInterestingMethods(path, type);

        int interestingFieldsSeen = 0;
        for (Class<?> cursor = type; cursor != null
                && interestingFieldsSeen < MAX_INTERESTING_FIELDS_PER_OBJECT;
                cursor = cursor.getSuperclass()) {
            Field[] fields;
            try {
                fields = cursor.getDeclaredFields();
            } catch (Throwable ignored) {
                continue;
            }
            for (Field field : fields) {
                if (Modifier.isStatic(field.getModifiers())) continue;
                String fieldType = field.getType().getName();
                String fieldName = field.getName();
                Object child = null;
                boolean metadataInteresting = interesting(fieldName) || interesting(fieldType);
                try {
                    field.setAccessible(true);
                    child = field.get(value);
                    if (child instanceof Reference<?> reference) child = reference.get();
                } catch (Throwable error) {
                    if (metadataInteresting) {
                        interestingFieldsSeen++;
                        log("field path=" + path + "." + fieldName
                                + " declared=" + fieldType + " inaccessible="
                                + error.getClass().getSimpleName());
                    }
                    continue;
                }
                boolean runtimeInteresting = child != null
                        && (interesting(child.getClass().getName())
                        || hasInterestingMethod(child.getClass())
                        || child instanceof TextureView
                        || child instanceof SurfaceTexture);
                if (!metadataInteresting && !runtimeInteresting) continue;
                if (interestingFieldsSeen++ >= MAX_INTERESTING_FIELDS_PER_OBJECT) break;
                log("field path=" + path + "." + fieldName
                        + " declared=" + fieldType + " value=" + describe(child));
                if (child != null && shouldDescend(fieldName, fieldType, child)) {
                    inspectObject(path + "." + fieldName, child, depth + 1, visited, objectBudget);
                    inspectIterable(path + "." + fieldName, child, depth + 1, visited, objectBudget);
                }
            }
        }
    }

    private static void inspectInterestingMethods(String path, Class<?> type) {
        int interestingMethodsSeen = 0;
        for (Class<?> cursor = type; cursor != null
                && interestingMethodsSeen < MAX_INTERESTING_METHODS_PER_OBJECT;
                cursor = cursor.getSuperclass()) {
            Method[] methods;
            try {
                methods = cursor.getDeclaredMethods();
            } catch (Throwable ignored) {
                continue;
            }
            for (Method method : methods) {
                String name = method.getName();
                if (!interestingMethod(name)) continue;
                if (interestingMethodsSeen++ >= MAX_INTERESTING_METHODS_PER_OBJECT) break;
                log("method path=" + path + " owner=" + cursor.getName()
                        + " sig=" + signature(method));
            }
        }
    }

    private static void inspectIterable(
            String path,
            Object value,
            int depth,
            Set<Object> visited,
            AtomicInteger objectBudget) {
        if (!(value instanceof Iterable<?> iterable) || depth > 2) return;
        int index = 0;
        try {
            for (Object item : iterable) {
                if (index >= 8) break;
                if (item != null && (interesting(item.getClass().getName())
                        || item instanceof TextureView
                        || item instanceof SurfaceTexture)) {
                    log("item path=" + path + "[" + index + "] value=" + describe(item));
                    inspectObject(path + "[" + index + "]", item, depth + 1, visited, objectBudget);
                }
                index++;
            }
        } catch (Throwable error) {
            log("iterable path=" + path + " failed=" + error.getClass().getSimpleName());
        }
    }

    private static Object readFieldByName(Object receiver, String name) {
        if (receiver == null) return null;
        for (Class<?> cursor = receiver.getClass(); cursor != null; cursor = cursor.getSuperclass()) {
            try {
                Field field = cursor.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(receiver);
            } catch (NoSuchFieldException ignored) {
                // Continue up the hierarchy.
            } catch (Throwable error) {
                log("read " + name + " failed=" + error.getClass().getSimpleName());
                return null;
            }
        }
        return null;
    }

    private static boolean hasInterestingMethod(Class<?> type) {
        for (Class<?> cursor = type; cursor != null; cursor = cursor.getSuperclass()) {
            try {
                for (Method method : cursor.getDeclaredMethods()) {
                    if (interestingMethod(method.getName())) return true;
                }
            } catch (Throwable ignored) {
            }
        }
        return false;
    }

    private static boolean shouldDescend(String fieldName, String fieldType, Object value) {
        String valueType = value.getClass().getName();
        return interesting(fieldName)
                || interesting(fieldType)
                || interesting(valueType)
                || value instanceof TextureView
                || value instanceof SurfaceTexture;
    }

    private static boolean interesting(String value) {
        if (value == null) return false;
        String s = value.toLowerCase(Locale.ROOT);
        return s.contains("pass")
                || s.contains("blur")
                || s.contains("texture")
                || s.contains("surface")
                || s.contains("render");
    }

    private static boolean interestingMethod(String value) {
        if (value == null) return false;
        String s = value.toLowerCase(Locale.ROOT);
        return s.contains("addtextureview")
                || s.contains("removetextureview")
                || s.contains("texture")
                || s.contains("passblur")
                || s.contains("passwindowblur")
                || s.contains("surface")
                || s.contains("blur");
    }

    private static String signature(Method method) {
        StringBuilder out = new StringBuilder();
        out.append(method.getReturnType().getSimpleName())
                .append(' ').append(method.getName()).append('(');
        Class<?>[] params = method.getParameterTypes();
        for (int i = 0; i < params.length; i++) {
            if (i > 0) out.append(',');
            out.append(params[i].getSimpleName());
        }
        return out.append(')').toString();
    }

    private static String describe(Object value) {
        if (value == null) return "null";
        String extra = "";
        if (value instanceof TextureView textureView) {
            SurfaceTexture texture = null;
            try { texture = textureView.getSurfaceTexture(); } catch (Throwable ignored) {}
            extra = " textureAvailable=" + textureView.isAvailable()
                    + " surfaceTexture=" + identity(texture);
        } else if (value instanceof SurfaceTexture) {
            extra = " SurfaceTexture";
        }
        return value.getClass().getName() + "@" + Integer.toHexString(System.identityHashCode(value))
                + extra;
    }

    private static String identity(Object value) {
        return value == null ? "null"
                : value.getClass().getName() + "@"
                        + Integer.toHexString(System.identityHashCode(value));
    }

    private static void log(String message) {
        try {
            Api101Bridge.log(LiquidUiLog.format(TAG + " " + message));
        } catch (Throwable ignored) {
            android.util.Log.i("LiquidUI", "[LUI]" + TAG + " " + message);
        }
    }
}
