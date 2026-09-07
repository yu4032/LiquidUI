package com.hellovoid.liquidui.glass.notification;

import android.graphics.SurfaceTexture;
import android.opengl.EGL14;
import android.opengl.EGLConfig;
import android.opengl.EGLContext;
import android.opengl.EGLDisplay;
import android.opengl.EGLSurface;
import android.opengl.GLES11Ext;
import android.opengl.GLES20;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.SystemClock;
import android.util.DisplayMetrics;
import android.view.Surface;
import android.view.View;

import com.hellovoid.liquidui.Api101Bridge;
import com.hellovoid.liquidui.diagnostics.LiquidUiLog;

import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.util.Locale;

/**
 * Feasibility probe for HyperOS's compositor-owned PassBlur stream.
 *
 * One EGL context owns an external-OES texture. Ordinary observe/resume keeps the current
 * SurfaceTexture/Surface producer alive. A real ViewRoot surface destruction is different:
 * SurfaceFlinger disconnects the PassBlur producer together with that root, so the disconnected
 * producer must never be handed to SetPassBlurSurface again. The SurfaceChangedCallback is used
 * only as rollover authority; root destruction retires the old producer and root creation creates
 * a fresh GPU SurfaceTexture/Surface before binding it with an independent transaction. No CPU
 * capture or readback is involved.
 */
final class NotificationGpuPassBlurStreamProbe {
    private static final String TAG = "[NotifGlass][GpuStream]";
    private static final float SOURCE_SCALE = 0.25f;

    private final HandlerThread gpuThread = new HandlerThread("LiquidUI-NotifPassBlurGpu");

    private volatile Handler gpuHandler;
    private volatile SurfaceTexture surfaceTexture;
    private volatile Surface producerSurface;
    private volatile SystemUiPassBlurBridge.Binding binding;
    private volatile boolean initializationPosted;

    private volatile Object observedViewRoot;
    private volatile Object surfaceChangedCallback;
    private volatile Method removeSurfaceChangedCallback;
    private volatile View observedRootHost;
    private volatile long rootSurfaceEpoch;
    private volatile int sourceWidthPx;
    private volatile int sourceHeightPx;

    private EGLDisplay eglDisplay = EGL14.EGL_NO_DISPLAY;
    private EGLContext eglContext = EGL14.EGL_NO_CONTEXT;
    private EGLSurface eglSurface = EGL14.EGL_NO_SURFACE;
    private int externalTextureId;
    private long endpointGeneration;
    private long frameCount;
    private long lastRateLogUptime;
    private long lastRateFrameCount;

    void observe(View target) {
        if (target == null) return;

        View rootHost = rootHost(target);
        if (!rootHost.isAttachedToWindow()) {
            target.post(() -> observe(target));
            return;
        }

        ensureSurfaceRolloverObserver(rootHost);

        if (binding != null && binding.bound && binding.hostRootSurface.isValid()) {
            try {
                Object currentViewRoot = SystemUiPassBlurBridge.getViewRootImpl(rootHost);
                if (currentViewRoot != null
                        && System.identityHashCode(currentViewRoot) == binding.viewRootIdentity) {
                    SystemUiPassBlurBridge.resumeUpdates(binding);
                    return;
                }
            } catch (Throwable ignored) {
                // A new target/root will fall through to normal binding logic.
            }
            SystemUiPassBlurBridge.unbind(binding);
            binding = null;
        }

        Surface readySurface = producerSurface;
        if (readySurface != null && readySurface.isValid()) {
            bindProducer(rootHost, readySurface);
            return;
        }

        if (initializationPosted) return;
        initializationPosted = true;

        int rootWidth = rootHost.getWidth();
        int rootHeight = rootHost.getHeight();
        DisplayMetrics metrics = target.getResources().getDisplayMetrics();
        if (rootWidth <= 0) rootWidth = metrics.widthPixels;
        if (rootHeight <= 0) rootHeight = metrics.heightPixels;
        sourceWidthPx = Math.max(1, rootWidth);
        sourceHeightPx = Math.max(1, rootHeight);

        gpuThread.start();
        Handler handler = new Handler(gpuThread.getLooper());
        gpuHandler = handler;
        handler.post(() -> initializeGpuConsumer(rootHost, sourceWidthPx, sourceHeightPx));
    }

