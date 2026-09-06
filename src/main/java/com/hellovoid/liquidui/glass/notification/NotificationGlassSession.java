package com.hellovoid.liquidui.glass.notification;

import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.FrameLayout;

import com.hellovoid.liquidui.Api101Bridge;
import com.hellovoid.liquidui.diagnostics.LiquidUiLog;

import java.lang.ref.WeakReference;
import java.util.ArrayList;
import java.util.List;
import java.util.WeakHashMap;

/** One NotificationShade/NSSL shared PassBlur producer, TextureView, EGL thread and Prismal scene. */
final class NotificationGlassSession implements NotificationPassBlurTextureView.ActivationListener {
    private static final String TAG = "[NotifGlass][Session]";

    private final WeakReference<View> stackRef;
    private final WeakReference<ViewGroup> parentRef;
    private final NotificationGlassNodeCollector collector;
    private final NotificationVendorMaterialController materialController;
    private final NotificationGlassActivityState activityState;
    private final NotificationPassBlurAuthorityState authorityState;
    private final NotificationPassBlurAuthorityState.Listener authorityListener;
    private final NotificationPassBlurSourceState sourceState;
    private final NotificationPassBlurSourceState.Listener sourceListener;
    private final int displayId;
    private final NotificationGlassSceneState sceneState = new NotificationGlassSceneState();
    private final WeakHashMap<Object, Boolean> rows = new WeakHashMap<>();
    private final WeakHashMap<Object, Boolean> wrappers = new WeakHashMap<>();
    private final NotificationGlassHostView host;
    private final NotificationPassBlurTextureView renderer;

    private ViewTreeObserver observer;
    private ViewTreeObserver.OnPreDrawListener preDrawListener;
    private List<NotificationGlassNode> lastNodes = List.of();
    private boolean active;
    private boolean shutdown;
    private boolean updatesPausedForNoRows;
    private boolean shadeBlurSuppressionActive;

    NotificationGlassSession(
            View stack,
            ViewGroup parent,
            NotificationGlassNodeCollector collector,
            NotificationVendorMaterialController materialController,
            NotificationGlassActivityState activityState,
            NotificationPassBlurAuthorityState authorityState,
            NotificationPassBlurSourceState sourceState) {
        this.stackRef = new WeakReference<>(stack);
        this.parentRef = new WeakReference<>(parent);
        this.collector = collector;
        this.materialController = materialController;
        this.activityState = activityState;
        this.authorityState = authorityState;
        this.authorityListener = this::onVendorPassBlurChanged;
        this.sourceState = sourceState;
        this.displayId = stack.getDisplay() != null ? stack.getDisplay().getDisplayId() : 0;
        this.sourceListener = this::onPassBlurSourceChanged;

        host = new NotificationGlassHostView(parent.getContext());
        host.setId(View.generateViewId());
        host.setOnDetached(() -> shutdown("host-detached"));
        int stackIndex = Math.max(0, parent.indexOfChild(stack));
        parent.addView(host, stackIndex,
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));

        renderer = new NotificationPassBlurTextureView(
                parent.getContext(), stack, sceneState, this, authorityState.isEnabled(),
                sourceState, displayId);
        renderer.setAlpha(0f);
        host.addView(renderer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        authorityState.addListener(authorityListener);
        sourceState.addListener(displayId, sourceListener);
        installPreDraw(stack);
        log("created stack=" + stack.getClass().getName()
                + " parent=" + parent.getClass().getName()
                + " hostIndex=" + stackIndex);
    }

    boolean isShutdown() { return shutdown; }
    boolean ownsStack(View stack) { return stackRef.get() == stack && !shutdown; }
    boolean ownsRow(Object row) { return rows.containsKey(row) && !shutdown; }
    boolean isActive() { return active && !shutdown; }

    void registerRow(Object row) {
        if (shutdown || row == null) return;
        rows.put(row, Boolean.TRUE);
        if (active) setShadeBlurSuppression(true);
        if (updatesPausedForNoRows) {
            updatesPausedForNoRows = false;
            renderer.setProducerUpdatesEnabled(true, "row-attached");
        }
        refreshScene();
    }

    void unregisterRow(Object row) {
        if (row == null) return;
        rows.remove(row);
        materialController.restoreRow(row);
        if (rows.isEmpty() && !shutdown) {
            setShadeBlurSuppression(false);
            sceneState.clear();
            lastNodes = List.of();
            renderer.requestSceneRefresh();
            updatesPausedForNoRows = true;
            renderer.setProducerUpdatesEnabled(false, "no-visible-rows");
        } else {
            refreshScene();
        }
    }

    void registerWrapper(Object wrapper) {
        if (shutdown || wrapper == null) return;
        wrappers.put(wrapper, Boolean.TRUE);
        View content = materialController.wrapperView(wrapper);
        if (active) materialController.suppressWrapper(wrapper);
        if (content != null) {
            content.post(() -> {
                if (!shutdown && active && wrappers.containsKey(wrapper)) {
                    materialController.suppressWrapper(wrapper);
                }
            });
        }
    }

