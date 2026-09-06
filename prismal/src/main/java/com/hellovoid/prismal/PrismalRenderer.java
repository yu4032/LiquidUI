package com.hellovoid.prismal;

import android.opengl.GLES20;
import java.nio.Buffer;
import java.nio.ByteBuffer;
import java.nio.ByteOrder;
import java.nio.FloatBuffer;
import java.util.HashMap;
import java.util.Map;

/* JADX INFO: loaded from: classes.dex */
public final class PrismalRenderer implements AutoCloseable {
    public boolean backdropPrepared;
    public int blurFramebufferH;
    public int blurFramebufferV;
    public int blurHProgram;
    public int blurHeight;
    public int blurTextureH;
    public int blurTextureV;
    public int blurVProgram;
    public int blurWidth;
    public int glassDrawCount;
    public boolean glassFrameBegun;
    public int glassProgram;
    public int height;
    public boolean legacySingleDraw;
    public int outputFramebuffer;
    public int outputTexture;
    public int sourceFramebuffer;
    public int sourceProgram;
    public int sourceTexture;
    public int width;
    public static final float[] FULL_QUAD = {-1.0f, -1.0f, 0.0f, 0.0f, 1.0f, -1.0f, 1.0f, 0.0f, -1.0f, 1.0f, 0.0f, 1.0f, 1.0f, 1.0f, 1.0f, 1.0f};
    public static final float[] GLASS_QUAD = {-0.5f, -0.5f, 0.5f, -0.5f, -0.5f, 0.5f, -0.5f, 0.5f, 0.5f, -0.5f, 0.5f, 0.5f};
    public static final float[] BLUR_QUAD = {-1.0f, -1.0f, 1.0f, -1.0f, -1.0f, 1.0f, -1.0f, 1.0f, 1.0f, -1.0f, 1.0f, 1.0f};
    public final Map glassUniformLocations = new HashMap();
    public final FloatBuffer fullQuad = floatBuffer(FULL_QUAD);
    public final FloatBuffer glassQuad = floatBuffer(GLASS_QUAD);
    public final FloatBuffer blurQuad = floatBuffer(BLUR_QUAD);

    public void prepareBackdrop(int backgroundTexture2D, int framebufferWidth, int framebufferHeight, PrismalParams params) {
        if (backgroundTexture2D <= 0) {
            throw new IllegalArgumentException("background texture <= 0");
        }
        if (framebufferWidth <= 0 || framebufferHeight <= 0) {
            throw new IllegalArgumentException("framebuffer dimensions <= 0");
        }
        if (params == null) {
            params = PrismalParams.builder().build();
        }
        ensurePrograms();
        ensureTargets(framebufferWidth, framebufferHeight);
        renderSourceAdapter(backgroundTexture2D);
        renderBlur(params);
        this.backdropPrepared = true;
        this.glassFrameBegun = false;
        this.glassDrawCount = 0;
    }

    public void beginGlassFrame() {
        if (!this.backdropPrepared) {
            throw new IllegalStateException("prepareBackdrop must be called before beginGlassFrame");
        }
        GLES20.glBindFramebuffer(36160, this.outputFramebuffer);
        GLES20.glViewport(0, 0, this.width, this.height);
        GLES20.glDisable(3042);
        GLES20.glDisable(3089);
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(16384);
        this.glassFrameBegun = true;
        this.glassDrawCount = 0;
    }

    public void drawGlass(PrismalGeometry geometry, PrismalParams params) {
        drawGlass(geometry, params, PrismalHighlightProfile.ALL_ENABLED);
    }

    public void drawGlass(PrismalGeometry geometry, PrismalParams params, PrismalHighlightProfile highlightProfile) {
        drawGlass(geometry, params, highlightProfile, (PrismalInteractionState) null);
    }

    public void drawGlass(PrismalGeometry geometry, PrismalParams params, PrismalHighlightProfile highlightProfile, PrismalInteractionState interactionState) {
        drawGlass(geometry, params, highlightProfile, interactionState, 1.0f);
    }

    public void drawGlass(PrismalGeometry geometry, PrismalParams params, PrismalHighlightProfile highlightProfile, PrismalInteractionState interactionState, float opacity) {
        if (geometry == null) {
            throw new IllegalArgumentException("geometry == null");
        }
        if (!this.glassFrameBegun) {
            throw new IllegalStateException("beginGlassFrame must be called before drawGlass");
        }
        if (geometry.framebufferWidth != this.width || geometry.framebufferHeight != this.height) {
            throw new IllegalArgumentException("geometry framebuffer does not match prepared backdrop");
        }
        if (params == null) {
            params = PrismalParams.builder().build();
        }
        PrismalParams params2 = params;
        if (highlightProfile == null) {
            highlightProfile = PrismalHighlightProfile.ALL_ENABLED;
        }
        PrismalHighlightProfile highlightProfile2 = highlightProfile;
        float safeOpacity = Float.isFinite(opacity) ? Math.max(0.0f, Math.min(1.0f, opacity)) : 1.0f;
        renderGlassNode(geometry, params2, highlightProfile2, interactionState, !this.legacySingleDraw || this.glassDrawCount > 0, safeOpacity);
        this.glassDrawCount++;
    }

    public void drawGlass(PrismalGeometry geometry, PrismalParams params, PrismalHighlightProfile highlightProfile, float opacity) {
        if (geometry == null) {
            throw new IllegalArgumentException("geometry == null");
        }
        if (!this.glassFrameBegun) {
            throw new IllegalStateException("beginGlassFrame must be called before drawGlass");
        }
        if (geometry.framebufferWidth != this.width || geometry.framebufferHeight != this.height) {
            throw new IllegalArgumentException("geometry framebuffer does not match prepared backdrop");
        }
        if (params == null) {
            params = PrismalParams.builder().build();
        }
        PrismalParams params2 = params;
        if (highlightProfile == null) {
            highlightProfile = PrismalHighlightProfile.ALL_ENABLED;
        }
        PrismalHighlightProfile highlightProfile2 = highlightProfile;
        float safeOpacity = Float.isFinite(opacity) ? Math.max(0.0f, Math.min(1.0f, opacity)) : 1.0f;
        renderGlassNode(geometry, params2, highlightProfile2, null, !this.legacySingleDraw || this.glassDrawCount > 0, safeOpacity);
        this.glassDrawCount++;
    }

    public int outputTexture() {
        return this.outputTexture;
    }