    private void ensureSurfaceRolloverObserver(View rootHost) {
        try {
            Object viewRoot = SystemUiPassBlurBridge.getViewRootImpl(rootHost);
            if (viewRoot == null) return;
            if (viewRoot == observedViewRoot && surfaceChangedCallback != null) return;

            removePreviousSurfaceRolloverObserver();

            Class<?> callbackType = findSurfaceChangedCallbackType(viewRoot.getClass());
            Method addCallback = viewRoot.getClass().getMethod(
                    "addSurfaceChangedCallback", callbackType);
            Method removeCallback = viewRoot.getClass().getMethod(
                    "removeSurfaceChangedCallback", callbackType);

            Object callback = Proxy.newProxyInstance(
                    callbackType.getClassLoader(),
                    new Class<?>[]{callbackType},
                    (proxy, method, args) -> {
                        String name = method.getName();
                        if (method.getDeclaringClass() == Object.class) {
                            if ("toString".equals(name)) return TAG + "-SurfaceChangedCallback";
                            if ("hashCode".equals(name)) return System.identityHashCode(proxy);
                            if ("equals".equals(name)) {
                                return args != null && args.length == 1 && proxy == args[0];
                            }
                            return null;
                        }
                        if ("surfaceCreated".equals(name) || "surfaceReplaced".equals(name)) {
                            recreateGpuProducerForRootRollover(rootHost, viewRoot, name);
                        } else if ("surfaceDestroyed".equals(name)) {
                            onRootSurfaceDestroyed(viewRoot);
                        }
                        return null;
                    });

            addCallback.invoke(viewRoot, callback);
            observedViewRoot = viewRoot;
            surfaceChangedCallback = callback;
            removeSurfaceChangedCallback = removeCallback;
            observedRootHost = rootHost;
            log("surface rollover observer attached viewRoot="
                    + System.identityHashCode(viewRoot));
        } catch (Throwable error) {
            logError("surface rollover observer unavailable", error);
        }
    }

    private void removePreviousSurfaceRolloverObserver() {
        Object previousViewRoot = observedViewRoot;
        Object previousCallback = surfaceChangedCallback;
        Method previousRemove = removeSurfaceChangedCallback;
        observedViewRoot = null;
        surfaceChangedCallback = null;
        removeSurfaceChangedCallback = null;
        observedRootHost = null;
        rootSurfaceEpoch++;
        if (previousViewRoot == null || previousCallback == null || previousRemove == null) return;
        try {
            previousRemove.invoke(previousViewRoot, previousCallback);
        } catch (Throwable error) {
            log("surface rollover observer remove skipped " + error);
        }
    }

    private void onRootSurfaceDestroyed(Object expectedViewRoot) {
        if (expectedViewRoot == null || expectedViewRoot != observedViewRoot) return;
        rootSurfaceEpoch++;
        SystemUiPassBlurBridge.Binding current = binding;
        if (current != null) {
            SystemUiPassBlurBridge.invalidate(current);
            binding = null;
        }
        retireGpuProducerForRootRollover();
        log("root surface destroyed endpointGen="
                + (current == null ? endpointGeneration : current.endpointGeneration)
                + " producerPreserved=false");
    }

    /**
     * SurfaceFlinger disconnects this producer when the owning PassBlur root is destructed. Remove
     * it from shared state immediately so ordinary observe cannot accidentally rebind it, then
     * release the actual BufferQueue objects on the EGL thread.
     */
    private void retireGpuProducerForRootRollover() {
        Surface surface = producerSurface;
        SurfaceTexture texture = surfaceTexture;
        producerSurface = null;
        surfaceTexture = null;

        Handler handler = gpuHandler;
        if (handler == null) {
            if (surface != null) surface.release();
            if (texture != null) texture.release();
            return;
        }
        handler.post(() -> {
            try {
                makeEglCurrent();
            } catch (Throwable ignored) {}
            if (surface != null) surface.release();
            if (texture != null) texture.release();
            log("retired disconnected GPU producer producerPreserved=false");
        });
    }