    @Override public void onFirstFrameActive() {
        if (shutdown || active || !authorityState.isEnabled()) return;
        active = true;
        renderer.setAlpha(1f);
        setShadeBlurSuppression(!rows.isEmpty());
        log("first GPU frame active nodes=" + lastNodes.size());
        suppressVendorMaterial();
        refreshScene();
    }

    @Override public void onTerminalFailure(String stage, Throwable error) {
        if (shutdown) return;
        log("terminal failure stage=" + stage + " error=" + error);
        active = false;
        setShadeBlurSuppression(false);
        materialController.restoreAll();
        shutdown("renderer-failure-" + stage);
    }

    void shutdown(String reason) {
        if (shutdown) return;
        shutdown = true;
        active = false;
        setShadeBlurSuppression(false);
        authorityState.removeListener(authorityListener);
        sourceState.removeListener(sourceListener);
        removePreDraw();
        materialController.restoreAll();
        sceneState.clear();
        rows.clear();
        wrappers.clear();
        try { renderer.shutdown(); } catch (Throwable ignored) {}
        ViewGroup parent = parentRef.get();
        if (!"host-detached".equals(reason)
                && parent != null && host.getParent() == parent) {
            try { parent.removeView(host); } catch (Throwable ignored) {}
        }
        log("shutdown reason=" + reason);
    }

    private void onVendorPassBlurChanged(boolean enabled) {
        if (shutdown) return;
        log("HyperOS notifPassBlur=" + enabled);
        if (!enabled) {
            active = false;
            renderer.setAlpha(0f);
            setShadeBlurSuppression(false);
            materialController.restoreAll();
            renderer.setVendorPassBlurEnabled(false, "hyperos-notifPassBlur");
            return;
        }
        renderer.setVendorPassBlurEnabled(true, "hyperos-notifPassBlur");
        refreshScene();
    }

    private void onPassBlurSourceChanged(NotificationPassBlurSourceState.Snapshot snapshot) {
        if (shutdown || snapshot.displayId() != displayId) return;
        log("task display source available=" + snapshot.available()
                + " generation=" + snapshot.generation());
        if (!snapshot.available()) {
            active = false;
            renderer.setAlpha(0f);
            setShadeBlurSuppression(false);
            materialController.restoreAll();
        }
        renderer.onPassBlurSourceChanged("root-task-display-area");
        if (snapshot.available() && authorityState.isEnabled()) refreshScene();
    }

    private void installPreDraw(View stack) {
        ViewTreeObserver value = stack.getViewTreeObserver();
        if (value == null || !value.isAlive()) return;
        preDrawListener = () -> {
            refreshScene();
            return true;
        };
        value.addOnPreDrawListener(preDrawListener);
        observer = value;
    }

    private void removePreDraw() {
        ViewTreeObserver value = observer;
        ViewTreeObserver.OnPreDrawListener listener = preDrawListener;
        observer = null;
        preDrawListener = null;
        if (value == null || listener == null) return;
        try { if (value.isAlive()) value.removeOnPreDrawListener(listener); } catch (Throwable ignored) {}
    }

    private void refreshScene() {
        if (shutdown || !host.isAttachedToWindow()) return;
        View stack = stackRef.get();
        if (stack == null || !stack.isAttachedToWindow()) return;
        List<NotificationGlassNode> nodes = new ArrayList<>();
        List<Object> stale = new ArrayList<>();
        for (Object row : new ArrayList<>(rows.keySet())) {
            if (!(row instanceof View rowView)
                    || !rowView.isAttachedToWindow()
                    || rowView.getRootView() != stack.getRootView()) {
                stale.add(row);
                continue;
            }
            NotificationGlassNode node = collector.collect(row, host);
            if (node != null && node.drawable()) nodes.add(node);
        }
        for (Object row : stale) {
            rows.remove(row);
            materialController.restoreRow(row);
        }
        if (!nodes.equals(lastNodes)) {
            lastNodes = List.copyOf(nodes);
            sceneState.publish(lastNodes);
            renderer.requestSceneRefresh();
        }
        if (active) suppressVendorMaterial();
    }

    private void setShadeBlurSuppression(boolean enabled) {
        if (enabled == shadeBlurSuppressionActive) return;
        shadeBlurSuppressionActive = enabled;
        if (enabled) activityState.activate();
        else activityState.deactivate();
        log("shade blur suppression=" + enabled);
    }

    private void suppressVendorMaterial() {
        for (Object row : new ArrayList<>(rows.keySet())) {
            materialController.suppressRow(row);
        }
        for (Object wrapper : new ArrayList<>(wrappers.keySet())) {
            Object row = materialController.wrapperRow(wrapper);
            if (row != null && rows.containsKey(row)) {
                materialController.suppressWrapper(wrapper);
            }
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