    public final void ensurePrograms() {
        if (this.sourceProgram != 0 && this.blurHProgram != 0 && this.blurVProgram != 0 && this.glassProgram != 0) {
            return;
        }
        this.sourceProgram = createProgram("attribute vec2 aPosition;\nattribute vec2 aUv;\nvarying vec2 vUv;\nvoid main() {\n    gl_Position = vec4(aPosition, 0.0, 1.0);\n    vUv = aUv;\n}\n", "precision highp float;\nuniform sampler2D uTexture;\nvarying vec2 vUv;\nvoid main() {\n    gl_FragColor = texture2D(uTexture, vec2(vUv.x, 1.0 - vUv.y));\n}\n");
        this.blurHProgram = createProgram("attribute vec2 a_position;\nvarying vec2 v_texCoord;\nvoid main() {\n    gl_Position = vec4(a_position, 0.0, 1.0);\n    v_texCoord = (a_position + 1.0) * 0.5;\n    v_texCoord.y = 1.0 - v_texCoord.y;\n}\n", "precision highp float;\nuniform sampler2D u_texture;\nuniform vec2 u_texelSize;\nuniform float u_sigma;\nvarying vec2 v_texCoord;\n\nvoid main() {\n    float s = max(u_sigma, 0.5);\n    float s2 = s * s * 2.0;\n    float norm = 0.0;\n    vec3 col = vec3(0.0);\n\n    // 31-tap Gaussian kernel, handles sigma up to ~5 cleanly\n    for (float i = -15.0; i <= 15.0; i += 1.0) {\n        float w = exp(-i * i / s2);\n        vec2 uv = clamp(v_texCoord + vec2(i * u_texelSize.x, 0.0), 0.0, 1.0);\n        col += texture2D(u_texture, uv).rgb * w;\n        norm += w;\n    }\n\n    gl_FragColor = vec4(col / norm, 1.0);\n}\n");
        this.blurVProgram = createProgram("attribute vec2 a_position;\nvarying vec2 v_texCoord;\nvoid main() {\n    gl_Position = vec4(a_position, 0.0, 1.0);\n    v_texCoord = (a_position + 1.0) * 0.5;\n    v_texCoord.y = 1.0 - v_texCoord.y;\n}\n", "precision highp float;\nuniform sampler2D u_texture;\nuniform vec2 u_texelSize;\nuniform float u_sigma;\nvarying vec2 v_texCoord;\n\nvoid main() {\n    float s = max(u_sigma, 0.5);\n    float s2 = s * s * 2.0;\n    float norm = 0.0;\n    vec3 col = vec3(0.0);\n\n    // 31-tap Gaussian kernel, handles sigma up to ~5 cleanly\n    for (float i = -15.0; i <= 15.0; i += 1.0) {\n        float w = exp(-i * i / s2);\n        vec2 uv = clamp(v_texCoord + vec2(0.0, i * u_texelSize.y), 0.0, 1.0);\n        col += texture2D(u_texture, uv).rgb * w;\n        norm += w;\n    }\n\n    gl_FragColor = vec4(col / norm, 1.0);\n}\n");
        String glassFragment = PrismalComponentGateShader.apply(PrismalOpticalEdgeShader.apply(PrismalSingleEdgeShader.apply("// ═══════════════════════════════════════════════════════════════════════════\n// Prismal - Liquid Glass Fragment Shader\n// lens + droplet height + dual-specular + Fresnel transmission/reflection\n//\n// Author: Saurav Sajeev\n// ═══════════════════════════════════════════════════════════════════════════\n\nprecision highp float;\n\nuniform sampler2D u_backgroundTexture;\nuniform sampler2D u_blurredTexture;\nuniform int       u_useBlurredTexture;\n\nuniform vec2  u_resolution;\nuniform vec2  u_glassSize;\nuniform vec4  u_cornerRadii;\nuniform float u_refractionInset;\nuniform float u_sminSmoothing;\nuniform float u_edgeRefractionFalloff;\n\nuniform float u_ior;\nuniform float u_glassThickness;\nuniform float u_normalStrength;\nuniform float u_displacementScale;\nuniform float u_heightTransitionWidth;\n\nuniform float u_lensRefractionPx;\nuniform float u_lensDepthEffect;\n\nuniform float u_chromaticAberration;\nuniform float u_dispersionR;\nuniform float u_dispersionB;\n\nuniform float u_vibrancy;\nuniform float u_plainHighlight;\n\nuniform float u_liquidDome;\nuniform float u_fresnelReflect;\n\nuniform float u_brightness;\nuniform vec4  u_glassColor;\nuniform float u_highlightWidth;\n\nuniform vec2  u_lightDir;\nuniform float u_specular;\nuniform float u_shininess;\nuniform float u_rimStrength;\n\nuniform vec4  u_shadowColor;\nuniform float u_shadowSoftness;\n\nuniform float u_causticIntensity;\nuniform float u_transmittance;\n\nuniform vec2  u_backdropSampleScale;\nuniform float u_parallaxScale;\n\nuniform float u_pressProgress;\nuniform float u_backdropPinch;\nuniform vec2  u_glowCenter;\nuniform float u_glowStrength;\n\nuniform int   u_showNormals;\n\nvarying vec2 v_screenTexCoord;\nvarying vec2 v_shapeCoord;\n\nfloat radiusAtCentered(vec2 c, vec4 radii) {\n    if (c.x >= 0.0) {\n        if (c.y <= 0.0) return radii.y;\n        else return radii.z;\n    } else {\n        if (c.y <= 0.0) return radii.x;\n        else return radii.w;\n    }\n}\n\nfloat sdRoundedRectRealistic(vec2 coord, vec2 halfSize, float radius) {\n    vec2 cornerCoord = abs(coord) - (halfSize - vec2(radius));\n    float outside = length(max(cornerCoord, 0.0)) - radius;\n    float inside = min(max(cornerCoord.x, cornerCoord.y), 0.0);\n    return outside + inside;\n}\n\nvec2 gradSdRoundedRectRealistic(vec2 coord, vec2 halfSize, float radius) {\n    vec2 cornerCoord = abs(coord) - (halfSize - vec2(radius));\n    if (cornerCoord.x >= 0.0 || cornerCoord.y >= 0.0) {\n        vec2 m = max(cornerCoord, 0.0);\n        float len = length(m);\n        if (len < 1e-5) return vec2(0.0);\n        return sign(coord) * (m / len);\n    } else {\n        float gradX = step(cornerCoord.y, cornerCoord.x);\n        return sign(coord) * vec2(gradX, 1.0 - gradX);\n    }\n}\n\nfloat circleMapRealistic(float x) {\n    x = clamp(x, 0.0, 1.0);\n    return 1.0 - sqrt(max(0.0, 1.0 - x * x));\n}\n\nfloat smin_poly(float a, float b, float k) {\n    if (k <= 0.0) return min(a, b);\n    float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);\n    return mix(b, a, h) - k * h * (1.0 - h);\n}\n\nfloat smax_poly(float a, float b, float k) {\n    if (k <= 0.0) return max(a, b);\n    float h = clamp(0.5 + 0.5 * (a - b) / k, 0.0, 1.0);\n    return mix(b, a, h) + k * h * (1.0 - h);\n}\n\nfloat sdRoundBox(vec2 p, vec2 b, float r, float k) {\n    if (k <= 0.0) {\n        vec2 q = abs(p) - b + r;\n        return min(max(q.x, q.y), 0.0) + length(max(q, 0.0)) - r;\n    }\n    vec2 q = abs(p) - b + r;\n    float a = smax_poly(q.x, q.y, k);\n    float c = smin_poly(a, 0.0, k * 0.5);\n    vec2  ql = vec2(smax_poly(q.x, 0.0, k), smax_poly(q.y, 0.0, k));\n    return c + length(ql) - r;\n}\n\nfloat getHeightFromDist(float dist, float tw) {\n    float t = clamp(-dist / tw, 0.0, 1.0);\n    return sqrt(max(0.0, 2.0 * t - t * t));\n}\n\nvec2 computeGradientHeight(vec2 pPx, vec2 halfSz, float cr, float k, float tw) {\n    vec2 s = vec2(1.0, 1.0);\n    float hpx = getHeightFromDist(sdRoundBox(pPx + vec2(s.x, 0.0), halfSz, cr, k), tw);\n    float hnx = getHeightFromDist(sdRoundBox(pPx - vec2(s.x, 0.0), halfSz, cr, k), tw);\n    float hpy = getHeightFromDist(sdRoundBox(pPx + vec2(0.0, s.y), halfSz, cr, k), tw);\n    float hny = getHeightFromDist(sdRoundBox(pPx - vec2(0.0, s.y), halfSz, cr, k), tw);\n    return vec2((hpx - hnx) * 0.5, (hpy - hny) * 0.5);\n}\n\nvec3 applyVibrancy(vec3 rgb, float sat) {\n    if (sat <= 1.001) return rgb;\n    float L = dot(rgb, vec3(0.213, 0.715, 0.072));\n    return clamp(mix(vec3(L), rgb, sat), 0.0, 1.0);\n}\n\nvec2 backdropUv(vec2 screenUv, vec2 offset, float pinchMix) {\n    float press = clamp(u_pressProgress, 0.0, 1.0);\n    float pinch = mix(1.0, max(u_backdropPinch, 0.01), press * pinchMix);\n    vec2 s = max(u_backdropSampleScale, vec2(0.01)) / vec2(pinch);\n    vec2 scaled = (screenUv - 0.5) / s + 0.5;\n    return clamp(scaled + offset, vec2(0.0), vec2(1.0));\n}\n\nvoid main() {\n    vec2 halfSz = u_glassSize * 0.5;\n    float minDim = min(halfSz.x, halfSz.y);\n    float pxNorm = clamp(minDim / 108.0, 0.36, 1.0) + smoothstep(88.0, 220.0, minDim) * 0.45;\n    float edgePunch = mix(1.0, 1.12, smoothstep(74.0, 200.0, minDim));\n    float smallGlass = smoothstep(128.0, 46.0, minDim * 2.0);\n    edgePunch = mix(edgePunch, 1.0, smallGlass * 0.85);\n    float rimScale = 1.0;\n    float crMask = min(min(u_cornerRadii.x, u_cornerRadii.y), min(u_cornerRadii.z, u_cornerRadii.w));\n    crMask = min(crMask, min(u_glassSize.x, u_glassSize.y) * 0.5);\n\n    vec2 pPx = v_shapeCoord * u_glassSize;\n    vec2 cKy = vec2(pPx.x, -pPx.y);\n\n    float crMax = min(halfSz.x, halfSz.y);\n    float radCorner = min(radiusAtCentered(cKy, u_cornerRadii), crMax);\n    float sdKy = sdRoundedRectRealistic(cKy, halfSz, radCorner);\n\n    float distMask = sdRoundBox(pPx, halfSz, crMask, u_sminSmoothing);\n    float edgeDist = -distMask;\n    float reflShell = smoothstep(clamp(minDim * 0.09, 1.8, 18.0), 0.0, edgeDist) * smoothstep(-3.0, 0.0, distMask);\n    reflShell *= mix(0.78, 0.42, smallGlass);\n    float inset = min(max(u_refractionInset, 0.8), max(minDim * 0.06, 1.6));\n    inset = mix(inset, min(inset, minDim * 0.04), smallGlass);\n    float opacity = 1.0 - smoothstep(-inset * 0.5, 0.0, distMask);\n    opacity = mix(opacity, 1.0, smoothstep(0.0, 0.55, edgeDist));\n    if (opacity < 0.001) discard;\n\n    float dome = clamp(u_liquidDome, 0.0, 2.0);\n    float refractionHeight = max(u_heightTransitionWidth * (1.0 + 0.55 * dome), 1.0);\n    refractionHeight = min(refractionHeight, minDim * 0.98);\n\n    float tw = max(u_heightTransitionWidth * (1.0 + 0.38 * dome), 1.0);\n    tw = min(tw, minDim * 0.98);\n    float hSig = getHeightFromDist(distMask, tw);\n    vec2 gradHSig = computeGradientHeight(pPx, halfSz, crMask, u_sminSmoothing, tw);\n\n    float gradRadius = min(radCorner * 1.5, min(halfSz.x, halfSz.y));\n    vec2 gradLens = gradSdRoundedRectRealistic(cKy, halfSz, gradRadius);\n\n    float innerReach = max(min(halfSz.x, halfSz.y) - crMask * 0.42, minDim * 0.22);\n    innerReach += refractionHeight * (1.0 + 0.25 * dome);\n    innerReach = min(innerReach, max(halfSz.x, halfSz.y) * 0.95);\n    float tDeep = clamp(edgeDist / max(innerReach, 2.0), 0.0, 1.0);\n    float tShell = 1.0 - tDeep;\n\n    float meniscusBand = smoothstep(0.0, 0.12, tShell);\n    float hCap = pow(tShell, 0.38);\n    float edgeBulge = 0.10 * pow(tShell, 2.8);\n    float hDome = (hCap + edgeBulge) * meniscusBand;\n\n    float coreBlend = smoothstep(0.0, 0.38, tDeep);\n    float hSlab = mix(hSig * (0.58 + 0.42 * coreBlend), hSig, 0.4 + 0.6 * (1.0 - dome));\n\n    float domeW = dome * (0.74 + 0.26 * smoothstep(0.12, 0.94, tShell));\n    float height = mix(hSlab, hDome, domeW);\n    float edgeRound = 1.0 - smoothstep(0.72, 1.0, tShell);\n    height = clamp(height * (0.84 + 0.16 * meniscusBand + 0.08 * edgeRound), 0.0, 1.0);\n\n    vec2 outward = (length(gradLens) > 1e-4) ? normalize(gradLens) : vec2(0.0, 1.0);\n    float shellCurv = smoothstep(0.0, 1.0, tShell);\n    vec2 gCap = outward * (-shellCurv * (0.38 / max(minDim, 8.0)));\n    gCap *= meniscusBand * edgeRound;\n    vec2 gradH = mix(gradHSig, gCap, domeW);\n\n    vec3 N = normalize(vec3(-gradH.x * u_normalStrength, -gradH.y * u_normalStrength, 1.0));\n\n    float menW = clamp(edgeDist / tw, 0.0, 1.0);\n    float menCirc = sqrt(max(0.0, 1.0 - menW * menW));\n    vec3 N_meniscus = normalize(vec3(-outward * menCirc * 0.95, 0.26 + 0.74 * menW));\n    float menBlend = smoothstep(tw * 0.42, 0.0, edgeDist) * smoothstep(-4.0, 0.0, distMask) * 0.62;\n    menBlend *= mix(1.0, 0.15, smallGlass);\n    N = normalize(mix(N, N_meniscus, menBlend));\n\n    float dropLens = pow(smoothstep(refractionHeight, 0.0, edgeDist), 0.82);\n\n    if (u_showNormals == 1) {\n        gl_FragColor = vec4(N * 0.5 + 0.5, opacity);\n        return;\n    }\n\n    vec3 V = vec3(0.0, 0.0, 1.0);\n    float cosVN = clamp(dot(N, V), 0.0, 1.0);\n    float r0 = pow((1.0 - u_ior) / (1.0 + u_ior), 2.0);\n    float silW = clamp(minDim * 0.12, 2.5, 34.0);\n    float edgeSil = smoothstep(silW, 0.0, edgeDist) * smoothstep(-4.5, 0.0, distMask);\n    float tiltW = clamp(length(N.xy) * 2.4, 0.0, 1.0);\n    float grazingW = clamp(edgeSil * 0.94 + tiltW * 0.55, 0.0, 1.0);\n    float cosVNeff = mix(cosVN, max(0.04, cosVN * 0.22 + 0.07 * tiltW), grazingW);\n    float F = r0 + (1.0 - r0) * pow(1.0 - cosVNeff, 5.0);\n    float fresCtl = clamp(u_fresnelReflect, 0.0, 5.0);\n    float Fr = fresCtl;\n    float cosVNrim = cosVNeff;\n\n    vec2 cenSafe = cKy + vec2(1e-4, 1e-4);\n    vec2 lensDir = gradLens + u_lensDepthEffect * normalize(cenSafe);\n    float ldLen = length(lensDir);\n    lensDir = ldLen > 1e-5 ? lensDir / ldLen : vec2(0.0);\n\n    float lensRh = refractionHeight;\n    float sdIn = min(sdKy, 0.0);\n    float dLens = 0.0;\n    if ((-sdKy) < lensRh) {\n        dLens = circleMapRealistic(1.0 - (-sdIn / lensRh)) * (-u_lensRefractionPx);\n        dLens *= (1.0 + clamp(u_pressProgress, 0.0, 1.0) * 0.45);\n    }\n\n    vec2 lensDeltaUv = (dLens * lensDir) / u_resolution;\n    float parallaxK = 0.052 * u_displacementScale;\n    vec2 parallax = (gradLens * height * (7.0 + 22.0 * F)) / u_resolution * parallaxK * u_parallaxScale;\n    lensDeltaUv += parallax;\n    lensDeltaUv *= mix(0.78, 1.12, (1.0 - F) * (0.42 + 0.58 * height));\n\n    float refrStr = height * (0.5 + F * 0.35);\n    vec3 refIn = refract(-V, N, 1.0 / u_ior);\n    vec3 refOut = (dot(refIn, refIn) < 0.001) ? vec3(0.0) : refract(refIn, -N, u_ior);\n    vec2 snellOff = (refOut.xy * u_glassThickness * refrStr / u_resolution) * u_displacementScale;\n    snellOff *= mix(0.72, 1.18, (1.0 - F) * (0.5 + 0.5 * height));\n\n    vec2 bDir = length(pPx) > 1e-3 ? -normalize(pPx) : vec2(0.0, -1.0);\n    float bulge = smoothstep(0.05, 0.38, tDeep) * (1.0 - smoothstep(0.52, 0.94, tDeep));\n    bulge = pow(max(bulge, 0.0), 0.62) * height * (0.014 + 0.01 * dome);\n    bulge *= smoothstep(0.02, 0.36, tDeep) * dropLens;\n    vec2 bulgeUv = bDir * bulge * u_glassSize / u_resolution;\n    snellOff *= pxNorm * dropLens;\n    bulgeUv *= pxNorm;\n\n    vec2 baseOffset = lensDeltaUv + snellOff + bulgeUv;\n    float pinchMix = 1.0 - smoothstep(0.0, 0.72, tDeep);\n    vec2 uvCenter = backdropUv(v_screenTexCoord, baseOffset, pinchMix);\n    float avgDim = (u_glassSize.x + u_glassSize.y) * 0.5;\n\n    float caAmt = max(u_chromaticAberration, 0.0);\n    vec3 color;\n\n    if (caAmt < 0.02) {\n        if (u_useBlurredTexture == 1) {\n            color = texture2D(u_blurredTexture, uvCenter).rgb;\n        } else {\n            color = texture2D(u_backgroundTexture, uvCenter).rgb;\n        }\n    } else {\n        float chromaFar = avgDim * 0.5;\n        float edgeFac = pow(smoothstep(chromaFar, 0.0, edgeDist), 1.8);\n        float chromaBase = caAmt * 0.0018 * edgeFac;\n\n        vec2 dispDir = length(pPx) > 1e-3 ? normalize(pPx) : vec2(0.0, 1.0);\n        vec2 chromaPush = dispDir * chromaBase * pxNorm;\n        vec2 uvR = backdropUv(v_screenTexCoord, baseOffset + chromaPush * u_dispersionR, pinchMix);\n        vec2 uvG = uvCenter;\n        vec2 uvB = backdropUv(v_screenTexCoord, baseOffset - chromaPush * u_dispersionB, pinchMix);\n\n        if (u_useBlurredTexture == 1) {\n            float r = texture2D(u_blurredTexture, uvR).r;\n            float g = texture2D(u_blurredTexture, uvG).g;\n            float b = texture2D(u_blurredTexture, uvB).b;\n            color = vec3(r, g, b);\n        } else {\n            float r = texture2D(u_backgroundTexture, uvR).r;\n            float g = texture2D(u_backgroundTexture, uvG).g;\n            float b = texture2D(u_backgroundTexture, uvB).b;\n            color = vec3(r, g, b);\n        }\n    }\n\n    color = applyVibrancy(color, u_vibrancy);\n\n    vec2 gDir = normalize(gradLens + vec2(1e-4));\n    float edgeG = reflShell * pow(1.0 - cosVNrim, 1.35) * mix(0.07, 0.62, F);\n    edgeG *= mix(0.72, 0.38, smallGlass);\n    float reflW = min(0.9, edgeG * (0.1 + fresCtl * 0.46) * (0.28 + 0.72 * height));\n    vec2 reflUv = clamp(\n        v_screenTexCoord + baseOffset\n            + gDir * (4.0 + 38.0 * pow(1.0 - cosVNrim, 1.25) + length(N.xy) * 14.0) / u_resolution * pxNorm,\n        vec2(0.0),\n        vec2(1.0)\n    );\n    vec3 reflSample;\n    if (u_useBlurredTexture == 1) {\n        reflSample = texture2D(u_blurredTexture, reflUv).rgb;\n    } else {\n        reflSample = texture2D(u_backgroundTexture, reflUv).rgb;\n    }\n    color = mix(color, reflSample, reflW);\n\n    vec3 skyHaze = vec3(0.88, 0.93, 1.02);\n    float skyW = min(0.62, edgeG * pow(1.0 - cosVNrim, 1.2) * (0.04 + fresCtl * 0.28) * (0.28 + 0.72 * height));\n    skyW *= mix(0.68, 0.32, smallGlass);\n    color = mix(color, mix(color, skyHaze, 0.55 + 0.1 * fresCtl), skyW);\n\n    color *= u_brightness;\n    color = mix(color, color * u_glassColor.rgb, u_glassColor.a);\n\n    vec3 Lp = normalize(vec3(u_lightDir, 1.45));\n    vec3 Ls = normalize(vec3(-u_lightDir.x * 0.62 + 0.41, -u_lightDir.y * 0.62 + 0.33, 0.74));\n    vec3 Hp = normalize(Lp + V);\n    vec3 Hs = normalize(Ls + V);\n\n    float shadowExt = mix(0.15, 0.60, u_shadowSoftness > 1.0\n        ? clamp(u_shadowSoftness / 20.0, 0.0, 1.0)\n        : clamp(u_shadowSoftness, 0.0, 1.0));\n    float shadowFalloff = avgDim * shadowExt;\n    float innerShadow = 1.0 - smoothstep(0.0, shadowFalloff, edgeDist);\n    innerShadow = pow(innerShadow, 2.35) * 0.62 * (0.22 + height * 0.68);\n    innerShadow *= 1.0 - smoothstep(0.0, clamp(minDim * 0.05, 0.6, 3.5), edgeDist) * 0.55;\n    color = mix(color, u_shadowColor.rgb * 0.25, innerShadow * u_shadowColor.a);\n\n    float sh = max(u_shininess, 1.0);\n    float sp = u_specular * 1.05;\n    float specP = pow(max(dot(N, Hp), 0.0), sh) * sp;\n    specP *= (0.32 + 0.68 * height);\n    float specS = pow(max(dot(N, Hs), 0.0), sh * 0.68) * sp * 0.48;\n    specS *= (0.24 + 0.76 * height) * (0.42 + 0.58 * F);\n    color += (specP + specS) * vec3(0.99, 0.993, 1.0);\n\n    vec3 Vn = normalize(V);\n    float dotNV = clamp(dot(N, Vn), 0.0, 1.0);\n    float Fnv = pow(1.0 - dotNV, 2.9);\n    float FedgeRim = pow(1.0 - cosVNrim, 3.25);\n\n    float rimBandTight = mix(0.82, 0.52, smallGlass);\n    float bandFracR = mix(0.022, 0.042, smoothstep(62.0, 218.0, minDim));\n    float bandR = clamp(minDim * bandFracR * rimBandTight, mix(0.28, 0.65, 1.0 - smallGlass), min(12.0, minDim * 0.1));\n    float shellRim = smoothstep(bandR, bandR * 0.06, edgeDist) * smoothstep(-2.2, 0.0, distMask);\n    float centerQuiet = smoothstep(minDim * 0.18, minDim * 0.62, edgeDist);\n    float depthFade = mix(1.0, 0.62, centerQuiet);\n\n    vec2 cn = cKy / max(halfSz, vec2(1.0));\n    vec2 Lxy = normalize(u_lightDir + vec2(1e-5));\n    vec2 gN = normalize(gradLens + vec2(1e-4));\n    float edgeLight = dot(gN, Lxy);\n\n    float tl = max(0.0, min(-cn.x, -cn.y));\n    float trc = max(0.0, min(cn.x, -cn.y));\n    float br = max(0.0, min(cn.x, cn.y));\n    float bl = max(0.0, min(-cn.x, cn.y));\n    float lightDiag = smoothstep(-0.3, 0.3, Lxy.x + Lxy.y * 0.46);\n    float pairOpp = pow(clamp(mix(tl + br, trc + bl, lightDiag), 0.0, 1.0), 1.06);\n    float runAlong = smoothstep(0.14, 0.98, max(abs(cn.x), abs(cn.y)));\n    float sx = exp(-abs(cn.y) * (2.25 + 1.85 * pairOpp));\n    float sy = exp(-abs(cn.x) * (2.25 + 1.85 * pairOpp));\n    float streakOpp = pairOpp * runAlong * max(sx, sy);\n\n    vec3 hiSoft = vec3(0.98, 0.992, 1.008);\n    vec3 hiVeil = vec3(0.958, 0.978, 1.012);\n    vec3 oppTint = vec3(0.952, 0.968, 1.018);\n\n    float schlickW = F;\n    float litHairline = pow(max(edgeLight, 0.0), 3.6) * shellRim;\n    float oppGlow = pow(max(-edgeLight, 0.0), 1.05) * shellRim * (0.28 + 0.72 * FedgeRim * schlickW);\n    float rimLitSide = litHairline * u_rimStrength * mix(0.92, 1.18, smallGlass) * (0.58 + 0.42 * height) * depthFade;\n    float rimOpposite = oppGlow * u_rimStrength * mix(0.34, 0.48, smallGlass) * (0.4 + 0.6 * height) * depthFade;\n    color += hiSoft * rimLitSide * rimScale;\n    color += mix(hiVeil, oppTint, 0.42) * rimOpposite * rimScale;\n\n    float rimCorner = streakOpp * shellRim * u_rimStrength * 0.035 * FedgeRim * (0.35 + 0.65 * height);\n    color += hiSoft * rimCorner * rimScale;\n\n    float faceSheenSoft = smoothstep(bandR * 1.8, bandR * 0.08, edgeDist) * smoothstep(-2.0, 0.0, distMask)\n        * smoothstep(0.08, 0.82, edgeLight) * Fnv * schlickW * u_rimStrength * 0.022;\n    color += hiSoft * faceSheenSoft * (0.48 + 0.52 * height) * rimScale;\n\n    float plusHL = smoothstep(bandR * 0.95, bandR * 0.05, edgeDist) * u_plainHighlight * u_rimStrength\n        * pow(max(edgeLight, 0.0), 2.2) * (1.0 - 0.55 * centerQuiet);\n    plusHL *= mix(0.42, 0.06, smallGlass);\n    color += plusHL * vec3(0.99, 0.995, 1.0);\n\n    if (u_causticIntensity > 0.001) {\n        float causticDot = dot(normalize(vec3(gradH * u_normalStrength, 0.45)), Lp);\n        float caust = pow(max(causticDot, 0.0), 7.0) * u_causticIntensity * height;\n        color += caust * vec3(1.0, 0.96, 0.90);\n    }\n\n    float pressGlow = clamp(u_pressProgress, 0.0, 1.0) * clamp(u_glowStrength, 0.0, 1.0);\n    if (pressGlow > 0.001) {\n        vec2 glowPx = u_glowCenter * u_glassSize - halfSz;\n        float glowR = minDim * 1.5;\n        float spot = smoothstep(glowR, glowR * 0.5, length(pPx - glowPx));\n        color += vec3(1.0) * pressGlow * (0.08 + spot * 0.15);\n    }\n\n    gl_FragColor = vec4(color, opacity * u_transmittance);\n}\n")));
        this.glassProgram = createProgram("precision highp float;\nattribute vec2 a_position;\nuniform vec2 u_resolution;\nuniform vec2 u_mousePos;\nuniform vec2 u_glassSize;\nvarying vec2 v_screenTexCoord;\nvarying vec2 v_shapeCoord;\nvoid main() {\n    vec2 screenPos = u_mousePos + a_position * u_glassSize;\n    vec2 clipSpacePos = (screenPos / u_resolution) * 2.0 - 1.0;\n    gl_Position = vec4(clipSpacePos, 0.0, 1.0);\n    v_screenTexCoord = screenPos / u_resolution;\n    v_screenTexCoord.y = 1.0 - v_screenTexCoord.y;\n    v_shapeCoord = a_position;\n}\n", glassFragment);
        this.glassUniformLocations.clear();
        if (this.sourceProgram == 0 || this.blurHProgram == 0 || this.blurVProgram == 0 || this.glassProgram == 0) {
            throw new IllegalStateException("Prismal shader program creation failed");
        }
    }

