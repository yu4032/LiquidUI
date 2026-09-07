package com.hellovoid.liquidui.glass.notification;

import android.opengl.GLES20;

import com.hellovoid.prismal.PrismalRenderer;

import java.util.Map;
import java.util.WeakHashMap;

/**
 * Notification-only Prismal fast path.
 *
 * HyperOS already provides the backdrop through its quarter-scale PassBlur producer. Running
 * Prismal's generic 31-tap horizontal + 31-tap vertical Gaussian on every notification frame is
 * redundant for this refraction-only profile, so replace those two programs with one-sample
 * copies while leaving Prismal's optical/refraction shader untouched.
 */
final class NotificationPrismalPerformanceTuner {
    private static final String FAST_COPY_VERTEX =
            "attribute vec2 a_position;\n" +
            "varying vec2 v_texCoord;\n" +
            "void main() {\n" +
            "    gl_Position = vec4(a_position, 0.0, 1.0);\n" +
            "    v_texCoord = (a_position + 1.0) * 0.5;\n" +
            "    v_texCoord.y = 1.0 - v_texCoord.y;\n" +
            "}\n";

    // renderBlurPass() requires u_texelSize and u_sigma to remain active uniforms. The tiny
    // offset keeps both live without producing a visible displacement at notification scale.
    private static final String FAST_COPY_FRAGMENT =
            "precision highp float;\n" +
            "uniform sampler2D u_texture;\n" +
            "uniform vec2 u_texelSize;\n" +
            "uniform float u_sigma;\n" +
            "varying vec2 v_texCoord;\n" +
            "void main() {\n" +
            "    vec2 keepUniforms = u_texelSize * (0.00000001 * max(u_sigma, 1.0));\n" +
            "    gl_FragColor = texture2D(u_texture, clamp(v_texCoord + keepUniforms, 0.0, 1.0));\n" +
            "}\n";

    private static final Map<PrismalRenderer, Boolean> TUNED = new WeakHashMap<>();

    private NotificationPrismalPerformanceTuner() {}

    static synchronized void ensureFastBackdrop(PrismalRenderer renderer) {
        if (renderer == null || TUNED.containsKey(renderer)) return;
        int fastH = 0;
        int fastV = 0;
        try {
            fastH = renderer.createProgram(FAST_COPY_VERTEX, FAST_COPY_FRAGMENT);
            fastV = renderer.createProgram(FAST_COPY_VERTEX, FAST_COPY_FRAGMENT);
            int oldH = renderer.blurHProgram;
            int oldV = renderer.blurVProgram;
            renderer.blurHProgram = fastH;
            renderer.blurVProgram = fastV;
            if (oldH != 0) GLES20.glDeleteProgram(oldH);
            if (oldV != 0) GLES20.glDeleteProgram(oldV);
            TUNED.put(renderer, Boolean.TRUE);
        } catch (Throwable error) {
            if (fastH != 0) GLES20.glDeleteProgram(fastH);
            if (fastV != 0) GLES20.glDeleteProgram(fastV);
            throw error;
        }
    }
}
