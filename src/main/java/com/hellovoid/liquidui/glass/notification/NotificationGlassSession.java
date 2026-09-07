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

/** One shared OES/Prismal session for one NotificationStackScrollLayout. */
final class NotificationGlassSession implements NotificationPassBlurTextureView.ActivationListener {
    private static final String TAG = "[NotifGlass][Session]";

    private final WeakReference<View> stackRef;
    private final WeakReference<ViewGroup> parentRef;
    private final NotificationGlassNodeCollector collector;
    private final LegacyNotificationVendorMaterialController materialController;
    private final NotificationGlassActivityState activityState;
    private final NotificationPassBlurAuthorityState authorityState;
    private final NotificationPassBlurAuthorityState.Listener authorityListener;
    private final NotificationGlassSceneState sceneState = new NotificationGlassSceneState();
    private final NotificationGlassPresentationState presentationState =
            new NotificationGlassPresentationState();
    private final WeakHashMap<Object, Boolean> rows = new WeakHashMap<>();
    private final WeakHashMap<Object, Boolean> wrappers = new WeakHashMap<>();
    private final NotificationGlassHostView host;
    private final NotificationPassBlurTextureView renderer;
    private final NotificationViewRootSurfaceObserver rootSurfaceObserver;

    private ViewTreeObserver observer;
    private ViewTreeObserver.OnPreDrawListener preDrawListener;
    private List<NotificationGlassNode> lastNodes = List.of();
    private long sourceGeneration = 1L;
    private long swapSequence;
    private boolean active;
    private boolean shutdown;
    private boolean updatesPausedForNoRows;
    private boolean shadeBlurSuppressionActive;

    NotificationGlassSession(
            View stack,
            ViewGroup parent,
            NotificationGlassNodeCollector collector,
            LegacyNotificationVendorMaterialController materialController,
            NotificationGlassActivityState activityState,
            NotificationPassBlurAuthorityState authorityState) {
        this.stackRef = new WeakReference<>(stack);
        this.parentRef = new WeakReference<>(parent);
        this.collector = collector;
        this.materialController = materialController;
        this.activityState = activityState;
        this.authorityState = authorityState;
        this.authorityListener = this::onVendorPassBlurChanged;
        presentationState.sourceBound(sourceGeneration);

        host = new NotificationGlassHostView(parent.getContext());
        host.setId(View.generateViewId());
        host.setOnDetached(() -> shutdown("host-detached"));
        int stackIndex = Math.max(0, parent.indexOfChild(stack));
        parent.addView(host, stackIndex,
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));

        renderer = new NotificationPassBlurTextureView(
                parent.getContext(), stack, sceneState, this, authorityState.isEnabled());
        // Keep the TextureView VISIBLE so Android creates and retains its output SurfaceTexture.
        // Presentation is gated by alpha; a stale frame cannot remain visible after source loss.
        renderer.setVisibility(View.VISIBLE);
        renderer.setAlpha(0f);
        host.addView(renderer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        rootSurfaceObserver = new NotificationViewRootSurfaceObserver(
                stack,
                new NotificationViewRootSurfaceObserver.Listener() {
                    @Override public void onSurfaceDestroyed() {
                        presentationState.sourceLost(sourceGeneration);
                        revokeSharedPresentation("root-surface-destroyed");
                    }

                    @Override public void onSurfaceReady(String event) {
                        if (shutdown) return;
                        sourceGeneration++;
                        presentationState.sourceBound(sourceGeneration);
                        renderer.rebindProducer("root-" + event);
                    }
                });
        rootSurfaceObserver.attach();

        authorityState.addListener(authorityListener);
        installPreDraw(stack);
        log("created stack=" + stack.getClass().getName()
                + " parent=" + parent.getClass().getName()
                + " hostIndex=" + stackIndex
                + " sharedRenderer=true sourceGen=" + sourceGeneration);
    }

    boolean isShutdown() { return shutdown; }
    boolean ownsStack(View stack) { return stackRef.get() == stack && !shutdown; }
    boolean ownsRow(Object row) { return rows.containsKey(row) && !shutdown; }
    boolean isActive() { return active && !shutdown; }

    void registerRow(Object row) {
        if (shutdown || row == null) return;
        rows.put(row, Boolean.TRUE);
        if (presentationState.isGlassActive()) {
            active = true;
            renderer.setAlpha(1f);
            setShadeBlurSuppression(true);
            materialController.suppressRow(row);
        }
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
            active = false;
            renderer.setAlpha(0f);
            setShadeBlurSuppression(false);
            materialController.restoreAll();
            sceneState.clear();
            NotificationGlassSceneSnapshot empty = sceneState.latest();
            presentationState.scene(empty.generation, 0);
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
        if (shutdown || active || !authorityState.isEnabled() || lastNodes.isEmpty()) return;
        NotificationGlassSceneSnapshot scene = sceneState.latest();
        presentationState.freshFrame(sourceGeneration);
        NotificationGlassPresentationState.ActivationToken token =
                presentationState.swapSucceeded(
                        sourceGeneration, scene.generation, ++swapSequence);
        if (!presentationState.accept(token)) {
            log("late/stale GPU activation rejected sourceGen=" + sourceGeneration
                    + " sceneGen=" + scene.generation);
            return;
        }

        active = true;
        renderer.setAlpha(1f);
        setShadeBlurSuppression(true);
        suppressVendorMaterial();
        log("shared GPU glass active nodes=" + lastNodes.size()
                + " sourceGen=" + token.sourceGeneration()
                + " sceneGen=" + token.sceneGeneration()
                + " swapSeq=" + token.swapSequence());
        refreshScene();
    }

    @Override public void onTerminalFailure(String stage, Throwable error) {
        if (shutdown) return;
        presentationState.terminalFailure();
        log("terminal failure stage=" + stage + " error=" + error);
        revokeSharedPresentation("renderer-failure-" + stage);
        shutdown("renderer-failure-" + stage);
    }

    void shutdown(String reason) {
        if (shutdown) return;
        presentationState.terminalFailure();
        revokeSharedPresentation("shutdown-" + reason);
        shutdown = true;
        authorityState.removeListener(authorityListener);
        rootSurfaceObserver.detach();
        removePreDraw();
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
            presentationState.fallbackNative();
            revokeSharedPresentation("hyperos-notifPassBlur-off");
            renderer.setVendorPassBlurEnabled(false, "hyperos-notifPassBlur");
            return;
        }
        sourceGeneration++;
        presentationState.sourceBound(sourceGeneration);
        renderer.setVendorPassBlurEnabled(true, "hyperos-notifPassBlur");
        refreshScene();
    }

    private void revokeSharedPresentation(String reason) {
        boolean wasActive = active;
        active = false;
        renderer.setAlpha(0f);
        setShadeBlurSuppression(false);
        materialController.restoreAll();
        if (wasActive) log("shared GPU glass revoked reason=" + reason + " fallback=2dp-native");
    }

    private void installPreDraw(View stack) {
        ViewTreeObserver value = stack.getViewTreeObserver();
        if (value == null || !value.isAlive()) return;
        preDrawListener = () -> {
            rootSurfaceObserver.attach();
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
            NotificationGlassSceneSnapshot scene = sceneState.publish(lastNodes);
            presentationState.scene(scene.generation, scene.size());
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