    private void recreateGpuProducerForRootRollover(
            View rootHost,
            Object expectedViewRoot,
            String event) {
        if (expectedViewRoot == null || expectedViewRoot != observedViewRoot) return;
        long eventEpoch = ++rootSurfaceEpoch;
        Handler handler = gpuHandler;
        if (handler == null || externalTextureId == 0) {
            log("root surface " + event + " before gpu consumer ready");
            return;
        }

        // surfaceReplaced may arrive without a preceding surfaceDestroyed callback. In that case
        // explicitly retire the old producer before creating a new one; reusing it is unsafe.
        if (producerSurface != null || surfaceTexture != null) {
            retireGpuProducerForRootRollover();
        }

        handler.post(() -> {
            if (expectedViewRoot != observedViewRoot || eventEpoch != rootSurfaceEpoch) return;
            try {
                makeEglCurrent();
                Surface surface = createGpuProducerSurface(sourceWidthPx, sourceHeightPx);
                log("recreated GPU producer event=" + event
                        + " epoch=" + eventEpoch
                        + " producerPreserved=false");

                rootHost.post(() -> {
                    if (expectedViewRoot != observedViewRoot || eventEpoch != rootSurfaceEpoch) return;
                    if (surface != producerSurface || !surface.isValid()) return;
                    long beforeGeneration = endpointGeneration;
                    bindProducer(rootHost, surface);
                    SystemUiPassBlurBridge.Binding rebound = binding;
                    if (rebound != null
                            && rebound.bound
                            && rebound.endpointGeneration > beforeGeneration) {
                        log("SF source rebound event=" + event
                                + " scale=" + SOURCE_SCALE
                                + " endpointGen=" + rebound.endpointGeneration
                                + " rootLayer=" + rebound.rootLayerId
                                + " surfaceSeq=" + rebound.surfaceSequenceId
                                + " producerPreserved=false");
                    } else {
                        log("SF source rollover bind deferred event=" + event
                                + " candidateGen=" + (endpointGeneration + 1));
                    }
                });
            } catch (Throwable error) {
                logError("GPU producer recreate failed event=" + event, error);
            }
        });
    }

    private static Class<?> findSurfaceChangedCallbackType(Class<?> viewRootType)
            throws NoSuchMethodException {
        Class<?> current = viewRootType;
        while (current != null) {
            for (Class<?> nested : current.getDeclaredClasses()) {
                if ("SurfaceChangedCallback".equals(nested.getSimpleName())) {
                    return nested;
                }
            }
            current = current.getSuperclass();
        }
        throw new NoSuchMethodException(viewRootType.getName() + "$SurfaceChangedCallback");
    }

    private static View rootHost(View target) {
        View root = target.getRootView();
        return root == null ? target : root;
    }

    private void initializeGpuConsumer(View rootHost, int sourceWidth, int sourceHeight) {
        try {
            initializeEgl();

            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            externalTextureId = textures[0];
            if (externalTextureId == 0) {
                throw new IllegalStateException("external OES texture allocation failed");
            }
            configureExternalTexture();

            Surface surface = createGpuProducerSurface(sourceWidth, sourceHeight);
            log("gpu consumer ready texture=" + externalTextureId
                    + " source=" + sourceWidth + "x" + sourceHeight
                    + " buffer=" + Math.max(1, Math.round(sourceWidth * SOURCE_SCALE))
                    + "x" + Math.max(1, Math.round(sourceHeight * SOURCE_SCALE))
                    + " scale=" + SOURCE_SCALE);

            rootHost.post(() -> bindProducer(rootHost, surface));
        } catch (Throwable error) {
            logError("gpu consumer init failed", error);
        }
    }

    private Surface createGpuProducerSurface(int sourceWidth, int sourceHeight) {
        makeEglCurrent();
        configureExternalTexture();

        SurfaceTexture texture = new SurfaceTexture(externalTextureId);
        int bufferWidth = Math.max(1, Math.round(sourceWidth * SOURCE_SCALE));
        int bufferHeight = Math.max(1, Math.round(sourceHeight * SOURCE_SCALE));
        texture.setDefaultBufferSize(bufferWidth, bufferHeight);
        texture.setOnFrameAvailableListener(this::onFrameAvailable, gpuHandler);
        Surface surface = new Surface(texture);

        surfaceTexture = texture;
        producerSurface = surface;
        return surface;
    }

