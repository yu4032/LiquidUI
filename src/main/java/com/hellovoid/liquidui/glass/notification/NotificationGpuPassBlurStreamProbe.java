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

import java.util.Locale;

/**
 * Feasibility probe for HyperOS's compositor-owned PassBlur stream.
 *
 * A single process-lifetime EGL consumer owns one external-OES SurfaceTexture. Its producer Surface
 * is handed to SurfaceFlinger through the exact hidden SetPassBlurSurface contract already used by
 * HyperOS. Ordinary observe/resume never recreates the BufferQueue; only a changed ViewRoot causes
 * the same producer to be rebound. Frame callbacks are drained with updateTexImage on the EGL
 * thread so an increasing counter proves a live GPU stream before any optical renderer is enabled.
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

        if (!target.isAttachedToWindow()) {
            target.post(() -> observe(target));
            return;
        }

        if (binding != null && binding.bound && binding.hostRootSurface.isValid()) {
            try {
                Object currentViewRoot = SystemUiPassBlurBridge.getViewRootImpl(target);
                if (currentViewRoot != null
                        && System.identityHashCode(currentViewRoot) == binding.viewRootIdentity) {
                    SystemUiPassBlurBridge.resumeUpdates(binding);
                    return;
                }
            } catch (Throwable ignored) {
                // A new target/root will fall through to rebind the same long-lived producer.
            }
            SystemUiPassBlurBridge.unbind(binding);
            binding = null;
        }

        Surface readySurface = producerSurface;
        if (readySurface != null && readySurface.isValid()) {
            bindProducer(target, readySurface);
            return;
        }

        if (initializationPosted) return;
        initializationPosted = true;

        int rootWidth = target.getRootView() == null ? 0 : target.getRootView().getWidth();
        int rootHeight = target.getRootView() == null ? 0 : target.getRootView().getHeight();
        DisplayMetrics metrics = target.getResources().getDisplayMetrics();
        if (rootWidth <= 0) rootWidth = metrics.widthPixels;
        if (rootHeight <= 0) rootHeight = metrics.heightPixels;
        final int sourceWidth = Math.max(1, rootWidth);
        final int sourceHeight = Math.max(1, rootHeight);

        gpuThread.start();
        Handler handler = new Handler(gpuThread.getLooper());
        gpuHandler = handler;
        handler.post(() -> initializeGpuConsumer(target, sourceWidth, sourceHeight));
    }

    private void initializeGpuConsumer(View target, int sourceWidth, int sourceHeight) {
        try {
            initializeEgl();

            int[] textures = new int[1];
            GLES20.glGenTextures(1, textures, 0);
            externalTextureId = textures[0];
            if (externalTextureId == 0) {
                throw new IllegalStateException("external OES texture allocation failed");
            }
            GLES20.glBindTexture(GLES11Ext.GL_TEXTURE_EXTERNAL_OES, externalTextureId);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_MIN_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_MAG_FILTER, GLES20.GL_LINEAR);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_WRAP_S, GLES20.GL_CLAMP_TO_EDGE);
            GLES20.glTexParameteri(GLES11Ext.GL_TEXTURE_EXTERNAL_OES,
                    GLES20.GL_TEXTURE_WRAP_T, GLES20.GL_CLAMP_TO_EDGE);

            SurfaceTexture texture = new SurfaceTexture(externalTextureId);
            int bufferWidth = Math.max(1, Math.round(sourceWidth * SOURCE_SCALE));
            int bufferHeight = Math.max(1, Math.round(sourceHeight * SOURCE_SCALE));
            texture.setDefaultBufferSize(bufferWidth, bufferHeight);
            texture.setOnFrameAvailableListener(this::onFrameAvailable, gpuHandler);
            Surface surface = new Surface(texture);

            surfaceTexture = texture;
            producerSurface = surface;
            log("gpu consumer ready texture=" + externalTextureId
                    + " source=" + sourceWidth + "x" + sourceHeight
                    + " buffer=" + bufferWidth + "x" + bufferHeight
                    + " scale=" + SOURCE_SCALE);

            target.post(() -> bindProducer(target, surface));
        } catch (Throwable error) {
            logError("gpu consumer init failed", error);
        }
    }

    private void bindProducer(View target, Surface surface) {
        if (target == null || surface == null || !surface.isValid()) return;
        if (!target.isAttachedToWindow()) {
            target.post(() -> bindProducer(target, surface));
            return;
        }

        SystemUiPassBlurBridge.Binding current = binding;
        if (current != null && current.bound && current.hostRootSurface.isValid()) {
            try {
                Object currentViewRoot = SystemUiPassBlurBridge.getViewRootImpl(target);
                if (currentViewRoot != null
                        && System.identityHashCode(currentViewRoot) == current.viewRootIdentity) {
                    SystemUiPassBlurBridge.resumeUpdates(current);
                    return;
                }
            } catch (Throwable ignored) {}
            SystemUiPassBlurBridge.unbind(current);
        }

        SystemUiPassBlurBridge.Binding next = SystemUiPassBlurBridge.bind(
                target, surface, ++endpointGeneration);
        binding = next;
        if (next != null) {
            log("SF source bound scale=" + SOURCE_SCALE
                    + " endpointGen=" + next.endpointGeneration
                    + " rootLayer=" + next.rootLayerId
                    + " surfaceSeq=" + next.surfaceSequenceId);
        } else {
            log("SF source bind deferred endpointGen=" + endpointGeneration);
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
