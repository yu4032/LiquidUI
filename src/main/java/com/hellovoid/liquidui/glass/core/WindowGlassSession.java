package com.hellovoid.liquidui.glass.core;

import android.os.Handler;
import android.view.View;
import android.view.ViewGroup;
import android.widget.FrameLayout;

import com.hellovoid.liquidui.config.GlassStyleConfig;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/** Owns the one shared glass source/output pipeline and merged component scene for one Window. */
public class WindowGlassSession implements AutoCloseable {
    public interface AdapterPresentationListener {
        void onPresentationChanged(Set<String> authorizedNodeIds, Set<String> revokedNodeIds);
        void onTerminalFailure(String stage, Throwable error);
    }

    /** Component-local publication handle. It never exposes EGL/OES/PassBlur ownership. */
    public final class AdapterBinding implements AutoCloseable {
        private final String adapterId;
        private final AdapterPresentationListener listener;
        private List<GlassNode> nodes = List.of();
        private boolean active;
        private boolean bindingClosed;

        private AdapterBinding(String adapterId, AdapterPresentationListener listener) {
            this.adapterId = adapterId;
            this.listener = listener;
        }

        public String adapterId() {
            return adapterId;
        }

        public void publish(List<GlassNode> nextNodes) {
            synchronized (WindowGlassSession.this) {
                requireLiveBinding(this);
                nodes = List.copyOf(Objects.requireNonNull(nextNodes, "nodes"));
                publishCombinedSceneLocked();
            }
        }

        /** Controls only this adapter's demand; the Window producer remains on if any adapter is active. */
        public void setActive(boolean value, String reason) {
            synchronized (WindowGlassSession.this) {
                requireLiveBinding(this);
                if (active == value) return;
                active = value;
                updateProducerGateLocked(reason == null ? adapterId : adapterId + ":" + reason);
            }
        }

        @Override
        public void close() {
            synchronized (WindowGlassSession.this) {
                if (bindingClosed) return;
                bindingClosed = true;
                active = false;
                nodes = List.of();
                // Keep the binding registered while requestScene synchronously dispatches any
                // revocations, then remove it so the feature can restore its exact native state.
                publishCombinedSceneLocked();
                adapters.remove(adapterId, this);
                updateProducerGateLocked(adapterId + ":closed");
            }
        }
    }

    private final WindowKey key;
    private final GlassSceneState sceneState = new GlassSceneState();
    private final WindowPresentationState presentationState = new WindowPresentationState();
    private final AtomicBoolean closed = new AtomicBoolean();
    private final Handler renderHandler;
    private final Map<String, AdapterBinding> adapters = new LinkedHashMap<>();
    private Map<String, AdapterBinding> nodeOwners = Map.of();
    private final Set<String> presentedNodeIds = new HashSet<>();

    private GlassHostView host;
    private WindowGlassRenderer renderer;
    private ViewGroup hostParent;
    private boolean vendorPassBlurEnabled = true;
    private GlassStyleConfig styleConfig = GlassStyleConfig.defaults();
    private long styleVersion = 1L;

    /** Pure lifecycle constructor retained for registry/state tests; it cannot attach a renderer. */
    public WindowGlassSession(WindowKey key) {
        this(key, null);
    }

    /** Production constructor supplied only by the process-global SystemUiGlassCore. */
    public WindowGlassSession(WindowKey key, Handler renderHandler) {
        this.key = Objects.requireNonNull(key, "key");
        this.renderHandler = renderHandler;
    }

    public WindowKey key() {
        return key;
    }

    public GlassSceneState sceneState() {
        return sceneState;
    }

    public WindowPresentationState presentationState() {
        return presentationState;
    }

    public synchronized AdapterBinding bindAdapter(
            String adapterId, AdapterPresentationListener listener) {
        if (closed.get()) throw new IllegalStateException("session closed");
        String id = Objects.requireNonNull(adapterId, "adapterId");
        if (id.isBlank()) throw new IllegalArgumentException("adapterId blank");
        if (adapters.containsKey(id)) throw new IllegalStateException("adapter already bound: " + id);
        AdapterBinding binding = new AdapterBinding(
                id, Objects.requireNonNull(listener, "listener"));
        adapters.put(id, binding);
        return binding;
    }