    public final void ensureTargets(int nextWidth, int nextHeight) {
        if (this.width == nextWidth && this.height == nextHeight && this.outputTexture != 0) {
            return;
        }
        releaseTargets();
        this.width = Math.max(1, nextWidth);
        this.height = Math.max(1, nextHeight);
        this.blurWidth = Math.max(1, (int) (this.width * 0.5f));
        this.blurHeight = Math.max(1, (int) (this.height * 0.5f));
        this.sourceTexture = createTexture(this.width, this.height);
        this.sourceFramebuffer = createFramebuffer(this.sourceTexture);
        this.blurTextureH = createTexture(this.blurWidth, this.blurHeight);
        this.blurFramebufferH = createFramebuffer(this.blurTextureH);
        this.blurTextureV = createTexture(this.blurWidth, this.blurHeight);
        this.blurFramebufferV = createFramebuffer(this.blurTextureV);
        this.outputTexture = createTexture(this.width, this.height);
        this.outputFramebuffer = createFramebuffer(this.outputTexture);
    }

    public final void renderSourceAdapter(int inputTexture) {
        GLES20.glDisable(3042);
        GLES20.glDisable(3089);
        GLES20.glBindFramebuffer(36160, this.sourceFramebuffer);
        GLES20.glViewport(0, 0, this.width, this.height);
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(16384);
        GLES20.glUseProgram(this.sourceProgram);
        bindInterleavedQuad(this.sourceProgram);
        GLES20.glActiveTexture(33984);
        GLES20.glBindTexture(3553, inputTexture);
        GLES20.glUniform1i(requireUniform(this.sourceProgram, "uTexture"), 0);
        GLES20.glDrawArrays(5, 0, 4);
        unbindInterleavedQuad(this.sourceProgram);
    }

