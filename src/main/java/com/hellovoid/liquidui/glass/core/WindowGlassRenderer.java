package com.hellovoid.liquidui.glass.core;

import android.content.Context;
import android.graphics.Point;
import android.graphics.Rect;
import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.SystemClock;
import android.view.Display;
import android.view.Surface;
import android.view.SurfaceControl;
import android.view.TextureView;
import android.view.View;
import android.view.ViewTreeObserver;

import com.hellovoid.liquidui.Api101Bridge;
import com.hellovoid.liquidui.diagnostics.LiquidUiLog;
import com.hellovoid.prismal.PrismalParams;
import com.hellovoid.prismal.PrismalRenderer;
import com.hellovoid.prismal.PrismalSampling;

import java.lang.ref.WeakReference;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Feedback-safe HyperOS 3.0.307 PassBlur -> OES -> 2D -> Prismal -> TextureView renderer.
 *
 * Stage A normalizes SurfaceFlinger's external-OES PassBlur producer into Window-local RGBA while
 * applying the validated HyperOS Stage-B coordinate transform. Stage B runs Prismal's original
 * half-resolution horizontal/vertical Gaussian blur. Stage C runs the upstream Prismal optical
 * shader over ordinary 2D textures. Pixel data never crosses to the CPU.
 */
final class WindowGlassRenderer extends TextureView
        implements TextureView.SurfaceTextureListener {
    interface PresentationListener {
        void onPresentationChanged(Set<String> authorizedNodeIds, Set<String> revokedNodeIds);
        void onTerminalFailure(String stage, Throwable error);
    }
    private static final String TAG = "[GlassCore][Renderer]";
    private static final int MAX_BIND_RETRY_FRAMES = 24;
    // PrismalSampling computes the automatic optical safety ring. Left/right also retain the
    // fixed 32dp compatibility baseline. GUI values are signed extras applied after that automatic
    // guard: positive expands, negative shrinks, and the final inset never goes below zero.
    private static final float EDGE_OVERSCAN_DP = 32f;

    private static final float[] QUAD = new float[]{
            -1f, -1f, 0f, 0f,
             1f, -1f, 1f, 0f,
            -1f,  1f, 0f, 1f,
             1f,  1f, 1f, 1f
    };

    private static final class SamplingInsets {
        final int left;
        final int right;
        final int top;
        final int bottom;

        SamplingInsets(int left, int right, int top, int bottom) {
            this.left = Math.max(0, left);
            this.right = Math.max(0, right);
            this.top = Math.max(0, top);
            this.bottom = Math.max(0, bottom);
        }
    }

    /** One immutable UI-thread mapping generation consumed atomically by the GL thread. */
    private static final class BackdropSnapshot {
        final int visibleWidth;
        final int visibleHeight;
        final int sampleWidth;
        final int sampleHeight;
        final int displayId;
        final int displayRotation;
        final int configRotation;
        final int windowLeft;
        final int windowTop;
        final int windowWidth;
        final int windowHeight;
        final int sampleLeft;
        final int sampleTop;
        final int surfaceWidth;
        final int surfaceHeight;
        final int bufferWidth;
        final int bufferHeight;
        final PrismalParams prismalParams;
        final float backdropX;
        final float backdropY;
        final float backdropW;
        final float backdropH;
        final float validSampleLeft;
        final float validSampleBottom;
        final float validSampleRight;
        final float validSampleTop;
        final float validDockLeft;
        final float validDockBottom;
        final float validDockRight;
        final float validDockTop;
        final float dockUvLeft;
        final float dockUvBottom;
        final float dockUvWidth;
        final float dockUvHeight;
        final BackdropMapping.Coverage coverage;

        BackdropSnapshot(
                int visibleWidth, int visibleHeight,
                int sampleWidth, int sampleHeight,
                int displayId, int displayRotation,
                int configRotation,
                int windowLeft, int windowTop, int windowWidth, int windowHeight,
                int sampleLeft, int sampleTop,
                int surfaceWidth, int surfaceHeight,
                int bufferWidth, int bufferHeight,
                PrismalParams prismalParams,
                float backdropX, float backdropY, float backdropW, float backdropH,
                float validSampleLeft, float validSampleBottom,
                float validSampleRight, float validSampleTop,
                float validDockLeft, float validDockBottom,
                float validDockRight, float validDockTop,
                float dockUvLeft, float dockUvBottom, float dockUvWidth, float dockUvHeight,
                BackdropMapping.Coverage coverage) {
            this.visibleWidth = visibleWidth;
            this.visibleHeight = visibleHeight;
            this.sampleWidth = sampleWidth;
            this.sampleHeight = sampleHeight;
            this.displayId = displayId;
            this.displayRotation = displayRotation;
            this.configRotation = configRotation;
            this.windowLeft = windowLeft;
            this.windowTop = windowTop;
            this.windowWidth = windowWidth;
            this.windowHeight = windowHeight;
            this.sampleLeft = sampleLeft;
            this.sampleTop = sampleTop;
            this.surfaceWidth = surfaceWidth;
            this.surfaceHeight = surfaceHeight;
            this.bufferWidth = bufferWidth;
            this.bufferHeight = bufferHeight;
            this.prismalParams = prismalParams;
            this.backdropX = backdropX;
            this.backdropY = backdropY;
            this.backdropW = backdropW;
            this.backdropH = backdropH;
            this.validSampleLeft = validSampleLeft;
            this.validSampleBottom = validSampleBottom;
            this.validSampleRight = validSampleRight;
            this.validSampleTop = validSampleTop;
            this.validDockLeft = validDockLeft;
            this.validDockBottom = validDockBottom;
            this.validDockRight = validDockRight;
            this.validDockTop = validDockTop;
            this.dockUvLeft = dockUvLeft;
            this.dockUvBottom = dockUvBottom;
            this.dockUvWidth = dockUvWidth;
            this.dockUvHeight = dockUvHeight;
            this.coverage = coverage;
        }
    }

    private static final class ProducerGeometry {
        final int surfaceWidth;
        final int surfaceHeight;
        final int bufferWidth;
        final int bufferHeight;
        final int configRotation;
        final SurfaceControl rootSurface;

        ProducerGeometry(
                int surfaceWidth, int surfaceHeight,
                int bufferWidth, int bufferHeight,
                int configRotation, SurfaceControl rootSurface) {
            this.surfaceWidth = surfaceWidth;
            this.surfaceHeight = surfaceHeight;
            this.bufferWidth = bufferWidth;
            this.bufferHeight = bufferHeight;
            this.configRotation = configRotation;
            this.rootSurface = rootSurface;
        }
    }

    private final WeakReference<View> materialHostRef;
    private final GlassSceneState sceneState;
    private final WindowPresentationState presentationState;
    private final MaterialProfileRegistry materialProfiles;
    private final GlassCompositor compositor;
    private final PresentationListener presentationListener;
    private final FloatBuffer quadBuffer;
    private final Handler renderHandler;
    private final Handler mainHandler;
    private final FrameCoordinator frameCoordinator;
    private final ViewRootSurfaceObserver rootSurfaceObserver;
    private final AtomicBoolean frameAvailable = new AtomicBoolean(false);
    private final ProducerRecoveryState producerRecovery = new ProducerRecoveryState();
    private final float[] textureMatrix = new float[16];

    private volatile boolean shuttingDown;
    private volatile boolean gpuBackdropActive;
    private volatile boolean producerUpdatesEnabled = true;
    private volatile boolean vendorPassBlurEnabled;
    private volatile boolean rootSurfaceAvailable = true;
    private volatile int configRotation;
    private volatile SurfaceTexture inputSurfaceTexture;
    private volatile Surface inputProducerSurface;
    private volatile long inputProducerGeneration;
    private volatile SurfaceTexture outputSurfaceTexture;
    private volatile Surface outputWindowSurface;
    private volatile SystemUiPassBlurBridge.Binding binding;
    private volatile PrismalParams portablePrismalParams;
    private volatile BackdropSnapshot backdropSnapshot;
    private volatile int outputWidth;
    private volatile int outputHeight;
    private volatile int maxTextureSize;
    private volatile int topSamplingExtraPx = Integer.MIN_VALUE;
    private volatile int bottomSamplingExtraPx = Integer.MIN_VALUE;
    private volatile int leftSamplingExtraPx = Integer.MIN_VALUE;
    private volatile int rightSamplingExtraPx = Integer.MIN_VALUE;

    // Stage A samples a real overscan ring around the visible Window. The sample-valid
    // rectangle is used only by the normalization mirror guard; Window validity remains separate
    // so half-pulled animations are still clipped to pixels that are actually on-screen.
    private volatile float backdropX;
    private volatile float backdropY;
    private volatile float backdropW = 1f;
    private volatile float backdropH = 1f;
    private volatile float validSampleLeft;
    private volatile float validSampleBottom;
    private volatile float validSampleRight = 1f;
    private volatile float validSampleTop = 1f;
    private volatile float validDockLeft;
    private volatile float validDockBottom;
    private volatile float validDockRight = 1f;
    private volatile float validDockTop = 1f;
    // Visible Dock coordinates inside the larger overscan texture: x, y, width, height.
    private volatile float dockUvLeft;
    private volatile float dockUvBottom;
    private volatile float dockUvWidth = 1f;
    private volatile float dockUvHeight = 1f;
    private volatile BackdropMapping.Coverage producerCoverage =
            BackdropMapping.Coverage.FULL;

    private EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLConfig eglConfig;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface eglWindowSurface = EGL14.EGL_NO_SURFACE;

    private int normalizeProgram;
    private int compositeProgram;
    private PrismalRenderer prismalRenderer;
    private int oesTexture;

    private int rawTexture;
    private int rawFramebuffer;
    private int fboWidth;
    private int fboHeight;

    private int boundSurfaceWidth;
    private int boundSurfaceHeight;
    private int boundBufferWidth;
    private int boundBufferHeight;
    private int boundConfigRotation = -1;
    private boolean firstFrameLogged;
    private boolean firstDrawLogged;
    private boolean firstMatrixLogged;
    private boolean stageBDiagnosticsLogged;
    private boolean prismalMappingLogged;
    private long producerFrameCount;
    private long renderedFrameCount;
    private long successfulSwapSequence;
    private long powerWindowStartedMs = SystemClock.uptimeMillis();
    private volatile boolean preparedBackdropReady;
    private volatile long preparedProducerGeneration = -1L;
    private volatile BackdropSnapshot preparedBackdropSnapshot;
    private ViewTreeObserver preDrawObserver;
    private ViewTreeObserver.OnPreDrawListener preDrawListener;

    WindowGlassRenderer(
            Context context,
            View materialHost,
            GlassSceneState sceneState,
            WindowPresentationState presentationState,
            Handler renderHandler,
            PresentationListener presentationListener,
            boolean vendorPassBlurEnabled) {
        super(context);
        materialHostRef = new WeakReference<>(materialHost);
        this.sceneState = sceneState;
        this.presentationState = presentationState;
        this.renderHandler = renderHandler;
        this.presentationListener = presentationListener;
        this.vendorPassBlurEnabled = vendorPassBlurEnabled;
        materialProfiles = new MaterialProfileRegistry(
                context.getResources().getDisplayMetrics().density);
        this.compositor = new GlassCompositor(sceneState, materialProfiles);
        portablePrismalParams = materialProfiles.paramsFor(GlassMaterialProfile.CARD);
        quadBuffer = ByteBuffer.allocateDirect(QUAD.length * Float.BYTES)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer();
        quadBuffer.put(QUAD).position(0);

        setOpaque(false);
        setSurfaceTextureListener(this);
        setClickable(false);
        setFocusable(false);
        setImportantForAccessibility(View.IMPORTANT_FOR_ACCESSIBILITY_NO);

        mainHandler = new Handler(context.getMainLooper());
        frameCoordinator = new FrameCoordinator(
                command -> this.renderHandler.post(command),
                this::renderPendingFrame);
        rootSurfaceObserver = new ViewRootSurfaceObserver(
                materialHost,
                new ViewRootSurfaceObserver.Listener() {
                    @Override public void onSurfaceDestroyed() {
                        handleRootSurfaceDestroyed();
                    }

                    @Override public void onSurfaceReady(String event) {
                        handleRootSurfaceReady(event);
                    }
                });
    }

    boolean isGpuBackdropActive() {
        return gpuBackdropActive;
    }

    boolean isActivationExhausted() {
        return producerRecovery.isActivationExhausted();
    }

    void requestScene() {
        if (shuttingDown) return;
        updateBackdropMapping();
        GlassSceneSnapshot scene = sceneState.latest();
        Set<String> revoked = presentationState.scene(
                scene != null ? scene.generation() : -1L, lifecycleMap(scene));
        dispatchPresentation(Set.of(), revoked);
        frameCoordinator.requestScene();
    }


    private void handleRootSurfaceDestroyed() {
        if (shuttingDown) return;
        rootSurfaceAvailable = false;
        SystemUiPassBlurBridge.Binding stale = binding;
        binding = null;
        if (stale != null) SystemUiPassBlurBridge.invalidate(stale);
        long producerGeneration = inputProducerGeneration;
        ProducerRecoveryState.Decision recovery = producerRecovery.onRootLost(producerGeneration);
        frameAvailable.set(false);
        preparedBackdropReady = false;
        gpuBackdropActive = false;
        dispatchPresentation(Set.of(), presentationState.sourceLost(producerGeneration));
        resetBoundGeometry();
        if (recovery.accepted() && recovery.recreateProducer()) {
            renderHandler.post(() -> recreateInputProducer("root-destroyed"));
        }
    }

    private void handleRootSurfaceReady(String event) {
        if (shuttingDown) return;
        rootSurfaceAvailable = true;
        if (producerRecovery.isRebindPending()
                && inputProducerSurface != null && inputSurfaceTexture != null) {
            post(() -> bindProducerWhenReady(0));
        } else {
            rebindProducer("root-" + event);
        }
    }

    /**
     * Reconnect SurfaceFlinger's PassBlur producer without rebuilding the attached TextureView.
     * The framework Binding can remain stale-true after its BufferQueue has disconnected.
     */
    void setProducerUpdatesEnabled(boolean enabled, String reason) {
        if (shuttingDown) return;
        boolean changed = producerUpdatesEnabled != enabled;
        producerUpdatesEnabled = enabled;
        renderHandler.post(() -> {
            if (shuttingDown) return;
            if (changed) {
                frameAvailable.set(false);
                producerRecovery.onGeometryInvalidated();
                preparedBackdropReady = false;
                gpuBackdropActive = false;
                SystemUiPassBlurBridge.Binding stateBinding = binding;
                if (stateBinding != null && stateBinding.bound && enabled && vendorPassBlurEnabled) {
                    dispatchPresentation(Set.of(), presentationState.sourceBound(
                            stateBinding.surfaceSequenceId, inputProducerGeneration,
                            materialProfiles.profileVersion()));
                } else {
                    dispatchPresentation(Set.of(), presentationState.sourceLost(inputProducerGeneration));
                }
                firstFrameLogged = false;
                firstDrawLogged = false;
                firstMatrixLogged = false;
            }
            SystemUiPassBlurBridge.Binding current = binding;
            if (current == null || !current.bound) return;
            boolean effective = enabled && vendorPassBlurEnabled;
            if (effective) SystemUiPassBlurBridge.resumeUpdates(current);
            else SystemUiPassBlurBridge.pauseUpdates(current);
            log(" producer updates=" + effective + " rowGate=" + enabled
                    + " vendorGate=" + vendorPassBlurEnabled + " reason=" + reason);
        });
    }

    void setVendorPassBlurEnabled(boolean enabled, String reason) {
        if (shuttingDown) return;
        boolean changed = vendorPassBlurEnabled != enabled;
        vendorPassBlurEnabled = enabled;
        if (!changed) return;
        if (enabled) {
            renderHandler.post(() -> activateVendorPassBlurAuthority(reason));
        } else {
            renderHandler.post(() -> retireVendorPassBlurAuthority(reason));
        }
    }

    private void activateVendorPassBlurAuthority(String reason) {
        if (shuttingDown || !vendorPassBlurEnabled) return;
        SystemUiPassBlurBridge.Binding current = binding;
        if (current != null && current.bound) {
            if (producerUpdatesEnabled) SystemUiPassBlurBridge.resumeUpdates(current);
            else SystemUiPassBlurBridge.pauseUpdates(current);
            return;
        }
        if (inputProducerSurface == null || inputSurfaceTexture == null) {
            log(" vendor PassBlur open; producer not ready reason=" + reason);
            return;
        }
        log(" vendor PassBlur open; request bind reason=" + reason);
        post(() -> bindProducerWhenReady(0));
    }

    private void retireVendorPassBlurAuthority(String reason) {
        if (shuttingDown) return;
        SystemUiPassBlurBridge.Binding current = binding;
        binding = null;
        if (current != null) SystemUiPassBlurBridge.unbind(current);
        gpuBackdropActive = false;
        frameAvailable.set(false);
        dispatchPresentation(Set.of(), presentationState.sourceLost(inputProducerGeneration));
        producerRecovery.onGeometryInvalidated();
        preparedBackdropReady = false;
        firstFrameLogged = false;
        firstDrawLogged = false;
        firstMatrixLogged = false;
        stageBDiagnosticsLogged = false;
        prismalMappingLogged = false;
        resetBoundGeometry();
        if (current != null) {
            ProducerRecoveryState.Decision recovery = producerRecovery.onRebindRequested();
            if (recovery.accepted() && recovery.recreateProducer()) {
                recreateInputProducer("vendor-authority-off:" + reason);
            }
        }
        log(" vendor PassBlur closed; sampling unbound reason=" + reason);
    }

    void rebindProducer(String reason) {
        if (shuttingDown) return;
        ProducerRecoveryState.Decision recovery =
                producerRecovery.onRebindRequested();
        if (!recovery.accepted()) return;
        if (recovery.clearFrameworkBinding()) {
            SystemUiPassBlurBridge.Binding stale = binding;
            binding = null;
            SystemUiPassBlurBridge.unbind(stale);
        }
        gpuBackdropActive = false;
        preparedBackdropReady = false;
        dispatchPresentation(Set.of(), presentationState.sourceLost(inputProducerGeneration));
        if (recovery.clearFrameAvailable()) frameAvailable.set(false);
        firstFrameLogged = false;
        firstDrawLogged = false;
        firstMatrixLogged = false;
        stageBDiagnosticsLogged = false;
        prismalMappingLogged = false;
        resetBoundGeometry();
        log(" producer rebind requested reason=" + reason);
        if (recovery.recreateProducer()) {
            renderHandler.post(() -> recreateInputProducer(reason));
        }
    }

    /**
     * SetPassBlurSurface marks the producer Binder before parceling it to SurfaceFlinger. A
     * producer that already crossed that boundary must not be reused; create a new BufferQueue.
     */
    private void recreateInputProducer(String reason) {
        Surface staleProducer = inputProducerSurface;
        SurfaceTexture staleInput = inputSurfaceTexture;
        try {
            makeCurrent();
            inputProducerSurface = null;
            inputSurfaceTexture = null;

            if (staleProducer != null) staleProducer.release();
            if (staleInput != null) {
                try { staleInput.setOnFrameAvailableListener(null); } catch (Throwable ignored) {}
                staleInput.release();
            }
            if (oesTexture != 0) {
                GLES20.glDeleteTextures(1, new int[]{oesTexture}, 0);
                oesTexture = 0;
            }

            createInputProducer();
            if (inputProducerSurface == staleProducer || inputSurfaceTexture == staleInput) {
                throw new IllegalStateException("PassBlur input producer was not replaced");
            }
            log(" input producer recreated reason=" + reason);
            ProducerRecoveryState.Decision recovery =
                    producerRecovery.onProducerRecreated();
            if (recovery.requestBind() && vendorPassBlurEnabled && rootSurfaceAvailable) {
                post(() -> bindProducerWhenReady(0));
            }
        } catch (Throwable error) {
            producerRecovery.onRecreateFailed();
            fail("producer recreate", error);
        }
    }

    void shutdown() {
        if (shuttingDown) return;
        shuttingDown = true;
        producerRecovery.onShutdown();
        dispatchPresentation(Set.of(), presentationState.detach());
        frameCoordinator.cancel();
        gpuBackdropActive = false;
        rootSurfaceObserver.detach();
        removeGeometryObserver();

        SystemUiPassBlurBridge.Binding currentBinding = binding;
        binding = null;
        SystemUiPassBlurBridge.unbind(currentBinding);
        resetBoundGeometry();

        renderHandler.post(this::releaseRenderResources);
    }

    @Override
    protected void onAttachedToWindow() {
        super.onAttachedToWindow();
        rootSurfaceAvailable = true;
        rootSurfaceObserver.attach();
        installGeometryObserver();
        updateBackdropMapping();
        if (isAvailable() && getSurfaceTexture() != null && outputWindowSurface == null) {
            onSurfaceTextureAvailable(getSurfaceTexture(), getWidth(), getHeight());
        }
    }

    @Override
    protected void onDetachedFromWindow() {
        rootSurfaceObserver.detach();
        shutdown();
        super.onDetachedFromWindow();
    }

    @Override
    public void onSurfaceTextureAvailable(SurfaceTexture surface, int width, int height) {
        if (shuttingDown || surface == null) return;
        outputSurfaceTexture = surface;
        outputWidth = Math.max(1, width);
        outputHeight = Math.max(1, height);
        updateBackdropMapping();
        Surface window = new Surface(surface);
        Surface stale = outputWindowSurface;
        outputWindowSurface = window;
        renderHandler.post(() -> attachOutputWindow(stale, window, outputWidth, outputHeight));
    }

    @Override
    public void onSurfaceTextureSizeChanged(SurfaceTexture surface, int width, int height) {
        if (shuttingDown || surface != outputSurfaceTexture) return;
        outputWidth = Math.max(1, width);
        outputHeight = Math.max(1, height);
        updateBackdropMapping();
        renderHandler.post(() -> {
            if (eglWindowSurface == EGL14.EGL_NO_SURFACE) return;
            try {
                makeCurrent();
                ensureFboSize(outputWidth, outputHeight);
                preparedBackdropReady = false;
                frameCoordinator.requestSourceFrame();
            } catch (Throwable error) {
                fail("output resize", error);
            }
        });
    }

    @Override
    public boolean onSurfaceTextureDestroyed(SurfaceTexture surface) {
        if (surface == outputSurfaceTexture) outputSurfaceTexture = null;
        Surface stale = outputWindowSurface;
        outputWindowSurface = null;
        outputWidth = 0;
        outputHeight = 0;
        preparedBackdropReady = false;
        gpuBackdropActive = false;
        dispatchPresentation(Set.of(), presentationState.sourceLost(inputProducerGeneration));
        if (stale != null) renderHandler.post(() -> destroyOutputWindow(stale));
        return true;
    }

    @Override
    public void onSurfaceTextureUpdated(SurfaceTexture surface) {
        // Output callback only. PassBlur input frames are driven by the independent input ST.
    }

    private void attachOutputWindow(Surface stale, Surface window, int width, int height) {
        if (shuttingDown) {
            if (window != null) window.release();
            return;
        }
        try {
            if (stale != null && stale != window) destroyOutputWindow(stale);
            ensureEglContext();
            destroyEglWindowSurfaceOnly();
            int[] attrs = new int[]{EGL14.EGL_NONE};
            eglWindowSurface = EGL14.eglCreateWindowSurface(
                    eglDisplay, eglConfig, window, attrs, 0);
            checkEglHandle("eglCreateWindowSurface", eglWindowSurface != EGL14.EGL_NO_SURFACE);
            makeCurrent();
            queryMaxTextureSize();
            // Mapping is View/screen geometry and therefore belongs on the UI thread. Do not
            // allocate the first FBO until that mapping has been recomputed with the real GPU
            // texture limit; otherwise one first frame could use capped FBOs with uncapped UVs.
            mainHandler.post(() -> {
                if (shuttingDown || outputWindowSurface != window) return;
                updateBackdropMapping();
                renderHandler.post(() -> finishOutputAttach(window, width, height));
            });
        } catch (Throwable error) {
            fail("output attach", error);
        }
    }

    private void finishOutputAttach(Surface window, int width, int height) {
        if (shuttingDown || outputWindowSurface != window
                || eglWindowSurface == EGL14.EGL_NO_SURFACE) return;
        try {
            makeCurrent();
            ensureGlResources();
            ensureFboSize(Math.max(1, width), Math.max(1, height));
            preparedBackdropReady = false;
            frameCoordinator.requestSourceFrame();
        } catch (Throwable error) {
            fail("output attach finish", error);
        }
    }

    private void queryMaxTextureSize() {
        if (maxTextureSize > 0) return;
        int[] value = new int[1];
        GLES20.glGetIntegerv(GLES20.GL_MAX_TEXTURE_SIZE, value, 0);
        if (value[0] <= 0) {
            throw new IllegalStateException("GL_MAX_TEXTURE_SIZE unavailable");
        }
        maxTextureSize = value[0];
        log(" GL_MAX_TEXTURE_SIZE=" + maxTextureSize);
    }

    private void destroyOutputWindow(Surface stale) {
        try {
            destroyEglWindowSurfaceOnly();
        } catch (Throwable error) {
            log(" output EGL surface destroy failed: " + error);
        }
        try {
            if (stale != null) stale.release();
        } catch (Throwable ignored) {}
    }

    private void ensureEglContext() {
        if (eglDisplay != EGL14.EGL_NO_DISPLAY
                && eglContext != EGL14.EGL_NO_CONTEXT
                && eglConfig != null) {
            return;
        }

        EGLDisplay display = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        checkEglHandle("eglGetDisplay", display != EGL14.EGL_NO_DISPLAY);
        int[] version = new int[2];
        if (!EGL14.eglInitialize(display, version, 0, version, 1)) {
            throw new IllegalStateException("eglInitialize error=0x"
                    + Integer.toHexString(EGL14.eglGetError()));
        }

        int[] configAttrs = new int[]{
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_WINDOW_BIT,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] count = new int[1];
        if (!EGL14.eglChooseConfig(display, configAttrs, 0, configs, 0, 1, count, 0)
                || count[0] <= 0 || configs[0] == null) {
            throw new IllegalStateException("eglChooseConfig error=0x"
                    + Integer.toHexString(EGL14.eglGetError()));
        }

        int[] contextAttrs = new int[]{
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        EGLContext context = EGL14.eglCreateContext(
                display, configs[0], EGL14.EGL_NO_CONTEXT, contextAttrs, 0);
        checkEglHandle("eglCreateContext", context != EGL14.EGL_NO_CONTEXT);

        eglDisplay = display;
        eglConfig = configs[0];
        eglContext = context;
    }

    private void ensureGlResources() {
        if (normalizeProgram != 0 && compositeProgram != 0 && prismalRenderer != null
                && oesTexture != 0 && inputSurfaceTexture != null && inputProducerSurface != null) {
            return;
        }

        normalizeProgram = createProgram(
                PassBlurShaders.QUAD_VERTEX,
                PassBlurShaders.OES_NORMALIZE_FRAGMENT);
        compositeProgram = createProgram(
                PassBlurShaders.QUAD_VERTEX,
                PassBlurShaders.COMPOSITE_FRAGMENT);
        if (normalizeProgram == 0 || compositeProgram == 0) {
            throw new IllegalStateException("Prismal adapter program creation failed");
        }
        if (prismalRenderer == null) prismalRenderer = new PrismalRenderer();

        createInputProducer();
        if (vendorPassBlurEnabled) {
            post(() -> bindProducerWhenReady(0));
        } else {
            log(" vendor PassBlur closed; initial producer remains unbound");
        }
    }

    private void createInputProducer() {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        oesTexture = textures[0];
        if (oesTexture == 0) throw new IllegalStateException("OES texture=0");
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture);
        GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(
                GLES11Ext.GL_TEXTURE_EXTERNAL_OES, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

        SurfaceTexture input = new SurfaceTexture(oesTexture);
        Surface producer = new Surface(input);
        inputSurfaceTexture = input;
        inputProducerSurface = producer;
        inputProducerGeneration = producerRecovery.observeProducer();
        input.setOnFrameAvailableListener(texture -> {
            if (shuttingDown || texture != inputSurfaceTexture
                    || !producerUpdatesEnabled || !vendorPassBlurEnabled) {
                return;
            }
            producerFrameCount++;
            frameAvailable.set(true);
            frameCoordinator.requestSourceFrame();
        }, renderHandler);
    }

    private void ensureFboSize(int width, int height) {
        if (maxTextureSize <= 0) {
            throw new IllegalStateException("FBO allocation before GL_MAX_TEXTURE_SIZE query");
        }
        if (width > maxTextureSize || height > maxTextureSize) {
            throw new IllegalStateException("visible material exceeds GL_MAX_TEXTURE_SIZE "
                    + width + "x" + height + " max=" + maxTextureSize);
        }
        SamplingInsets insets = resolveSamplingInsets(width, height);
        ensureFboSizeExact(
                Math.max(1, width + insets.left + insets.right),
                Math.max(1, height + insets.top + insets.bottom));
    }

    private void ensureFboSizeExact(int nextWidth, int nextHeight) {
        if (maxTextureSize <= 0) {
            throw new IllegalStateException("FBO allocation before GL_MAX_TEXTURE_SIZE query");
        }
        if (nextWidth <= 0 || nextHeight <= 0
                || nextWidth > maxTextureSize || nextHeight > maxTextureSize) {
            throw new IllegalStateException("sample FBO exceeds GL_MAX_TEXTURE_SIZE "
                    + nextWidth + "x" + nextHeight + " max=" + maxTextureSize);
        }
        if (rawFramebuffer != 0 && fboWidth == nextWidth && fboHeight == nextHeight) return;

        releaseFbos();
        rawTexture = createTexture2D(nextWidth, nextHeight);
        rawFramebuffer = createFramebuffer(rawTexture);
        fboWidth = nextWidth;
        fboHeight = nextHeight;
    }

    private void renderPendingFrame(boolean sourcePending, boolean scenePending) {
        if (shuttingDown || eglWindowSurface == EGL14.EGL_NO_SURFACE
                || normalizeProgram == 0 || compositeProgram == 0 || prismalRenderer == null
                || oesTexture == 0) {
            return;
        }
        SurfaceTexture input = inputSurfaceTexture;
        if (input == null) return;

        try {
            makeCurrent();
            boolean consumedFreshSource = false;
            if (sourcePending && frameAvailable.getAndSet(false)) {
                input.updateTexImage();
                input.getTransformMatrix(textureMatrix);
                producerRecovery.onFreshFrameConsumed();
                consumedFreshSource = true;
                SystemUiPassBlurBridge.Binding freshBinding = binding;
                if (freshBinding != null && freshBinding.bound
                        && freshBinding.endpointGeneration == inputProducerGeneration) {
                    presentationState.freshFrame(
                            freshBinding.surfaceSequenceId, inputProducerGeneration);
                }
            }
            if (!producerRecovery.hasFreshFrame()) return;

            BackdropSnapshot mapping = backdropSnapshot;
            if (mapping == null
                    || mapping.visibleWidth != outputWidth
                    || mapping.visibleHeight != outputHeight
                    || mapping.configRotation != boundConfigRotation) {
                return;
            }
            if (!firstFrameLogged && consumedFreshSource) {
                firstFrameLogged = true;
                log(" first OES frame configRot=" + mapping.configRotation);
            }
            if (!firstMatrixLogged && consumedFreshSource) {
                firstMatrixLogged = true;
                log(" texture matrix=" + formatTextureMatrix(textureMatrix)
                        + " stage=normalize-only configRot=" + mapping.configRotation);
            }

            DisplayTransform displayTransform = null;
            boolean mustPrepareBackdrop = consumedFreshSource
                    || !preparedBackdropReady
                    || preparedProducerGeneration != inputProducerGeneration
                    || preparedBackdropSnapshot != mapping;
            if (mustPrepareBackdrop) {
                if (!consumedFreshSource && !sourcePending) return;
                ensureFboSizeExact(mapping.sampleWidth, mapping.sampleHeight);
                displayTransform = renderNormalizationPass(mapping);
                prismalRenderer.prepareBackdrop(
                        rawTexture, mapping.sampleWidth, mapping.sampleHeight, mapping.prismalParams);
                preparedBackdropReady = true;
                preparedProducerGeneration = inputProducerGeneration;
                preparedBackdropSnapshot = mapping;
            }

            GlassSceneSnapshot scene = compositor.latestScene();
            Map<String, Long> currentNodes = lifecycleMap(scene);
            Set<String> sceneRevoked = presentationState.scene(
                    scene != null ? scene.generation() : -1L, currentNodes);
            dispatchPresentation(Set.of(), sceneRevoked);

            int insetLeft = Math.max(0, Math.round(mapping.dockUvLeft * mapping.sampleWidth));
            int insetBottom = Math.max(0, Math.round(mapping.dockUvBottom * mapping.sampleHeight));
            int insetTop = Math.max(0, mapping.sampleHeight - mapping.visibleHeight - insetBottom);
            compositor.drawFrame(prismalRenderer, scene,
                    mapping.sampleWidth, mapping.sampleHeight, insetLeft, insetTop);
            int prismalTexture = prismalRenderer.outputTexture();
            renderCompositePass(prismalTexture, mapping);

            int glError = GLES20.glGetError();
            if (glError != GLES20.GL_NO_ERROR) {
                throw new IllegalStateException("GLES error=0x" + Integer.toHexString(glError));
            }
            if (backdropSnapshot != mapping
                    || mapping.visibleWidth != outputWidth
                    || mapping.visibleHeight != outputHeight
                    || mapping.configRotation != boundConfigRotation) {
                return;
            }
            if (!EGL14.eglSwapBuffers(eglDisplay, eglWindowSurface)) {
                throw new IllegalStateException("eglSwapBuffers error=0x"
                        + Integer.toHexString(EGL14.eglGetError()));
            }
            renderedFrameCount++;
            long activationSourceGeneration = inputProducerGeneration;
            long activationSceneGeneration = scene != null ? scene.generation() : -1L;
            long activationSwapSequence = ++successfulSwapSequence;
            maybeLogPowerStats();

            SystemUiPassBlurBridge.Binding currentBinding = binding;
            gpuBackdropActive = currentBinding != null
                    && currentBinding.bound
                    && currentBinding.endpointGeneration == activationSourceGeneration;
            if (gpuBackdropActive) {
                GlassActivationToken token = new GlassActivationToken(
                        currentBinding.surfaceSequenceId,
                        activationSourceGeneration,
                        activationSceneGeneration,
                        materialProfiles.profileVersion(),
                        activationSwapSequence,
                        currentNodes);
                Set<String> authorized = presentationState.accept(token);
                dispatchPresentation(authorized, Set.of());
            }

            if (gpuBackdropActive && !firstDrawLogged
                    && scene != null && !scene.nodes().isEmpty()) {
                firstDrawLogged = true;
                log(" first EGL material draw"
                        + " textureDomain=normalized-2d"
                        + " material=prismal-module-official"
                        + " blur=official-two-pass-0.5x"
                        + " coverage=" + mapping.coverage
                        + " backdropRect=[" + mapping.backdropX + "," + mapping.backdropY + ","
                        + mapping.backdropW + "," + mapping.backdropH + "]"
                        + " validWindowRect=[" + mapping.validDockLeft + "," + mapping.validDockBottom + ","
                        + mapping.validDockRight + "," + mapping.validDockTop + "]"
                        + " output=" + mapping.visibleWidth + "x" + mapping.visibleHeight
                        + " producerSurface=" + mapping.surfaceWidth + "x" + mapping.surfaceHeight
                        + " producerBuffer=" + mapping.bufferWidth + "x" + mapping.bufferHeight
                        + " configRot=" + mapping.configRotation
                        + " producerGen=" + activationSourceGeneration
                        + " sceneGen=" + activationSceneGeneration
                        + " swapSeq=" + activationSwapSequence
                        + " sourcePending=" + sourcePending
                        + " scenePending=" + scenePending);
            }
            if (gpuBackdropActive && !stageBDiagnosticsLogged && displayTransform != null) {
                stageBDiagnosticsLogged = true;
                float[] textureMatrixSnapshot = textureMatrix.clone();
                DisplayTransform finalDisplayTransform = displayTransform;
                post(() -> logStageBDiagnostics(
                        finalDisplayTransform, textureMatrixSnapshot, mapping));
            }
            if (gpuBackdropActive && !prismalMappingLogged) {
                prismalMappingLogged = true;
            }
        } catch (Throwable error) {
            fail("draw", error);
        }
    }

    private static Map<String, Long> lifecycleMap(GlassSceneSnapshot scene) {
        if (scene == null || scene.nodes().isEmpty()) return Map.of();
        Map<String, Long> result = new LinkedHashMap<>();
        for (GlassNode node : scene.nodes()) {
            if (node != null && node.drawable()) {
                result.put(node.id(), node.lifecycleGeneration());
            }
        }
        return Map.copyOf(result);
    }

    private void dispatchPresentation(Set<String> authorized, Set<String> revoked) {
        if ((authorized == null || authorized.isEmpty())
                && (revoked == null || revoked.isEmpty())) return;
        PresentationListener listener = presentationListener;
        if (listener == null) return;
        Set<String> safeAuthorized = authorized == null ? Set.of() : Set.copyOf(authorized);
        Set<String> safeRevoked = revoked == null ? Set.of() : Set.copyOf(revoked);
        if (android.os.Looper.myLooper() == getContext().getMainLooper()) {
            listener.onPresentationChanged(safeAuthorized, safeRevoked);
        } else {
            mainHandler.post(() -> listener.onPresentationChanged(safeAuthorized, safeRevoked));
        }
    }

    private void maybeLogPowerStats() {
        long now = SystemClock.uptimeMillis();
        long elapsed = now - powerWindowStartedMs;
        if (elapsed < 5000L) return;
        float seconds = Math.max(0.001f, elapsed / 1000f);
        float producerFps = producerFrameCount / seconds;
        float drawFps = renderedFrameCount / seconds;
        GlassSceneSnapshot scene = compositor.latestScene();
        log("[GlassCore][PBTX][Power] producerFps=" + producerFps
                + " drawFps=" + drawFps
                + " glassNodes=" + (scene != null ? scene.nodes().size() : 0)
                + " attached=" + isAttachedToWindow()
                + " shown=" + isShown());
        producerFrameCount = 0L;
        renderedFrameCount = 0L;
        powerWindowStartedMs = now;
    }

    private DisplayTransform renderNormalizationPass(BackdropSnapshot mapping) {
        SystemUiPassBlurBridge.Binding currentBinding = binding;
        long rootGeneration = currentBinding != null
                ? currentBinding.surfaceSequenceId : -1L;
        DisplayTransform transform = DisplayTransformEngine.compose(
                new DisplayTransformSnapshot(
                        mapping.displayId,
                        mapping.displayRotation,
                        mapping.configRotation,
                        mapping.windowLeft,
                        mapping.windowTop,
                        mapping.windowWidth,
                        mapping.windowHeight,
                        mapping.sampleLeft,
                        mapping.sampleTop,
                        mapping.sampleWidth,
                        mapping.sampleHeight,
                        mapping.bufferWidth,
                        mapping.bufferHeight,
                        textureMatrix,
                        rootGeneration,
                        inputProducerGeneration));
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, rawFramebuffer);
        GLES20.glViewport(0, 0, fboWidth, fboHeight);
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);
        GLES20.glUseProgram(normalizeProgram);
        bindQuad(normalizeProgram);

        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, oesTexture);
        GLES20.glUniform1i(requireUniform(normalizeProgram, "uTexture"), 0);
        GLES20.glUniformMatrix4fv(
                requireUniform(normalizeProgram, "uWindowUvToOes"),
                1, false, transform.windowUvToOes(), 0);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        unbindQuad(normalizeProgram);
        return transform;
    }

    private void renderCompositePass(int prismalTexture, BackdropSnapshot mapping) {
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        GLES20.glViewport(0, 0, mapping.visibleWidth, mapping.visibleHeight);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
        GLES20.glClearColor(0f, 0f, 0f, 0f);
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT);

        if (mapping.coverage == BackdropMapping.Coverage.OUTSIDE
                || mapping.validDockRight <= mapping.validDockLeft
                || mapping.validDockTop <= mapping.validDockBottom) {
            return;
        }
        if (mapping.coverage == BackdropMapping.Coverage.PARTIAL) {
            int left = Math.max(0, Math.round(mapping.validDockLeft * mapping.visibleWidth));
            int bottom = Math.max(0, Math.round(mapping.validDockBottom * mapping.visibleHeight));
            int right = Math.min(mapping.visibleWidth,
                    Math.round(mapping.validDockRight * mapping.visibleWidth));
            int top = Math.min(mapping.visibleHeight,
                    Math.round(mapping.validDockTop * mapping.visibleHeight));
            GLES20.glEnable(GLES20.GL_SCISSOR_TEST);
            GLES20.glScissor(left, bottom, Math.max(0, right - left), Math.max(0, top - bottom));
        }

        GLES20.glEnable(GLES20.GL_BLEND);
        GLES20.glBlendFuncSeparate(
                GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA,
                GLES20.GL_ONE, GLES20.GL_ONE_MINUS_SRC_ALPHA);
        GLES20.glUseProgram(compositeProgram);
        bindQuad(compositeProgram);
        GLES20.glActiveTexture(GLES20.GL_TEXTURE0);
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, prismalTexture);
        GLES20.glUniform1i(requireUniform(compositeProgram, "uTexture"), 0);
        GLES20.glUniform4f(requireUniform(compositeProgram, "uCropRect"),
                mapping.dockUvLeft, mapping.dockUvBottom, mapping.dockUvWidth, mapping.dockUvHeight);
        GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4);
        unbindQuad(compositeProgram);
        GLES20.glDisable(GLES20.GL_BLEND);
        GLES20.glDisable(GLES20.GL_SCISSOR_TEST);
    }

    private void bindQuad(int program) {
        int position = GLES20.glGetAttribLocation(program, "aPosition");
        int uv = GLES20.glGetAttribLocation(program, "aUv");
        if (position < 0 || uv < 0) throw new IllegalStateException("quad attribute unavailable");
        quadBuffer.position(0);
        GLES20.glEnableVertexAttribArray(position);
        GLES20.glVertexAttribPointer(
                position, 2, GLES20.GL_FLOAT, false, 4 * Float.BYTES, quadBuffer);
        quadBuffer.position(2);
        GLES20.glEnableVertexAttribArray(uv);
        GLES20.glVertexAttribPointer(
                uv, 2, GLES20.GL_FLOAT, false, 4 * Float.BYTES, quadBuffer);
    }

    private void unbindQuad(int program) {
        int position = GLES20.glGetAttribLocation(program, "aPosition");
        int uv = GLES20.glGetAttribLocation(program, "aUv");
        if (position >= 0) GLES20.glDisableVertexAttribArray(position);
        if (uv >= 0) GLES20.glDisableVertexAttribArray(uv);
    }

    private void bindProducerWhenReady(int attempt) {
        if (shuttingDown || binding != null || !vendorPassBlurEnabled || !rootSurfaceAvailable) return;
        View materialHost = materialHostRef.get();
        Surface producer = inputProducerSurface;
        SurfaceTexture input = inputSurfaceTexture;
        long endpointGeneration = inputProducerGeneration;
        if (materialHost == null || !materialHost.isAttachedToWindow()
                || !isAttachedToWindow() || !isAvailable()
                || producer == null || input == null) {
            retryBind(attempt, "views/input not ready");
            return;
        }

        ProducerGeometry geometry = readSurfaceGeometry(materialHost);
        if (geometry == null || geometry.bufferWidth <= 0 || geometry.bufferHeight <= 0
                || geometry.rootSurface == null || !geometry.rootSurface.isValid()) {
            retryBind(attempt, "Window root not ready");
            return;
        }

        renderHandler.post(() -> {
            SurfaceTexture currentInput = inputSurfaceTexture;
            if (shuttingDown || currentInput == null || currentInput != input
                    || endpointGeneration != inputProducerGeneration) return;
            try {
                currentInput.setDefaultBufferSize(geometry.bufferWidth, geometry.bufferHeight);
                post(() -> finishBindProducer(geometry, producer, endpointGeneration, attempt));
            } catch (Throwable error) {
                post(() -> retryBind(attempt, error.getClass().getSimpleName()));
            }
        });
    }

    private void finishBindProducer(ProducerGeometry geometry, Surface producer,
                                    long endpointGeneration, int attempt) {
        if (shuttingDown || binding != null || !vendorPassBlurEnabled
                || producer != inputProducerSurface
                || endpointGeneration != inputProducerGeneration) return;
        View materialHost = materialHostRef.get();
        if (materialHost == null) return;

        ProducerGeometry current = readSurfaceGeometry(materialHost);
        if (current == null || current.rootSurface == null || !current.rootSurface.isValid()
                || !isSameSurface(current.rootSurface, geometry.rootSurface)) {
            retryBind(attempt, "Window root changed before bind");
            return;
        }

        SystemUiPassBlurBridge.Binding next = SystemUiPassBlurBridge.bind(
                materialHost, current.rootSurface, producer, endpointGeneration);
        if (next == null) {
            retryBind(attempt, "framework bind returned null");
            return;
        }

        binding = next;
        if (!producerUpdatesEnabled) {
            SystemUiPassBlurBridge.pauseUpdates(next);
        }
        producerRecovery.onBindSucceeded();
        Set<String> revoked = presentationState.sourceBound(
                next.surfaceSequenceId, endpointGeneration, materialProfiles.profileVersion());
        dispatchPresentation(Set.of(), revoked);
        configRotation = current.configRotation;
        boundSurfaceWidth = current.surfaceWidth;
        boundSurfaceHeight = current.surfaceHeight;
        boundBufferWidth = current.bufferWidth;
        boundBufferHeight = current.bufferHeight;
        boundConfigRotation = current.configRotation;
        stageBDiagnosticsLogged = false;
        prismalMappingLogged = false;
        updateBackdropMapping();
        log(" native Window PassBlur source bound"
                + " root=" + current.rootSurface
                + " endpointGen=" + endpointGeneration
                + " surface=" + current.surfaceWidth + "x" + current.surfaceHeight
                + " buffer=" + current.bufferWidth + "x" + current.bufferHeight
                + " configRot=" + current.configRotation);
    }

    private void retryBind(int attempt, String reason) {
        if (shuttingDown || binding != null || !vendorPassBlurEnabled) return;
        if (attempt >= MAX_BIND_RETRY_FRAMES) {
            producerRecovery.onBindExhausted();
            IllegalStateException failure = new IllegalStateException(
                    "PassBlur activation exhausted: " + reason);
            Set<String> revoked = presentationState.terminalFailure();
            dispatchPresentation(Set.of(), revoked);
            if (presentationListener != null) presentationListener.onTerminalFailure("bind", failure);
            log("PassBlur TextureView activation exhausted reason=" + reason);
            return;
        }
        postOnAnimation(() -> bindProducerWhenReady(attempt + 1));
    }

    private void installGeometryObserver() {
        removeGeometryObserver();
        View root = getRootView();
        ViewTreeObserver observer = root != null ? root.getViewTreeObserver() : null;
        if (observer == null || !observer.isAlive()) return;
        ViewTreeObserver.OnPreDrawListener listener = () -> {
            refreshProducerGeometryInPlace();
            updateBackdropMapping();
            return true;
        };
        observer.addOnPreDrawListener(listener);
        preDrawObserver = observer;
        preDrawListener = listener;
    }

    private void removeGeometryObserver() {
        ViewTreeObserver observer = preDrawObserver;
        ViewTreeObserver.OnPreDrawListener listener = preDrawListener;
        preDrawObserver = null;
        preDrawListener = null;
        if (observer == null || listener == null) return;
        try {
            if (observer.isAlive()) observer.removeOnPreDrawListener(listener);
        } catch (Throwable ignored) {}
    }

    private void refreshProducerGeometryInPlace() {
        if (shuttingDown || binding == null) return;
        View materialHost = materialHostRef.get();
        SurfaceTexture input = inputSurfaceTexture;
        if (materialHost == null || input == null) return;

        ProducerGeometry geometry = readSurfaceGeometry(materialHost);
        if (geometry == null || geometry.rootSurface == null || !geometry.rootSurface.isValid()) return;
        if (!binding.hostRootSurface.isValid()
                || !isSameSurface(binding.hostRootSurface, geometry.rootSurface)) {
            rebindProducer("host-root-changed");
            return;
        }
        if (geometry.surfaceWidth == boundSurfaceWidth
                && geometry.surfaceHeight == boundSurfaceHeight
                && geometry.bufferWidth == boundBufferWidth
                && geometry.bufferHeight == boundBufferHeight
                && geometry.configRotation == boundConfigRotation) {
            return;
        }

        configRotation = geometry.configRotation;
        boundSurfaceWidth = geometry.surfaceWidth;
        boundSurfaceHeight = geometry.surfaceHeight;
        boundBufferWidth = geometry.bufferWidth;
        boundBufferHeight = geometry.bufferHeight;
        boundConfigRotation = geometry.configRotation;
        ProducerRecoveryState.Decision invalidated =
                producerRecovery.onGeometryInvalidated();
        if (invalidated.clearFrameAvailable()) frameAvailable.set(false);
        dispatchPresentation(Set.of(), presentationState.sourceBound(
                binding.surfaceSequenceId, inputProducerGeneration, materialProfiles.profileVersion()));
        preparedBackdropReady = false;
        firstFrameLogged = false;
        firstDrawLogged = false;
        firstMatrixLogged = false;
        stageBDiagnosticsLogged = false;
        prismalMappingLogged = false;
        updateBackdropMapping();
        renderHandler.post(() -> {
            SurfaceTexture currentInput = inputSurfaceTexture;
            if (!shuttingDown && currentInput == input) {
                currentInput.setDefaultBufferSize(geometry.bufferWidth, geometry.bufferHeight);
            }
        });
        log(" producer geometry updated in place surface="
                + geometry.surfaceWidth + "x" + geometry.surfaceHeight
                + " buffer=" + geometry.bufferWidth + "x" + geometry.bufferHeight
                + " configRot=" + geometry.configRotation);
    }

    private int horizontalOverscanPx() {
        float density = getResources().getDisplayMetrics().density;
        return Math.max(1, Math.round(EDGE_OVERSCAN_DP * Math.max(0.1f, density)));
    }

    private SamplingInsets resolveSamplingInsets(int width, int height) {
        return resolveSamplingInsets(width, height, portablePrismalParams);
    }

    private SamplingInsets resolveSamplingInsets(
            int width, int height, PrismalParams prismalParams) {
        if (prismalParams == null) return new SamplingInsets(0, 0, 0, 0);
        int opticalX = PrismalSampling.requiredGuardPx(
                prismalParams, width, height, true);
        int opticalY = PrismalSampling.requiredGuardPx(
                prismalParams, width, height, false);

        int autoHorizontal = Math.max(horizontalOverscanPx(), opticalX);
        int left = combineAutoGuardAndUserExtra(autoHorizontal, leftSamplingExtraPx);
        int right = combineAutoGuardAndUserExtra(autoHorizontal, rightSamplingExtraPx);
        int top = combineAutoGuardAndUserExtra(opticalY, topSamplingExtraPx);
        int bottom = combineAutoGuardAndUserExtra(opticalY, bottomSamplingExtraPx);

        int[] horizontal = fitInsetPairToTextureLimit(width, left, right, maxTextureSize);
        int[] vertical = fitInsetPairToTextureLimit(height, top, bottom, maxTextureSize);
        return new SamplingInsets(horizontal[0], horizontal[1], vertical[0], vertical[1]);
    }

    private static int combineAutoGuardAndUserExtra(int automaticGuardPx, int userExtraPx) {
        long combined = (long) Math.max(0, automaticGuardPx) + userExtraPx;
        return (int) Math.max(0L, Math.min(Integer.MAX_VALUE, combined));
    }

    private static int[] fitInsetPairToTextureLimit(
            int visible, int before, int after, int maxTextureSize) {
        int safeVisible = Math.max(1, visible);
        int safeBefore = Math.max(0, before);
        int safeAfter = Math.max(0, after);
        if (maxTextureSize <= 0) return new int[]{safeBefore, safeAfter};

        int available = Math.max(0, maxTextureSize - safeVisible);
        long desired = (long) safeBefore + safeAfter;
        if (desired <= available) return new int[]{safeBefore, safeAfter};
        if (available <= 0 || desired <= 0) return new int[]{0, 0};

        int fittedBefore = (int) Math.round(safeBefore * (available / (double) desired));
        fittedBefore = Math.max(0, Math.min(available, fittedBefore));
        int fittedAfter = available - fittedBefore;
        return new int[]{fittedBefore, fittedAfter};
    }

    private void updateBackdropMapping() {
        if (shuttingDown || !isAttachedToWindow()) return;
        int visibleWidth = outputWidth > 0 ? outputWidth : getWidth();
        int visibleHeight = outputHeight > 0 ? outputHeight : getHeight();
        if (visibleWidth <= 0 || visibleHeight <= 0) return;

        Rect winFrame = readViewRootRectField(this, "mWinFrameInScreen");
        if (winFrame == null || winFrame.width() <= 0 || winFrame.height() <= 0) return;
        int[] viewScreen = new int[2];
        getLocationOnScreen(viewScreen);
        Display display = getDisplay();
        int displayId = display != null ? display.getDisplayId() : 0;
        int displayRotation = display != null ? display.getRotation() : 0;

        PrismalParams frameParams = portablePrismalParams;
        if (frameParams == null) return;
        SamplingInsets insets = resolveSamplingInsets(visibleWidth, visibleHeight, frameParams);
        int sampleWidth = visibleWidth + insets.left + insets.right;
        int sampleHeight = visibleHeight + insets.top + insets.bottom;
        BackdropMapping.Result sample = BackdropMapping.compute(
                viewScreen[0] - insets.left, viewScreen[1] - insets.top,
                sampleWidth, sampleHeight,
                winFrame.left, winFrame.top, winFrame.width(), winFrame.height());
        BackdropMapping.Result dock = BackdropMapping.compute(
                viewScreen[0], viewScreen[1], visibleWidth, visibleHeight,
                winFrame.left, winFrame.top, winFrame.width(), winFrame.height());

        float nextDockUvLeft = insets.left / (float) sampleWidth;
        float nextDockUvBottom = insets.bottom / (float) sampleHeight;
        float nextDockUvWidth = visibleWidth / (float) sampleWidth;
        float nextDockUvHeight = visibleHeight / (float) sampleHeight;

        BackdropSnapshot currentSnapshot = backdropSnapshot;
        boolean unchanged = currentSnapshot != null
                && currentSnapshot.visibleWidth == visibleWidth
                && currentSnapshot.visibleHeight == visibleHeight
                && currentSnapshot.sampleWidth == sampleWidth
                && currentSnapshot.sampleHeight == sampleHeight
                && currentSnapshot.displayId == displayId
                && currentSnapshot.displayRotation == displayRotation
                && currentSnapshot.configRotation == configRotation
                && currentSnapshot.windowLeft == winFrame.left
                && currentSnapshot.windowTop == winFrame.top
                && currentSnapshot.windowWidth == winFrame.width()
                && currentSnapshot.windowHeight == winFrame.height()
                && currentSnapshot.sampleLeft == viewScreen[0] - insets.left
                && currentSnapshot.sampleTop == viewScreen[1] - insets.top
                && currentSnapshot.surfaceWidth == boundSurfaceWidth
                && currentSnapshot.surfaceHeight == boundSurfaceHeight
                && currentSnapshot.bufferWidth == boundBufferWidth
                && currentSnapshot.bufferHeight == boundBufferHeight
                && currentSnapshot.prismalParams == frameParams
                && Float.compare(backdropX, sample.backdropX) == 0
                && Float.compare(backdropY, sample.backdropY) == 0
                && Float.compare(backdropW, sample.backdropW) == 0
                && Float.compare(backdropH, sample.backdropH) == 0
                && Float.compare(validSampleLeft, sample.validLeft) == 0
                && Float.compare(validSampleBottom, sample.validBottom) == 0
                && Float.compare(validSampleRight, sample.validRight) == 0
                && Float.compare(validSampleTop, sample.validTop) == 0
                && Float.compare(validDockLeft, dock.validLeft) == 0
                && Float.compare(validDockBottom, dock.validBottom) == 0
                && Float.compare(validDockRight, dock.validRight) == 0
                && Float.compare(validDockTop, dock.validTop) == 0
                && Float.compare(dockUvLeft, nextDockUvLeft) == 0
                && Float.compare(dockUvBottom, nextDockUvBottom) == 0
                && Float.compare(dockUvWidth, nextDockUvWidth) == 0
                && Float.compare(dockUvHeight, nextDockUvHeight) == 0
                && producerCoverage == dock.coverage;
        if (unchanged) return;

        backdropX = sample.backdropX;
        backdropY = sample.backdropY;
        backdropW = sample.backdropW;
        backdropH = sample.backdropH;
        validSampleLeft = sample.validLeft;
        validSampleBottom = sample.validBottom;
        validSampleRight = sample.validRight;
        validSampleTop = sample.validTop;
        validDockLeft = dock.validLeft;
        validDockBottom = dock.validBottom;
        validDockRight = dock.validRight;
        validDockTop = dock.validTop;
        dockUvLeft = nextDockUvLeft;
        dockUvBottom = nextDockUvBottom;
        dockUvWidth = nextDockUvWidth;
        dockUvHeight = nextDockUvHeight;
        producerCoverage = dock.coverage;
        // Volatile publication is deliberately last: the GL thread never observes a mixture of
        // output size, overscan FBO, UV crop, rotation, or Prismal parameter generations.
        backdropSnapshot = new BackdropSnapshot(
                visibleWidth, visibleHeight,
                sampleWidth, sampleHeight,
                displayId, displayRotation,
                configRotation,
                winFrame.left, winFrame.top, winFrame.width(), winFrame.height(),
                viewScreen[0] - insets.left, viewScreen[1] - insets.top,
                boundSurfaceWidth, boundSurfaceHeight,
                boundBufferWidth, boundBufferHeight,
                frameParams,
                sample.backdropX, sample.backdropY, sample.backdropW, sample.backdropH,
                sample.validLeft, sample.validBottom, sample.validRight, sample.validTop,
                dock.validLeft, dock.validBottom, dock.validRight, dock.validTop,
                nextDockUvLeft, nextDockUvBottom, nextDockUvWidth, nextDockUvHeight,
                dock.coverage);
        stageBDiagnosticsLogged = false;
        prismalMappingLogged = false;
        if (producerRecovery.hasFreshFrame()) frameCoordinator.requestSourceFrame();
    }

    private ProducerGeometry readSurfaceGeometry(View materialHost) {
        if (materialHost == null) return null;
        try {
            Object viewRoot = getViewRootImpl(materialHost);
            if (viewRoot == null) return null;
            Field sizeField = findField(viewRoot.getClass(), "mSurfaceSize");
            sizeField.setAccessible(true);
            Object value = sizeField.get(viewRoot);
            if (!(value instanceof Point)) return null;
            Point surfaceSize = (Point) value;
            int surfaceWidth = surfaceSize.x;
            int surfaceHeight = surfaceSize.y;
            if (surfaceWidth <= 0 || surfaceHeight <= 0) return null;

            int nextRotation = readConfigRotation(materialHost);
            int bufferWidth = surfaceWidth;
            int bufferHeight = surfaceHeight;
            if (nextRotation == 1 || nextRotation == 3) {
                bufferWidth = surfaceHeight;
                bufferHeight = surfaceWidth;
            }

            Method getSurfaceControl = viewRoot.getClass().getDeclaredMethod("getSurfaceControl");
            getSurfaceControl.setAccessible(true);
            Object surface = getSurfaceControl.invoke(viewRoot);
            SurfaceControl rootSurface = surface instanceof SurfaceControl
                    ? (SurfaceControl) surface : null;
            return new ProducerGeometry(
                    surfaceWidth, surfaceHeight,
                    bufferWidth, bufferHeight,
                    nextRotation, rootSurface);
        } catch (Throwable error) {
            log(" producer geometry unavailable: " + error);
            return null;
        }
    }

    private void logStageBDiagnostics(
            DisplayTransform transform,
            float[] textureMatrixSnapshot,
            BackdropSnapshot mapping) {
        if (shuttingDown || mapping == null) return;
        View materialHost = materialHostRef.get();
        if (materialHost == null) return;
        View root = materialHost.getRootView();
        if (root == null) return;

        int[] viewScreen = new int[2];
        int[] hostScreen = new int[2];
        int[] rootScreen = new int[2];
        getLocationOnScreen(viewScreen);
        materialHost.getLocationOnScreen(hostScreen);
        root.getLocationOnScreen(rootScreen);
        Rect winFrame = readViewRootRectField(this, "mWinFrameInScreen");

        float[] bl = transform.map(0f, 0f);
        float[] br = transform.map(1f, 0f);
        float[] tl = transform.map(0f, 1f);
        float[] tr = transform.map(1f, 1f);

        log(" stage-B mapping rootScreen=["
                + rootScreen[0] + "," + rootScreen[1] + "]"
                + " viewScreen=[" + viewScreen[0] + "," + viewScreen[1] + "]"
                + " hostScreen=[" + hostScreen[0] + "," + hostScreen[1] + "]"
                + " hostSize=" + materialHost.getWidth() + "x" + materialHost.getHeight()
                + " winFrame=" + formatRect(winFrame)
                + " rootSurface=" + mapping.surfaceWidth + "x" + mapping.surfaceHeight
                + " producerBuffer=" + mapping.bufferWidth + "x" + mapping.bufferHeight
                + " coverage=" + mapping.coverage
                + " backdropRect=[" + mapping.backdropX + "," + mapping.backdropY + ","
                + mapping.backdropW + "," + mapping.backdropH + "]"
                + " validDockRect=[" + mapping.validDockLeft + "," + mapping.validDockBottom + ","
                + mapping.validDockRight + "," + mapping.validDockTop + "]"
                + " configRot=" + mapping.configRotation
                + " texture matrix=" + formatTextureMatrix(textureMatrixSnapshot)
                + " composed matrix=" + formatTextureMatrix(transform.windowUvToOes())
                + " mapped corners bl=[" + bl[0] + "," + bl[1] + "]"
                + " br=[" + br[0] + "," + br[1] + "]"
                + " tl=[" + tl[0] + "," + tl[1] + "]"
                + " tr=[" + tr[0] + "," + tr[1] + "]");
    }

    private static int createTexture2D(int width, int height) {
        int[] textures = new int[1];
        GLES20.glGenTextures(1, textures, 0);
        int texture = textures[0];
        if (texture == 0) throw new IllegalStateException("2D texture=0");
        GLES20.glBindTexture(GLES20.GL_TEXTURE_2D, texture);
        GLES20.glTexImage2D(
                GLES20.GL_TEXTURE_2D, 0, GLES20.GL_RGBA,
                width, height, 0, GLES20.GL_RGBA, GLES20.GL_UNSIGNED_BYTE, null);
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(
                GLES20.GL_TEXTURE_2D, GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
        return texture;
    }

    private static int createFramebuffer(int texture) {
        int[] framebuffers = new int[1];
        GLES20.glGenFramebuffers(1, framebuffers, 0);
        int framebuffer = framebuffers[0];
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, framebuffer);
        GLES20.glFramebufferTexture2D(
                GLES20.GL_FRAMEBUFFER, GLES20.GL_COLOR_ATTACHMENT0,
                GLES20.GL_TEXTURE_2D, texture, 0);
        int status = GLES20.glCheckFramebufferStatus(GLES20.GL_FRAMEBUFFER);
        GLES20.glBindFramebuffer(GLES20.GL_FRAMEBUFFER, 0);
        if (status != GLES20.GL_FRAMEBUFFER_COMPLETE) {
            throw new IllegalStateException("framebuffer incomplete=0x" + Integer.toHexString(status));
        }
        return framebuffer;
    }

    private void releaseFbos() {
        if (rawFramebuffer != 0) {
            GLES20.glDeleteFramebuffers(1, new int[]{rawFramebuffer}, 0);
            rawFramebuffer = 0;
        }
        if (rawTexture != 0) {
            GLES20.glDeleteTextures(1, new int[]{rawTexture}, 0);
            rawTexture = 0;
        }
        fboWidth = 0;
        fboHeight = 0;
    }

    private void makeCurrent() {
        if (eglDisplay == EGL14.EGL_NO_DISPLAY
                || eglContext == EGL14.EGL_NO_CONTEXT
                || eglWindowSurface == EGL14.EGL_NO_SURFACE) {
            throw new IllegalStateException("EGL output unavailable");
        }
        if (!EGL14.eglMakeCurrent(
                eglDisplay, eglWindowSurface, eglWindowSurface, eglContext)) {
            throw new IllegalStateException("eglMakeCurrent error=0x"
                    + Integer.toHexString(EGL14.eglGetError()));
        }
    }

    private void destroyEglWindowSurfaceOnly() {
        if (eglDisplay == EGL14.EGL_NO_DISPLAY || eglWindowSurface == EGL14.EGL_NO_SURFACE) return;
        try {
            EGL14.eglMakeCurrent(
                    eglDisplay, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_SURFACE, EGL14.EGL_NO_CONTEXT);
        } catch (Throwable ignored) {}
        EGL14.eglDestroySurface(eglDisplay, eglWindowSurface);
        eglWindowSurface = EGL14.EGL_NO_SURFACE;
    }

    private void releaseRenderResources() {
        try {
            if (eglDisplay != EGL14.EGL_NO_DISPLAY
                    && eglWindowSurface != EGL14.EGL_NO_SURFACE
                    && eglContext != EGL14.EGL_NO_CONTEXT) {
                makeCurrent();
            }
        } catch (Throwable ignored) {}

        try { releaseFbos(); } catch (Throwable ignored) {}
        if (prismalRenderer != null) {
            try { prismalRenderer.close(); } catch (Throwable ignored) {}
            prismalRenderer = null;
        }
        if (oesTexture != 0) {
            try { GLES20.glDeleteTextures(1, new int[]{oesTexture}, 0); } catch (Throwable ignored) {}
            oesTexture = 0;
        }
        int[] programs = new int[]{normalizeProgram, compositeProgram};
        for (int program : programs) {
            if (program != 0) {
                try { GLES20.glDeleteProgram(program); } catch (Throwable ignored) {}
            }
        }
        normalizeProgram = 0;
        compositeProgram = 0;

        Surface producer = inputProducerSurface;
        inputProducerSurface = null;
        if (producer != null) {
            try { producer.release(); } catch (Throwable ignored) {}
        }
        SurfaceTexture input = inputSurfaceTexture;
        inputSurfaceTexture = null;
        if (input != null) {
            try { input.release(); } catch (Throwable ignored) {}
        }

        Surface output = outputWindowSurface;
        outputWindowSurface = null;
        destroyEglWindowSurfaceOnly();
        if (output != null) {
            try { output.release(); } catch (Throwable ignored) {}
        }

        if (eglDisplay != EGL14.EGL_NO_DISPLAY && eglContext != EGL14.EGL_NO_CONTEXT) {
            try { EGL14.eglDestroyContext(eglDisplay, eglContext); } catch (Throwable ignored) {}
        }
        if (eglDisplay != EGL14.EGL_NO_DISPLAY) {
            try { EGL14.eglTerminate(eglDisplay); } catch (Throwable ignored) {}
        }
        eglContext = EGL14.EGL_NO_CONTEXT;
        eglDisplay = EGL14.EGL_NO_DISPLAY;
        eglConfig = null;
        outputSurfaceTexture = null;
    }

    private void resetBoundGeometry() {
        boundSurfaceWidth = 0;
        boundSurfaceHeight = 0;
        boundBufferWidth = 0;
        boundBufferHeight = 0;
        boundConfigRotation = -1;
        backdropX = 0f;
        backdropY = 0f;
        backdropW = 1f;
        backdropH = 1f;
        validSampleLeft = 0f;
        validSampleBottom = 0f;
        validSampleRight = 1f;
        validSampleTop = 1f;
        validDockLeft = 0f;
        validDockBottom = 0f;
        validDockRight = 1f;
        validDockTop = 1f;
        dockUvLeft = 0f;
        dockUvBottom = 0f;
        dockUvWidth = 1f;
        dockUvHeight = 1f;
        producerCoverage = BackdropMapping.Coverage.FULL;
        preparedBackdropReady = false;
        preparedProducerGeneration = -1L;
        preparedBackdropSnapshot = null;
    }

    private void fail(String stage, Throwable error) {
        producerRecovery.onTerminalFailure();
        gpuBackdropActive = false;
        Set<String> revoked = presentationState.terminalFailure();
        dispatchPresentation(Set.of(), revoked);
        if (presentationListener != null) {
            post(() -> presentationListener.onTerminalFailure(stage, error));
        }
        log(" PassBlur TextureView " + stage + " failed: " + error);
        requestTerminalShutdown();
    }

    private void requestTerminalShutdown() {
        if (shuttingDown) return;
        if (android.os.Looper.myLooper() == getContext().getMainLooper()) {
            shutdown();
        } else {
            mainHandler.post(this::shutdown);
        }
    }

    private static int readConfigRotation(View materialHost) {
        Display display = materialHost != null ? materialHost.getDisplay() : null;
        if (display == null) return 0;
        int installOrientation = 0;
        try {
            Method method = Display.class.getMethod("getInstallOrientation");
            Object value = method.invoke(display);
            if (value instanceof Number) installOrientation = ((Number) value).intValue();
        } catch (Throwable ignored) {}
        int rotation = display.getRotation();
        int result = (installOrientation + rotation) % 4;
        return result < 0 ? result + 4 : result;
    }

    private static Rect readViewRootRectField(View view, String fieldName) {
        if (view == null) return null;
        try {
            Object viewRoot = getViewRootImpl(view);
            if (viewRoot == null) return null;
            Field field = findField(viewRoot.getClass(), fieldName);
            field.setAccessible(true);
            Object value = field.get(viewRoot);
            return value instanceof Rect ? new Rect((Rect) value) : null;
        } catch (Throwable ignored) {
            return null;
        }
    }

    private static Object getViewRootImpl(View view) throws Exception {
        Method method = View.class.getDeclaredMethod("getViewRootImpl");
        method.setAccessible(true);
        return method.invoke(view);
    }

    private static Field findField(Class<?> type, String name) throws NoSuchFieldException {
        Class<?> current = type;
        while (current != null) {
            try {
                return current.getDeclaredField(name);
            } catch (NoSuchFieldException ignored) {
                current = current.getSuperclass();
            }
        }
        throw new NoSuchFieldException(name);
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

    private static int requireUniform(int program, String name) {
        int location = GLES20.glGetUniformLocation(program, name);
        if (location < 0) throw new IllegalStateException("missing uniform " + name);
        return location;
    }

    private static void checkEglHandle(String stage, boolean ok) {
        if (!ok) {
            throw new IllegalStateException(stage + " error=0x"
                    + Integer.toHexString(EGL14.eglGetError()));
        }
    }

    private static int createProgram(String vertexSource, String fragmentSource) {
        int vertex = compileShader(GLES20.GL_VERTEX_SHADER, vertexSource);
        int fragment = compileShader(GLES20.GL_FRAGMENT_SHADER, fragmentSource);
        if (vertex == 0 || fragment == 0) {
            if (vertex != 0) GLES20.glDeleteShader(vertex);
            if (fragment != 0) GLES20.glDeleteShader(fragment);
            return 0;
        }
        int result = GLES20.glCreateProgram();
        GLES20.glAttachShader(result, vertex);
        GLES20.glAttachShader(result, fragment);
        GLES20.glLinkProgram(result);
        int[] linked = new int[1];
        GLES20.glGetProgramiv(result, GLES20.GL_LINK_STATUS, linked, 0);
        GLES20.glDeleteShader(vertex);
        GLES20.glDeleteShader(fragment);
        if (linked[0] == 0) {
            log(" program link failed: " + GLES20.glGetProgramInfoLog(result));
            GLES20.glDeleteProgram(result);
            return 0;
        }
        return result;
    }

    private static int compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0);
        if (compiled[0] == 0) {
            log(" shader compile failed: " + GLES20.glGetShaderInfoLog(shader));
            GLES20.glDeleteShader(shader);
            return 0;
        }
        return shader;
    }

    private static String formatRect(Rect rect) {
        if (rect == null) return "unavailable";
        return "[" + rect.left + "," + rect.top + "," + rect.right + "," + rect.bottom + "]";
    }

    private static String formatTextureMatrix(float[] matrix) {
        StringBuilder value = new StringBuilder("[");
        for (int i = 0; i < matrix.length; i++) {
            if (i > 0) value.append(',');
            value.append(matrix[i]);
        }
        return value.append(']').toString();
    }

    private static void log(String message) {
        try {
            Api101Bridge.log(LiquidUiLog.format(TAG + " " + message));
        } catch (Throwable ignored) {
            android.util.Log.i("LiquidUI", "[LUI]" + TAG + " " + message);
        }
    }
}
