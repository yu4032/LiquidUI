package com.hellovoid.liquidui.glass.core;

/** Shader sources for adapting the Window PassBlur external-OES producer into the shared 2D domain. */
final class PassBlurShaders {
    static final String QUAD_VERTEX = """
            attribute vec2 aPosition;
            attribute vec2 aUv;
            varying vec2 vUv;
            void main() {
                vUv = aUv;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
            """;

    static final String OES_NORMALIZE_FRAGMENT = """
            #extension GL_OES_EGL_image_external : require
            precision highp float;

            uniform samplerExternalOES uTexture;
            uniform mat4 uWindowUvToOes;
            varying vec2 vUv;

            void main() {
                vec2 textureUv = (uWindowUvToOes * vec4(vUv, 0.0, 1.0)).xy;
                if (any(lessThan(textureUv, vec2(0.0)))
                        || any(greaterThan(textureUv, vec2(1.0)))) {
                    discard;
                }
                vec4 sampled = texture2D(uTexture, textureUv);
                gl_FragColor = vec4(sampled.rgb, 1.0);
            }
            """;

    /** Current upstream Prismal 31-tap Gaussian kernel used for both blur directions. */
    static final String GAUSSIAN_BLUR_FRAGMENT = """
            precision highp float;
            uniform sampler2D uTexture;
            uniform vec2 uTexelSize;
            uniform vec2 uDirection;
            uniform float uSigma;
            varying vec2 vUv;

            void main() {
                float s = max(uSigma, 0.5);
                float s2 = s * s * 2.0;
                float norm = 0.0;
                vec3 col = vec3(0.0);

                for (float i = -15.0; i <= 15.0; i += 1.0) {
                    float w = exp(-i * i / s2);
                    vec2 delta = vec2(
                            i * uTexelSize.x * uDirection.x,
                            i * uTexelSize.y * uDirection.y);
                    vec2 uv = clamp(vUv + delta, 0.0, 1.0);
                    col += texture2D(uTexture, uv).rgb * w;
                    norm += w;
                }

                gl_FragColor = vec4(col / norm, 1.0);
            }
            """;

    /** Final crop from the shared prepared 2D framebuffer into the visible Window host view. */
    static final String COMPOSITE_FRAGMENT = """
            precision highp float;
            uniform sampler2D uTexture;
            uniform vec4 uCropRect;
            varying vec2 vUv;
            void main() {
                vec2 uv = uCropRect.xy + vUv * uCropRect.zw;
                gl_FragColor = texture2D(uTexture, uv);
            }
            """;

    private PassBlurShaders() {}
}
