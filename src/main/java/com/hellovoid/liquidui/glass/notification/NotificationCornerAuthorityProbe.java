package com.hellovoid.liquidui.glass.notification;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewParent;

import com.hellovoid.liquidui.Api101Bridge;
import com.hellovoid.liquidui.diagnostics.LiquidUiLog;

import java.lang.reflect.Field;
import java.util.WeakHashMap;

/** Read-only evidence for the remaining notification rounded-outline authority. */
final class NotificationCornerAuthorityProbe {
    private static final String TAG = "[NotifGlass][CornerProbe]";
    private static final int MAX_PARENT_DEPTH = 8;
    private static final WeakHashMap<View, Boolean> NODE_LOGGED = new WeakHashMap<>();
    private static final WeakHashMap<View, Boolean> MAPPING_LOGGED = new WeakHashMap<>();

    private NotificationCornerAuthorityProbe() {}

    static void observeNode(
            Object rowObject,
            View row,
            View background,
            View host,
            NotificationGlassNode node,
            int actualWidth,
            int actualHeight,
            int clipTop,
            int clipBottom,
            boolean expand,
            float topRadius,
            float bottomRadius) {
        if (rowObject == null || row == null || background == null || host == null || node == null) {
            return;
        }
        synchronized (NODE_LOGGED) {
            if (NODE_LOGGED.containsKey(host)) return;
            NODE_LOGGED.put(host, Boolean.TRUE);
        }
        try {
            log("node authority"
                    + " row=" + describeView(row)
                    + " background=" + describeView(background)
                    + " actualWidth=" + actualWidth
                    + " actualHeight=" + actualHeight
                    + " clipTop=" + clipTop
                    + " clipBottom=" + clipBottom
                    + " expand=" + expand
                    + " topRadius=" + topRadius
                    + " bottomRadius=" + bottomRadius
                    + " node=[" + node.left + "," + node.top + ","
                    + node.width + "," + node.height + "]"
                    + " radii=[" + node.topLeftRadius + "," + node.topRightRadius + ","
                    + node.bottomRightRadius + "," + node.bottomLeftRadius + "]"
                    + " opacity=" + node.opacity);
            logHierarchy("row", row, host);
            if (background != row) logHierarchy("background", background, host);
        } catch (Throwable error) {
            log("node authority failed=" + error.getClass().getSimpleName()
                    + ":" + error.getMessage());
        }
    }

    static void observeMapping(View renderer) {
        if (renderer == null) return;
        synchronized (MAPPING_LOGGED) {
            if (MAPPING_LOGGED.containsKey(renderer)) return;
            MAPPING_LOGGED.put(renderer, Boolean.TRUE);
        }
        try {
            Object snapshot = readField(renderer, "backdropSnapshot");
            if (snapshot == null) {
                log("mapping unavailable backdropSnapshot=null renderer=" + describeView(renderer));
                return;
            }
            int visibleWidth = intField(snapshot, "visibleWidth");
            int visibleHeight = intField(snapshot, "visibleHeight");
            int sampleWidth = intField(snapshot, "sampleWidth");
            int sampleHeight = intField(snapshot, "sampleHeight");
            float backdropX = floatField(snapshot, "backdropX");
            float backdropY = floatField(snapshot, "backdropY");
            float backdropW = floatField(snapshot, "backdropW");
            float backdropH = floatField(snapshot, "backdropH");
            float validDockLeft = floatField(snapshot, "validDockLeft");
            float validDockBottom = floatField(snapshot, "validDockBottom");
            float validDockRight = floatField(snapshot, "validDockRight");
            float validDockTop = floatField(snapshot, "validDockTop");
            float dockUvLeft = floatField(snapshot, "dockUvLeft");
            float dockUvBottom = floatField(snapshot, "dockUvBottom");
            Object coverage = readField(snapshot, "coverage");

            int left = Math.max(0, Math.round(dockUvLeft * sampleWidth));
            int bottom = Math.max(0, Math.round(dockUvBottom * sampleHeight));
            int right = Math.max(0, sampleWidth - visibleWidth - left);
            int top = Math.max(0, sampleHeight - visibleHeight - bottom);

            log("mapping authority"
                    + " renderer=" + describeView(renderer)
                    + " visible=" + visibleWidth + "x" + visibleHeight
                    + " sample=" + sampleWidth + "x" + sampleHeight
                    + " backdropRect=[" + backdropX + "," + backdropY + ","
                    + backdropW + "," + backdropH + "]"
                    + " validDockRect=[" + validDockLeft + "," + validDockBottom + ","
                    + validDockRight + "," + validDockTop + "]"
                    + " overscanInsets=[" + left + "," + top + "," + right + "," + bottom + "]"
                    + " coverage=" + String.valueOf(coverage));
        } catch (Throwable error) {
            log("mapping authority failed=" + error.getClass().getSimpleName()
                    + ":" + error.getMessage());
        }
    }

    private static void logHierarchy(String origin, View start, View host) {
        View current = start;
        for (int depth = 0; current != null && depth < MAX_PARENT_DEPTH; depth++) {
            StringBuilder line = new StringBuilder()
                    .append("hierarchy origin=").append(origin)
                    .append(" depth=").append(depth)
                    .append(" view=").append(describeView(current))
                    .append(" clipToOutline=").append(current.getClipToOutline())
                    .append(" outlineProvider=").append(className(current.getOutlineProvider()));
            if (current instanceof ViewGroup group) {
                line.append(" clipChildren=").append(group.getClipChildren())
                        .append(" clipToPadding=").append(group.getClipToPadding());
            }
            log(line.toString());
            if (current == host) break;
            ViewParent parent = current.getParent();
            current = parent instanceof View ? (View) parent : null;
        }
    }

    private static String describeView(View view) {
        if (view == null) return "null";
        int[] screen = new int[2];
        try { view.getLocationOnScreen(screen); } catch (Throwable ignored) {}
        return view.getClass().getName()
                + "@" + Integer.toHexString(System.identityHashCode(view))
                + " screen=[" + screen[0] + "," + screen[1] + "]"
                + " size=" + view.getWidth() + "x" + view.getHeight()
                + " alpha=" + view.getAlpha();
    }

    private static String className(Object value) {
        return value == null ? "null" : value.getClass().getName();
    }

    private static int intField(Object receiver, String name) throws Exception {
        Object value = readField(receiver, name);
        return value instanceof Number ? ((Number) value).intValue() : -1;
    }

    private static float floatField(Object receiver, String name) throws Exception {
        Object value = readField(receiver, name);
        return value instanceof Number ? ((Number) value).floatValue() : Float.NaN;
    }

    private static Object readField(Object receiver, String name) throws Exception {
        Class<?> type = receiver.getClass();
        while (type != null) {
            try {
                Field field = type.getDeclaredField(name);
                field.setAccessible(true);
                return field.get(receiver);
            } catch (NoSuchFieldException ignored) {
                type = type.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
    }

    private static void log(String message) {
        try {
            Api101Bridge.log(LiquidUiLog.format(TAG + " " + message));
        } catch (Throwable ignored) {
            android.util.Log.i("LiquidUI", "[LUI]" + TAG + " " + message);
        }
    }
}