    public final void renderBlur(PrismalParams p) {
        float sigma = Math.max(p.blurRadiusPx * 0.5f, 0.5f);
        renderBlurPass(this.blurHProgram, this.sourceTexture, this.blurFramebufferH, sigma);
        renderBlurPass(this.blurVProgram, this.blurTextureH, this.blurFramebufferV, sigma);
    }

    public final void renderBlurPass(int program, int inputTexture, int framebuffer, float sigma) {
        GLES20.glDisable(3042);
        GLES20.glBindFramebuffer(36160, framebuffer);
        GLES20.glViewport(0, 0, this.blurWidth, this.blurHeight);
        GLES20.glClearColor(0.0f, 0.0f, 0.0f, 0.0f);
        GLES20.glClear(16384);
        GLES20.glUseProgram(program);
        int position = requireAttrib(program, "a_position");
        this.blurQuad.position(0);
        GLES20.glEnableVertexAttribArray(position);
        GLES20.glVertexAttribPointer(position, 2, 5126, false, 0, (Buffer) this.blurQuad);
        GLES20.glActiveTexture(33984);
        GLES20.glBindTexture(3553, inputTexture);
        GLES20.glUniform1i(requireUniform(program, "u_texture"), 0);
        GLES20.glUniform2f(requireUniform(program, "u_texelSize"), 1.0f / Math.max(1, this.blurWidth), 1.0f / Math.max(1, this.blurHeight));
        GLES20.glUniform1f(requireUniform(program, "u_sigma"), sigma);
        GLES20.glDrawArrays(4, 0, 6);
        GLES20.glDisableVertexAttribArray(position);
    }