    private void configureExternalTexture() {
        GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, externalTextureId);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
        GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);
    }

    private void bindProducer(View rootHost, Surface surface) {
        if (rootHost == null || surface == null || !surface.isValid()) return;
        if (surface != producerSurface) {
            log("bind skipped stale GPU producer");
            return;
        }
        if (!rootHost.isAttachedToWindow()) {
            rootHost.post(() -> bindProducer(rootHost, surface));
            return;
        }

        ensureSurfaceRolloverObserver(rootHost);

        SystemUiPassBlurBridge.Binding current = binding;
        if (current != null && current.bound && current.hostRootSurface.isValid()) {
            try {
                Object currentViewRoot = SystemUiPassBlurBridge.getViewRootImpl(rootHost);
                if (currentViewRoot != null
                        && System.identityHashCode(currentViewRoot) == current.viewRootIdentity) {
                    SystemUiPassBlurBridge.resumeUpdates(current);
                    return;
                }
            } catch (Throwable ignored) {}
            SystemUiPassBlurBridge.unbind(current);
            binding = null;
        }

        long nextGeneration = endpointGeneration + 1;
        SystemUiPassBlurBridge.Binding next = SystemUiPassBlurBridge.bind(
                rootHost, surface, nextGeneration);
        if (next != null) {
            binding = next;
            endpointGeneration = next.endpointGeneration;
            log("SF source bound scale=" + SOURCE_SCALE
                    + " endpointGen=" + next.endpointGeneration
                    + " rootLayer=" + next.rootLayerId
                    + " surfaceSeq=" + next.surfaceSequenceId);
        } else {
            log("SF source bind deferred candidateGen=" + nextGeneration);
        }
    }

    private void onFrameAvailable(SurfaceTexture producer) {
        if (producer == null || producer != surfaceTexture) return;
        try {
            makeEglCurrent();
            producer.updateTexImage();
            long timestampNs = producer.getTimestamp();
            long count = ++frameCount;
            long now = SystemClock.uptimeMillis();

            if (count <= 5 || lastRateLogUptime == 0 || now - lastRateLogUptime >= 1000L) {
                long elapsed = lastRateLogUptime == 0 ? 0 : now - lastRateLogUptime;
                long delta = count - lastRateFrameCount;
                float fps = elapsed <= 0 ? 0f : (delta * 1000f) / elapsed;
                log("gpu stream frame count=" + count
                        + " timestampNs=" + timestampNs
                        + " fps=" + String.format(Locale.US, "%.1f", fps)
                        + " texture=" + externalTextureId);
                lastRateLogUptime = now;
                lastRateFrameCount = count;
            }
        } catch (Throwable error) {
            logError("gpu stream frame drain failed", error);
        }
    }

    private void initializeEgl() {
        eglDisplay = EGL14.eglGetDisplay(EGL14.EGL_DEFAULT_DISPLAY);
        if (eglDisplay == EGL14.EGL_NO_DISPLAY) {
            throw new IllegalStateException("eglGetDisplay failed");
        }
        int[] version = new int[2];
        if (!EGL14.eglInitialize(eglDisplay, version, 0, version, 1)) {
            throw new IllegalStateException("eglInitialize failed");
        }

        int[] configAttributes = {
                EGL14.EGL_RENDERABLE_TYPE, EGL14.EGL_OPENGL_ES2_BIT,
                EGL14.EGL_SURFACE_TYPE, EGL14.EGL_PBUFFER_BIT,
                EGL14.EGL_RED_SIZE, 8,
                EGL14.EGL_GREEN_SIZE, 8,
                EGL14.EGL_BLUE_SIZE, 8,
                EGL14.EGL_ALPHA_SIZE, 8,
                EGL14.EGL_NONE
        };
        EGLConfig[] configs = new EGLConfig[1];
        int[] configCount = new int[1];
        if (!EGL14.eglChooseConfig(
                eglDisplay, configAttributes, 0, configs, 0, 1, configCount, 0)
                || configCount[0] <= 0) {
            throw new IllegalStateException("eglChooseConfig failed");
        }

        int[] contextAttributes = {
                EGL14.EGL_CONTEXT_CLIENT_VERSION, 2,
                EGL14.EGL_NONE
        };
        eglContext = EGL14.eglCreateContext(
                eglDisplay, configs[0], EGL14.EGL_NO_CONTEXT, contextAttributes, 0);
        if (eglContext == EGL14.EGL_NO_CONTEXT) {
            throw new IllegalStateException("eglCreateContext failed");
        }

        int[] pbufferAttributes = {
                EGL14.EGL_WIDTH, 1,
                EGL14.EGL_HEIGHT, 1,
                EGL14.EGL_NONE
        };
        eglSurface = EGL14.eglCreatePbufferSurface(
                eglDisplay, configs[0], pbufferAttributes, 0);
        if (eglSurface == EGL14.EGL_NO_SURFACE) {
            throw new IllegalStateException("eglCreatePbufferSurface failed");
        }
        makeEglCurrent();
    }

    private void makeEglCurrent() {
        if (!EGL14.eglMakeCurrent(
                eglDisplay, eglSurface, eglSurface, eglContext)) {
            throw new IllegalStateException("eglMakeCurrent failed");
        }
    }

    private static void log(String message) {
        String formatted = LiquidUiLog.format(TAG + " " + message);
        android.util.Log.i("LiquidUI", formatted);
        try { Api101Bridge.log(formatted); } catch (Throwable ignored) {}
    }

    private static void logError(String message, Throwable error) {
        String formatted = LiquidUiLog.format(TAG + " " + message);
        android.util.Log.e("LiquidUI", formatted, error);
        try { Api101Bridge.log(formatted, error); } catch (Throwable ignored) {}
    }
}
