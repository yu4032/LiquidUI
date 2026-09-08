package com.hellovoid.liquidui.glass.notification;

import android.view.View;

import com.hellovoid.liquidui.Api101Bridge;
import com.hellovoid.liquidui.diagnostics.LiquidUiLog;
import com.hellovoid.liquidui.glass.core.GlassNode;
import com.hellovoid.liquidui.glass.core.SystemUiGlassCore;
import com.hellovoid.liquidui.glass.core.WindowGlassSession;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.WeakHashMap;

/** Notification-only geometry/material adapter. All GPU/source ownership stays in WindowGlassSession. */
final class NotificationGlassAdapter implements WindowGlassSession.AdapterPresentationListener {
    private static final String TAG = "[NotifGlass][Adapter]";

    private static final class RowState {
        final String id;
        long lifecycleGeneration;
        boolean active;

        RowState(String id) {
            this.id = id;
        }
    }

    private final WeakReference<View> stackRef;
    private final NotificationGlassNodeCollector collector;
    private final LegacyNotificationVendorMaterialController materialController;
    private final NotificationGlassActivityState activityState;
    private final WindowGlassSession session;
    private final WindowGlassSession.AdapterBinding binding;
    private final View sceneHost;
    private final WeakHashMap<Object, RowState> rows = new WeakHashMap<>();
    private final WeakHashMap<Object, Boolean> wrappers = new WeakHashMap<>();
    private final Set<String> authorizedNodeIds = new HashSet<>();

    private List<GlassNode> lastNodes = List.of();
    private long identitySequence;
    private long lifecycleGeneration;
    private float panelExpansionFraction;
    private boolean shadeBlurSuppressionActive;
    private boolean shutdown;

    NotificationGlassAdapter(
            SystemUiGlassCore glassCore,
            View stack,
            NotificationGlassNodeCollector collector,
            LegacyNotificationVendorMaterialController materialController,
            NotificationGlassActivityState activityState) {
        this.stackRef = new WeakReference<>(stack);
        this.collector = collector;
        this.materialController = materialController;
        this.activityState = activityState;

        // Component adapters never create the Window renderer. The exact
        // NotificationShadeWindowView authority must already have established it.
        session = glassCore.sessionFor(stack);
        sceneHost = session.sceneHost();
        if (sceneHost == null) {
            throw new IllegalStateException("Shade Window renderer authority unavailable");
        }
        binding = session.bindAdapter(
                "notification:" + Integer.toHexString(System.identityHashCode(stack)), this);

        log("created windowSession=" + System.identityHashCode(session)
                + " stack=" + stack.getClass().getName()
                + " host=" + sceneHost.getClass().getName());
    }

    boolean isShutdown() {
        return shutdown || session.isClosed();
    }

    boolean ownsStack(View stack) {
        return !isShutdown() && stackRef.get() == stack;
    }

    boolean ownsRow(Object row) {
        RowState state = rows.get(row);
        return !isShutdown() && state != null && state.active;
    }

    void onPanelExpansion(float fraction) {
        if (isShutdown()) return;
        float next = Math.max(0f, Math.min(1f, fraction));
        panelExpansionFraction = next;
        // updateExpandedHeight itself is the native transition tick. The fraction is diagnostic
        // state only: stale/unchanged values must not suppress a geometry refresh.
        refreshScene();
    }

    void registerRow(Object row) {
        if (isShutdown() || row == null) return;
        RowState state = rows.get(row);
        if (state == null) {
            state = new RowState(nodeId(row));
            rows.put(row, state);
        }
        if (!state.active) {
            state.active = true;
            state.lifecycleGeneration = ++lifecycleGeneration;
        }
        refreshScene();
    }

    void unregisterRow(Object row) {
        if (row == null) return;
        RowState state = rows.get(row);
        if (state == null || !state.active) return;
        state.active = false;
        if (authorizedNodeIds.remove(state.id)) {
            restoreRowAndWrappers(row);
        } else {
            materialController.restoreRow(row);
        }
        refreshScene();
    }

    void registerWrapper(Object wrapper) {
        if (isShutdown() || wrapper == null) return;
        wrappers.put(wrapper, Boolean.TRUE);
        Object row = materialController.wrapperRow(wrapper);
        RowState state = row != null ? rows.get(row) : null;
        if (state != null && state.active && authorizedNodeIds.contains(state.id)) {
            materialController.suppressWrapper(wrapper);
        }
    }