    public final void renderGlassNode(PrismalGeometry prismalGeometry, PrismalParams prismalParams, PrismalHighlightProfile prismalHighlightProfile, PrismalInteractionState prismalInteractionState, boolean z, float f) {
        GLES20.glBindFramebuffer(36160, this.outputFramebuffer);
        GLES20.glViewport(0, 0, this.width, this.height);
        GLES20.glDisable(3089);
        if (z) {
            GLES20.glEnable(3042);
            GLES20.glBlendFuncSeparate(770, 771, 1, 771);
        } else {
            GLES20.glDisable(3042);
        }
        GLES20.glUseProgram(this.glassProgram);
        int iRequireAttrib = requireAttrib(this.glassProgram, "a_position");
        this.glassQuad.position(0);
        GLES20.glEnableVertexAttribArray(iRequireAttrib);
        GLES20.glVertexAttribPointer(iRequireAttrib, 2, 5126, false, 0, (Buffer) this.glassQuad);
        uniform2f("u_resolution", this.width, this.height);
        uniform2f("u_mousePos", prismalGeometry.centerX, this.height - prismalGeometry.centerY);
        uniform2f("u_glassSize", prismalGeometry.glassWidth, prismalGeometry.glassHeight);
        uniform4f("u_cornerRadii", prismalGeometry.topLeftRadius, prismalGeometry.topRightRadius, prismalGeometry.bottomRightRadius, prismalGeometry.bottomLeftRadius);
        uniform1f("u_refractionInset", prismalParams.refractionInsetPx);
        uniform1f("u_sminSmoothing", prismalParams.sminSmoothingPx);
        uniform1f("u_edgeRefractionFalloff", prismalParams.edgeRefractionFalloff);
        uniform1f("u_ior", prismalParams.ior);
        uniform1f("u_glassThickness", prismalParams.glassThicknessPx);
        uniform1f("u_normalStrength", prismalParams.normalStrength);
        uniform1f("u_displacementScale", prismalParams.displacementScale);
        uniform1f("u_heightTransitionWidth", prismalParams.heightTransitionWidthPx);
        uniform1f("u_lensRefractionPx", clamp(prismalParams.lensRefractionScale * 2.0f * prismalParams.heightTransitionWidthPx * ((clamp(prismalParams.liquidDome, 0.0f, 2.0f) * 0.55f) + 1.0f) * prismalParams.displacementScale, 4.0f, Math.max(4.0f, 0.85f * Math.min(prismalGeometry.glassWidth, prismalGeometry.glassHeight))));
        uniform1f("u_lensDepthEffect", prismalParams.lensDepthEffect);
        uniform1f("u_chromaticAberration", Math.max(0.0f, prismalParams.chromaticAberration));
        uniform1f("u_dispersionR", prismalParams.dispersionR);
        uniform1f("u_dispersionB", prismalParams.dispersionB);
        uniform1f("u_vibrancy", prismalParams.vibrancy);
        uniform1f("u_plainHighlight", prismalParams.plainHighlight);
        uniform1f("u_liquidDome", prismalParams.liquidDome);
        uniform1f("u_fresnelReflect", prismalParams.fresnelReflect);
        uniform1f("u_brightness", prismalParams.brightness);
        uniform4f("u_glassColor", prismalParams.tintR, prismalParams.tintG, prismalParams.tintB, prismalParams.tintA);
        uniform1f("u_highlightWidth", prismalParams.highlightWidth);
        uniform2f("u_lightDir", prismalParams.lightDirX, prismalParams.lightDirY);
        uniform1f("u_specular", prismalParams.specular);
        uniform1f("u_shininess", prismalParams.shininess);
        uniform1f("u_rimStrength", prismalParams.rimStrength);
        uniform4f("u_shadowColor", prismalParams.shadowR, prismalParams.shadowG, prismalParams.shadowB, prismalParams.shadowA);
        uniform1f("u_shadowSoftness", prismalParams.shadowSoftness);
        uniform1f("u_causticIntensity", prismalParams.causticIntensity);
        uniform1f("u_transmittance", prismalParams.transmittance * f);
        uniform2f("u_backdropSampleScale", prismalParams.backdropScaleX, prismalParams.backdropScaleY);
        uniform1f("u_parallaxScale", prismalParams.parallaxScale);
        float f2 = prismalInteractionState != null ? prismalInteractionState.pressProgress : prismalParams.pressProgress;
        float f3 = prismalInteractionState != null ? prismalInteractionState.glowCenterX : prismalParams.glowCenterX;
        float f4 = prismalInteractionState != null ? prismalInteractionState.glowCenterY : prismalParams.glowCenterY;
        uniform1f("u_pressProgress", f2);
        uniform1f("u_backdropPinch", prismalParams.backdropPinch);
        uniform2f("u_glowCenter", f3, f4);
        uniform1f("u_glowStrength", prismalParams.glowStrength);
        uniform1i("u_showNormals", prismalParams.showNormals ? 1 : 0);
        uniform1f("u_componentSkyHaze", prismalHighlightProfile.skyHaze ? 1.0f : 0.0f);
        uniform1f("u_componentSpecular", prismalHighlightProfile.specular ? 1.0f : 0.0f);
        uniform1f("u_componentLitRim", prismalHighlightProfile.litRim ? 1.0f : 0.0f);
        uniform1f("u_componentOppositeRim", prismalHighlightProfile.oppositeRim ? 1.0f : 0.0f);
        uniform1f("u_componentCornerRim", prismalHighlightProfile.cornerRim ? 1.0f : 0.0f);
        uniform1f("u_componentFaceSheen", prismalHighlightProfile.faceSheen ? 1.0f : 0.0f);
        uniform1f("u_componentPlainHighlight", prismalHighlightProfile.plainHighlight ? 1.0f : 0.0f);
        uniform1f("u_componentCaustics", prismalHighlightProfile.caustics ? 1.0f : 0.0f);
        uniform1f("u_componentPressGlow", prismalHighlightProfile.pressGlow ? 1.0f : 0.0f);
        GLES20.glActiveTexture(33984);
        GLES20.glBindTexture(3553, this.sourceTexture);
        GLES20.glUniform1i(glassUniformLocation("u_backgroundTexture"), 0);
        GLES20.glActiveTexture(33985);
        GLES20.glBindTexture(3553, this.blurTextureV);
        GLES20.glUniform1i(glassUniformLocation("u_blurredTexture"), 1);
        GLES20.glUniform1i(glassUniformLocation("u_useBlurredTexture"), 1);
        GLES20.glDrawArrays(4, 0, 6);
        GLES20.glDisableVertexAttribArray(iRequireAttrib);
        GLES20.glDisable(3042);
    }

