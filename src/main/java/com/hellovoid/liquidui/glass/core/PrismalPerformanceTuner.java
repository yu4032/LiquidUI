package com.hellovoid.liquidui.glass.core;

import android.opengl.GLES20;

import com.hellovoid.prismal.PrismalParams;
import com.hellovoid.prismal.PrismalRenderer;

import java.util.Map;
import java.util.Objects;
import java.util.WeakHashMap;

/**
 * Owns the derived Prismal blur program mode for one shared renderer.
 * Zero blur uses an exact fast copy; non-zero blur restores the official Gaussian kernel.
 */
final class PrismalPerformanceTuner {
    private enum Mode { GAUSSIAN, FAST_COPY }

    private static final String BLUR_VERTEX =
            "attribute vec2 a_position;\n" +
            "varying vec2 v_texCoord;\n" +
            "void main() {\n" +
            "    gl_Position = vec4(a_position, 0.0, 1.0);\n" +
            "    v_texCoord = (a_position + 1.0) * 0.5;\n" +
            "    v_texCoord.y = 1.0 - v_texCoord.y;\n" +
            "}\n";

    // renderBlurPass() requires u_texelSize and u_sigma to stay live. The tiny offset keeps the
    // uniforms active while remaining far below one framebuffer sub-pixel.
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

    private static final String GAUSSIAN_FRAGMENT =
            "precision highp float;\n" +
            "uniform sampler2D u_texture;\n" +
            "uniform vec2 u_texelSize;\n" +
            "uniform float u_sigma;\n" +
            "varying vec2 v_texCoord;\n" +
            "void main() {\n" +
            "    float s = max(u_sigma, 0.5);\n" +
            "    float s2 = s * s * 2.0;\n" +
            "    float norm = 0.0;\n" +
            "    vec3 col = vec3(0.0);\n" +
            "    for (float i = -15.0; i <= 15.0; i += 1.0) {\n" +
            "        float w = exp(-i * i / s2);\n" +
            "        vec2 uv = clamp(v_texCoord + vec2(i * u_texelSize.x, 0.0), 0.0, 1.0);\n" +
            "        col += texture2D(u_texture, uv).rgb * w;\n" +
            "        norm += w;\n" +
            "    }\n" +
            "    gl_FragColor = vec4(col / norm, 1.0);\n" +
            "}\n";

    private static final String GAUSSIAN_VERTICAL_FRAGMENT =
            GAUSSIAN_FRAGMENT.replace(
                    "vec2(i * u_texelSize.x, 0.0)",
                    "vec2(0.0, i * u_texelSize.y)");

    private static final Map<PrismalRenderer, Mode> MODES = new WeakHashMap<>();

    private PrismalPerformanceTuner() {}

    static synchronized boolean modeMatches(PrismalRenderer renderer, PrismalParams params) {
        Objects.requireNonNull(renderer, "renderer");
        Objects.requireNonNull(params, "params");
        return currentMode(renderer) == desiredMode(params);
    }

    /** Rebuild the renderer's one derived blur texture for the exact next node profile. */
    static synchronized void prepareNodeBackdrop(PrismalRenderer renderer, PrismalParams params) {
        Objects.requireNonNull(renderer, "renderer");
        Objects.requireNonNull(params, "params");
        Mode desired = desiredMode(params);
        Mode current = currentMode(renderer);
        if (current != desired) {
            replaceBlurPrograms(renderer, desired);
            MODES.put(renderer, desired);
        }
        renderer.renderBlur(params);
    }

    private static Mode desiredMode(PrismalParams params) {
        return params.blurRadiusPx <= 0f ? Mode.FAST_COPY : Mode.GAUSSIAN;
    }

    private static Mode currentMode(PrismalRenderer renderer) {
        Mode current = MODES.get(renderer);
        if (current == null) {
            // PrismalRenderer.ensurePrograms() creates the official Gaussian programs first.
            current = Mode.GAUSSIAN;
            MODES.put(renderer, current);
        }
        return current;
    }

    private static void replaceBlurPrograms(PrismalRenderer renderer, Mode mode) {
        String horizontal = mode == Mode.FAST_COPY ? FAST_COPY_FRAGMENT : GAUSSIAN_FRAGMENT;
        String vertical = mode == Mode.FAST_COPY ? FAST_COPY_FRAGMENT : GAUSSIAN_VERTICAL_FRAGMENT;
        int nextH = 0;
        int nextV = 0;
        try {
            nextH = renderer.createProgram(BLUR_VERTEX, horizontal);
            nextV = renderer.createProgram(BLUR_VERTEX, vertical);
            int oldH = renderer.blurHProgram;
            int oldV = renderer.blurVProgram;
            renderer.blurHProgram = nextH;
            renderer.blurVProgram = nextV;
            if (oldH != 0) GLES20.glDeleteProgram(oldH);
            if (oldV != 0) GLES20.glDeleteProgram(oldV);
        } catch (Throwable error) {
            if (nextH != 0) GLES20.glDeleteProgram(nextH);
            if (nextV != 0) GLES20.glDeleteProgram(nextV);
            throw error;
        }
    }
}