    /**
     * Establishes the Window output host once. Phase 0 keeps the validated notification parent
     * geometry; later adapters in the same Window reuse this exact host and renderer.
     */
    public synchronized View attachRenderer(
            View materialHost,
            ViewGroup parent,
            int insertionIndex,
            boolean vendorPassBlurEnabled) {
        if (closed.get()) throw new IllegalStateException("session closed");
        Objects.requireNonNull(materialHost, "materialHost");
        Objects.requireNonNull(parent, "parent");
        if (renderHandler == null) {
            throw new IllegalStateException("session has no shared render handler");
        }
        this.vendorPassBlurEnabled = vendorPassBlurEnabled;
        if (renderer != null && host != null) {
            renderer.setVendorPassBlurEnabled(vendorPassBlurEnabled, "session-reuse");
            return host;
        }

        GlassHostView nextHost = new GlassHostView(parent.getContext());
        nextHost.setId(View.generateViewId());
        nextHost.setOnDetached(this::close);
        int safeIndex = Math.max(0, Math.min(insertionIndex, parent.getChildCount()));
        parent.addView(nextHost, safeIndex,
                new ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT));

        WindowGlassRenderer nextRenderer = new WindowGlassRenderer(
                parent.getContext(),
                materialHost,
                sceneState,
                presentationState,
                renderHandler,
                new WindowGlassRenderer.PresentationListener() {
                    @Override
                    public void onPresentationChanged(
                            Set<String> authorizedNodeIds, Set<String> revokedNodeIds) {
                        handleRendererPresentation(authorizedNodeIds, revokedNodeIds);
                    }

                    @Override
                    public void onTerminalFailure(String stage, Throwable error) {
                        handleRendererTerminalFailure(stage, error);
                    }
                },
                vendorPassBlurEnabled);
        nextRenderer.updateGlassStyles(styleConfig, styleVersion);
        nextRenderer.setVisibility(View.VISIBLE);
        nextRenderer.setAlpha(0f);
        nextHost.addView(nextRenderer, new FrameLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));

        hostParent = parent;
        host = nextHost;
        renderer = nextRenderer;
        updateProducerGateLocked("renderer-attached");
        if (!sceneState.latest().nodes().isEmpty()) nextRenderer.requestScene();
        return nextHost;
    }

    public synchronized View sceneHost() {
        return host;
    }

    /** Applies material/sampling state only; Window producer ownership never changes here. */
    public synchronized void updateGlassStyles(GlassStyleConfig style, long version) {
        if (closed.get() || version <= styleVersion) return;
        styleConfig = Objects.requireNonNull(style, "style");
        styleVersion = version;
        if (renderer != null) renderer.updateGlassStyles(style, version);
    }

    public synchronized void setVendorPassBlurEnabled(boolean enabled, String reason) {
        if (closed.get()) return;
        vendorPassBlurEnabled = enabled;
        if (!enabled) {
            long producerGeneration = presentationState.producerGeneration();
            if (producerGeneration >= 0L) {
                handleRendererPresentation(
                        Set.of(), presentationState.sourceLost(producerGeneration));
            }
        }
        if (renderer != null) renderer.setVendorPassBlurEnabled(enabled, reason);
    }

    public boolean isClosed() {
        return closed.get();
    }

    @Override
    public void close() {
        if (!closed.compareAndSet(false, true)) return;
        WindowGlassRenderer oldRenderer;
        GlassHostView oldHost;
        ViewGroup oldParent;
        List<AdapterBinding> oldAdapters;
        synchronized (this) {
            oldRenderer = renderer;
            oldHost = host;
            oldParent = hostParent;
            renderer = null;
            host = null;
            hostParent = null;
            oldAdapters = new ArrayList<>(adapters.values());
        }

        if (oldRenderer != null) {
            try { oldRenderer.shutdown(); } catch (Throwable ignored) {}
            oldRenderer.setAlpha(0f);
        }
        Set<String> revoked = presentationState.detach();
        handleRendererPresentation(Set.of(), revoked);
        for (AdapterBinding binding : oldAdapters) {
            try { binding.listener.onTerminalFailure("session-close", null); } catch (Throwable ignored) {}
        }

        synchronized (this) {
            adapters.clear();
            nodeOwners = Map.of();
            presentedNodeIds.clear();
            sceneState.clear();
        }
        if (oldParent != null && oldHost != null && oldHost.getParent() == oldParent) {
            try { oldParent.removeView(oldHost); } catch (Throwable ignored) {}
        }
    }

    private void requireLiveBinding(AdapterBinding binding) {
        if (closed.get()) throw new IllegalStateException("session closed");
        if (binding.bindingClosed || adapters.get(binding.adapterId) != binding) {
            throw new IllegalStateException("adapter binding closed: " + binding.adapterId);
        }
    }

    private void publishCombinedSceneLocked() {
        List<GlassNode> merged = new ArrayList<>();
        Map<String, AdapterBinding> nextOwners = new HashMap<>();
        for (AdapterBinding binding : adapters.values()) {
            for (GlassNode node : binding.nodes) {
                if (node == null) continue;
                AdapterBinding previous = nextOwners.put(node.id(), binding);
                if (previous != null && previous != binding) {
                    throw new IllegalStateException("duplicate Window glass node id: " + node.id());
                }
                merged.add(node);
            }
        }

        sceneState.publish(merged);
        WindowGlassRenderer current = renderer;
        if (current != null) {
            // requestScene can synchronously revoke removed ids on the UI thread. Keep nodeOwners
            // pointing at the old scene until that callback completes; authorizations arrive only
            // after a later successful render and therefore see the new ownership map.
            current.requestScene();
        } else {
            Set<String> revoked = presentationState.scene(
                    sceneState.latest().generation(), lifecycleMap(merged));
            handleRendererPresentation(Set.of(), revoked);
        }
        nodeOwners = Map.copyOf(nextOwners);
    }

    private void updateProducerGateLocked(String reason) {
        boolean anyActive = false;
        for (AdapterBinding binding : adapters.values()) {
            if (!binding.bindingClosed && binding.active) {
                anyActive = true;
                break;
            }
        }
        if (renderer != null) renderer.setProducerUpdatesEnabled(anyActive, reason);
    }

    private void handleRendererPresentation(Set<String> authorized, Set<String> revoked) {
        Map<AdapterBinding, Set<String>> authorizedByAdapter = new LinkedHashMap<>();
        Map<AdapterBinding, Set<String>> revokedByAdapter = new LinkedHashMap<>();
        WindowGlassRenderer currentRenderer;
        synchronized (this) {
            if (revoked != null) {
                for (String id : revoked) {
                    presentedNodeIds.remove(id);
                    AdapterBinding owner = nodeOwners.get(id);
                    if (owner != null) {
                        revokedByAdapter.computeIfAbsent(owner, ignored -> new HashSet<>()).add(id);
                    }
                }
            }
            if (authorized != null) {
                for (String id : authorized) {
                    AdapterBinding owner = nodeOwners.get(id);
                    if (owner == null || owner.bindingClosed) continue;
                    presentedNodeIds.add(id);
                    authorizedByAdapter.computeIfAbsent(owner, ignored -> new HashSet<>()).add(id);
                }
            }
            currentRenderer = renderer;
            if (currentRenderer != null) {
                currentRenderer.setAlpha(presentedNodeIds.isEmpty() ? 0f : 1f);
            }
        }

        Set<AdapterBinding> recipients = new HashSet<>();
        recipients.addAll(authorizedByAdapter.keySet());
        recipients.addAll(revokedByAdapter.keySet());
        for (AdapterBinding binding : recipients) {
            Set<String> safeAuthorized = Set.copyOf(
                    authorizedByAdapter.getOrDefault(binding, Set.of()));
            Set<String> safeRevoked = Set.copyOf(
                    revokedByAdapter.getOrDefault(binding, Set.of()));
            try {
                binding.listener.onPresentationChanged(safeAuthorized, safeRevoked);
            } catch (Throwable ignored) {}
        }
    }

    private void handleRendererTerminalFailure(String stage, Throwable error) {
        List<AdapterBinding> snapshot;
        synchronized (this) {
            presentedNodeIds.clear();
            if (renderer != null) renderer.setAlpha(0f);
            snapshot = new ArrayList<>(adapters.values());
        }
        for (AdapterBinding binding : snapshot) {
            if (binding.bindingClosed) continue;
            try { binding.listener.onTerminalFailure(stage, error); } catch (Throwable ignored) {}
        }
        close();
    }

    private static Map<String, Long> lifecycleMap(List<GlassNode> nodes) {
        if (nodes == null || nodes.isEmpty()) return Map.of();
        Map<String, Long> result = new LinkedHashMap<>();
        for (GlassNode node : nodes) {
            if (node != null && node.drawable()) result.put(node.id(), node.lifecycleGeneration());
        }
        return Map.copyOf(result);
    }
}