    public final void bindInterleavedQuad(int program) {
        int position = requireAttrib(program, "aPosition");
        int uv = requireAttrib(program, "aUv");
        this.fullQuad.position(0);
        GLES20.glEnableVertexAttribArray(position);
        GLES20.glVertexAttribPointer(position, 2, 5126, false, 16, (Buffer) this.fullQuad);
        this.fullQuad.position(2);
        GLES20.glEnableVertexAttribArray(uv);
        GLES20.glVertexAttribPointer(uv, 2, 5126, false, 16, (Buffer) this.fullQuad);
    }

    public final void unbindInterleavedQuad(int program) {
        int position = GLES20.glGetAttribLocation(program, "aPosition");
        int uv = GLES20.glGetAttribLocation(program, "aUv");
        if (position >= 0) {
            GLES20.glDisableVertexAttribArray(position);
        }
        if (uv >= 0) {
            GLES20.glDisableVertexAttribArray(uv);
        }
    }

    public final int createTexture(int w, int h) {
        int[] ids = new int[1];
        GLES20.glGenTextures(1, ids, 0);
        GLES20.glBindTexture(3553, ids[0]);
        GLES20.glTexParameteri(3553, 10241, 9729);
        GLES20.glTexParameteri(3553, 10240, 9729);
        GLES20.glTexParameteri(3553, 10242, 33071);
        GLES20.glTexParameteri(3553, 10243, 33071);
        GLES20.glTexImage2D(3553, 0, 6408, w, h, 0, 6408, 5121, null);
        return ids[0];
    }

