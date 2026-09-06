package com.hellovoid.liquidui.glass.notification;

import android.graphics.SurfaceTexture;
import android.view.SurfaceControl;
import android.view.TextureView;
import android.view.View;

import com.hellovoid.liquidui.Api101Bridge;
import com.hellovoid.liquidui.diagnostics.LiquidUiLog;

import java.lang.ref.Reference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.Locale;
import java.util.Set;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * Read-only runtime probe for HyperOS/framework-owned PassBlur consumer objects.
 *
 * <p>This intentionally does not call addTextureView, producer-binding transactions or any
 * mutating blur API. It only inspects the live NotificationPanelView ViewRoot object graph after
 * HyperOS itself enables pass-window blur, so device logs can reveal the framework-owned consumer
 * and its TextureView/SurfaceTexture holders without disturbing the proven RTDA->Prismal path.</p>
 */
final class FrameworkPassBlurProbe {
    private static final String TAG = "[NotifGlass][FrameworkPB]";
    private static final Set<Integer> PROBED_ROOTS = Collections.synchronizedSet(new java.util.HashSet<>());
    private static final int MAX_OBJECTS = 56;
    private static final int MAX_INTERESTING_FIELDS_PER_OBJECT = 48;
    private static final int MAX_INTERESTING_METHODS_PER_OBJECT = 48;

    private FrameworkPassBlurProbe() {}

    static void inspectOnce(View notificationPanelView) {
        if (notificationPanelView == null) return;
        notificationPanelView.post(() -> inspect(notificationPanelView));
    }

    private static void inspect(View panel) {
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
            if (!PROBED_ROOTS.add(rootIdentity)) return;
            log("begin root=" + Integer.toHexString(rootIdentity) + " panel=" + describe(panel));
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
