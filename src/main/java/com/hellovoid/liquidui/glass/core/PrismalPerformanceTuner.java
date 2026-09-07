package com.hellovoid.liquidui.glass.core;

import android.opengl.GLES20;

import com.hellovoid.prismal.PrismalRenderer;

import java.util.Map;
import java.util.WeakHashMap;

/** Shared Prismal fast path for the refraction-only Window glass profile. */
final class PrismalPerformanceTuner {
    private static final String FAST_COPY_VERTEX =
            "attribute vec2 a_position;\n" +
            "varying vec2 v_texCoord;\n" +
            "void main() {\n" +
            "    gl_Position = vec4(a_position, 0.0, 1.0);\n" +
            "    v_texCoord = (a_position + 1.0) * 0.5;\n" +
            "    v_texCoord.y = 1.0 - v_texCoord.y;\n" +
            "}\n";

    // renderBlurPass() requires u_texelSize and u_sigma to remain active uniforms. The tiny
    // offset keeps both live without producing a visible displacement at Window scale.
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

    private PrismalPerformanceTuner() {}

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