    public final int createFramebuffer(int texture) {
        int[] ids = new int[1];
        GLES20.glGenFramebuffers(1, ids, 0);
        GLES20.glBindFramebuffer(36160, ids[0]);
        GLES20.glFramebufferTexture2D(36160, 36064, 3553, texture, 0);
        int status = GLES20.glCheckFramebufferStatus(36160);
        if (status != 36053) {
            throw new IllegalStateException("Prismal framebuffer incomplete=0x" + Integer.toHexString(status));
        }
        return ids[0];
    }

    public final int createProgram(String vertexSource, String fragmentSource) {
        int vertex = compileShader(35633, vertexSource);
        int fragment = compileShader(35632, fragmentSource);
        int program = GLES20.glCreateProgram();
        GLES20.glAttachShader(program, vertex);
        GLES20.glAttachShader(program, fragment);
        GLES20.glLinkProgram(program);
        int[] linked = new int[1];
        GLES20.glGetProgramiv(program, 35714, linked, 0);
        GLES20.glDeleteShader(vertex);
        GLES20.glDeleteShader(fragment);
        if (linked[0] == 0) {
            String log = GLES20.glGetProgramInfoLog(program);
            GLES20.glDeleteProgram(program);
            throw new IllegalStateException("Prismal program link failed: " + log);
        }
        return program;
    }