    @Override
    public void onPresentationChanged(
            Set<String> authorizedNodeIds, Set<String> revokedNodeIds) {
        if (shutdown) return;
        if (revokedNodeIds != null) {
            for (String id : revokedNodeIds) {
                this.authorizedNodeIds.remove(id);
                Object row = rowForId(id);
                if (row != null) restoreRowAndWrappers(row);
            }
        }
        if (authorizedNodeIds != null) {
            for (String id : authorizedNodeIds) {
                Object row = rowForId(id);
                if (row == null) continue;
                RowState state = rows.get(row);
                if (state == null || !state.active) continue;
                this.authorizedNodeIds.add(id);
                materialController.suppressRow(row);
                suppressWrappersForRow(row);
            }
        }
        setShadeBlurSuppression(!this.authorizedNodeIds.isEmpty());
    }

    @Override
    public void onTerminalFailure(String stage, Throwable error) {
        if (shutdown) return;
        log("terminal failure stage=" + stage + " error=" + error);
        failClosed("terminal-" + stage);
    }

    void shutdown(String reason) {
        if (shutdown) return;
        shutdown = true;
        try { binding.close(); } catch (Throwable ignored) {}
        authorizedNodeIds.clear();
        materialController.restoreAll();
        setShadeBlurSuppression(false);
        rows.clear();
        wrappers.clear();
        lastNodes = List.of();
        log("shutdown reason=" + reason);
    }

    private void failClosed(String reason) {
        shutdown = true;
        authorizedNodeIds.clear();
        materialController.restoreAll();
        setShadeBlurSuppression(false);
        log("native fallback reason=" + reason);
    }

    private void refreshScene() {
        if (shutdown || session.isClosed() || sceneHost == null || !sceneHost.isAttachedToWindow()) return;
        View stack = stackRef.get();
        if (stack == null || !stack.isAttachedToWindow()) return;

        List<GlassNode> nodes = new ArrayList<>();
        List<Object> stale = new ArrayList<>();
        boolean hasActiveRows = false;
        for (var entry : new WeakHashMap<>(rows).entrySet()) {
            Object rowObject = entry.getKey();
            RowState state = entry.getValue();
            if (state == null || !state.active) continue;
            if (!(rowObject instanceof View row)
                    || !row.isAttachedToWindow()
                    || row.getRootView() != stack.getRootView()) {
                stale.add(rowObject);
                continue;
            }
            hasActiveRows = true;
            GlassNode node = collector.collect(
                    rowObject,
                    sceneHost,
                    state.id,
                    state.lifecycleGeneration,
                    collector.visualZOrder(rowObject));
            if (node != null && node.drawable()) nodes.add(node);
        }

        for (Object row : stale) {
            RowState state = rows.get(row);
            if (state != null) {
                state.active = false;
                authorizedNodeIds.remove(state.id);
            }
            restoreRowAndWrappers(row);
        }

        nodes.sort(Comparator.comparingInt(GlassNode::zOrder).thenComparing(GlassNode::id));
        List<GlassNode> nextNodes = List.copyOf(nodes);
        if (!nextNodes.equals(lastNodes)) {
            lastNodes = nextNodes;
            binding.publish(lastNodes);
        }
        // Keep the Window producer warm while rows exist. Opening/closing the panel must not force
        // a PassBlur producer teardown/rebind on the same critical transition frame.
        binding.setActive(hasActiveRows, hasActiveRows ? "rows-present" : "no-rows");
        if (authorizedNodeIds.isEmpty()) setShadeBlurSuppression(false);
    }

    private String nodeId(Object row) {
        return "notification:"
                + Integer.toHexString(System.identityHashCode(row))
                + ":" + (++identitySequence);
    }

    private Object rowForId(String id) {
        if (id == null) return null;
        for (var entry : new WeakHashMap<>(rows).entrySet()) {
            RowState state = entry.getValue();
            if (state != null && id.equals(state.id)) return entry.getKey();
        }
        return null;
    }

    private void suppressWrappersForRow(Object row) {
        for (Object wrapper : new ArrayList<>(wrappers.keySet())) {
            if (materialController.wrapperRow(wrapper) == row) {
                materialController.suppressWrapper(wrapper);
            }
        }
    }

    private void restoreRowAndWrappers(Object row) {
        materialController.restoreRow(row);
        for (Object wrapper : new ArrayList<>(wrappers.keySet())) {
            if (materialController.wrapperRow(wrapper) == row) {
                materialController.restoreWrapper(wrapper);
            }
        }
    }

    private void setShadeBlurSuppression(boolean enabled) {
        if (enabled == shadeBlurSuppressionActive) return;
        shadeBlurSuppressionActive = enabled;
        if (enabled) activityState.activate();
        else activityState.deactivate();
        log("shade blur suppression=" + enabled);
    }

    private static void log(String message) {
        try {
            Api101Bridge.log(LiquidUiLog.format(TAG + " " + message));
        } catch (Throwable ignored) {
            android.util.Log.i("LiquidUI", "[LUI]" + TAG + " " + message);
        }
    }
}