    public final int compileShader(int type, String source) {
        int shader = GLES20.glCreateShader(type);
        GLES20.glShaderSource(shader, source);
        GLES20.glCompileShader(shader);
        int[] compiled = new int[1];
        GLES20.glGetShaderiv(shader, 35713, compiled, 0);
        if (compiled[0] == 0) {
            String log = GLES20.glGetShaderInfoLog(shader);
            GLES20.glDeleteShader(shader);
            throw new IllegalStateException("Prismal shader compile failed: " + log);
        }
        return shader;
    }

    public final int glassUniformLocation(String name) {
        Integer cached = (Integer) this.glassUniformLocations.get(name);
        if (cached != null) {
            return cached.intValue();
        }
        int location = GLES20.glGetUniformLocation(this.glassProgram, name);
        this.glassUniformLocations.put(name, Integer.valueOf(location));
        return location;
    }

    public final void uniform1f(String name, float value) {
        GLES20.glUniform1f(glassUniformLocation(name), value);
    }

    public final void uniform1i(String name, int value) {
        GLES20.glUniform1i(glassUniformLocation(name), value);
    }

    public final void uniform2f(String name, float x, float y) {
        GLES20.glUniform2f(glassUniformLocation(name), x, y);
    }

    public final void uniform4f(String name, float x, float y, float z, float w) {
        GLES20.glUniform4f(glassUniformLocation(name), x, y, z, w);
    }

    public static int requireUniform(int program, String name) {
        int location = GLES20.glGetUniformLocation(program, name);
        if (location < 0) {
            throw new IllegalStateException("missing Prismal uniform " + name);
        }
        return location;
    }

    public static int requireAttrib(int program, String name) {
        int location = GLES20.glGetAttribLocation(program, name);
        if (location < 0) {
            throw new IllegalStateException("missing Prismal attribute " + name);
        }
        return location;
    }

    public static float clamp(float value, float min, float max) {
        return Math.max(min, Math.min(max, value));
    }

    public static FloatBuffer floatBuffer(float[] values) {
        FloatBuffer buffer = ByteBuffer.allocateDirect(values.length * 4).order(ByteOrder.nativeOrder()).asFloatBuffer();
        buffer.put(values).position(0);
        return buffer;
    }

    public final void releaseTargets() {
        if (this.sourceFramebuffer != 0) {
            GLES20.glDeleteFramebuffers(1, new int[]{this.sourceFramebuffer}, 0);
        }
        if (this.blurFramebufferH != 0) {
            GLES20.glDeleteFramebuffers(1, new int[]{this.blurFramebufferH}, 0);
        }
        if (this.blurFramebufferV != 0) {
            GLES20.glDeleteFramebuffers(1, new int[]{this.blurFramebufferV}, 0);
        }
        if (this.outputFramebuffer != 0) {
            GLES20.glDeleteFramebuffers(1, new int[]{this.outputFramebuffer}, 0);
        }
        if (this.sourceTexture != 0) {
            GLES20.glDeleteTextures(1, new int[]{this.sourceTexture}, 0);
        }
        if (this.blurTextureH != 0) {
            GLES20.glDeleteTextures(1, new int[]{this.blurTextureH}, 0);
        }
        if (this.blurTextureV != 0) {
            GLES20.glDeleteTextures(1, new int[]{this.blurTextureV}, 0);
        }
        if (this.outputTexture != 0) {
            GLES20.glDeleteTextures(1, new int[]{this.outputTexture}, 0);
        }
        this.outputFramebuffer = 0;
        this.blurFramebufferV = 0;
        this.blurFramebufferH = 0;
        this.sourceFramebuffer = 0;
        this.outputTexture = 0;
        this.blurTextureV = 0;
        this.blurTextureH = 0;
        this.sourceTexture = 0;
        this.blurHeight = 0;
        this.blurWidth = 0;
        this.height = 0;
        this.width = 0;
        this.backdropPrepared = false;
        this.glassFrameBegun = false;
        this.glassDrawCount = 0;
        this.legacySingleDraw = false;
    }

    @Override // java.lang.AutoCloseable
    public void close() {
        releaseTargets();
        if (this.sourceProgram != 0) {
            GLES20.glDeleteProgram(this.sourceProgram);
        }
        if (this.blurHProgram != 0) {
            GLES20.glDeleteProgram(this.blurHProgram);
        }
        if (this.blurVProgram != 0) {
            GLES20.glDeleteProgram(this.blurVProgram);
        }
        if (this.glassProgram != 0) {
            GLES20.glDeleteProgram(this.glassProgram);
        }
        this.glassProgram = 0;
        this.blurVProgram = 0;
        this.blurHProgram = 0;
        this.sourceProgram = 0;
        this.glassUniformLocations.clear();
    }
}
