package com.hellovoid.prismal;

/* JADX INFO: loaded from: classes.dex */
public abstract class PrismalSingleEdgeShader {
    public static String apply(String upstreamFragment) {
        if (upstreamFragment == null) {
            throw new IllegalArgumentException("upstreamFragment == null");
        }
        String corrected = replaceExactlyOnce(upstreamFragment, "    vec2 cenSafe = cKy + vec2(1e-4, 1e-4);\n    vec2 lensDir = gradLens + u_lensDepthEffect * normalize(cenSafe);\n    float ldLen = length(lensDir);\n    lensDir = ldLen > 1e-5 ? lensDir / ldLen : vec2(0.0);\n", "    // A straight Dock edge must have one translation-invariant refraction direction.\n    // Keep the transmitted lens on the local SDF normal instead of biasing it toward\n    // the center of an extremely wide glass rectangle.\n    vec2 lensDir = length(gradLens) > 1e-5 ? normalize(gradLens) : vec2(0.0);\n", "Prismal lens-direction block");
        return replaceExactlyOnce(replaceExactlyOnce(replaceExactlyOnce(corrected, "    vec2 lensDeltaUv = (dLens * lensDir) / u_resolution;\n    float parallaxK = 0.052 * u_displacementScale;\n    vec2 parallax = (gradLens * height * (7.0 + 22.0 * F)) / u_resolution * parallaxK * u_parallaxScale;\n    lensDeltaUv += parallax;\n    lensDeltaUv *= mix(0.78, 1.12, (1.0 - F) * (0.42 + 0.58 * height));\n\n    float refrStr = height * (0.5 + F * 0.35);\n    vec3 refIn = refract(-V, N, 1.0 / u_ior);\n    vec3 refOut = (dot(refIn, refIn) < 0.001) ? vec3(0.0) : refract(refIn, -N, u_ior);\n    vec2 snellOff = (refOut.xy * u_glassThickness * refrStr / u_resolution) * u_displacementScale;\n    snellOff *= mix(0.72, 1.18, (1.0 - F) * (0.5 + 0.5 * height));\n\n    vec2 bDir = length(pPx) > 1e-3 ? -normalize(pPx) : vec2(0.0, -1.0);\n    float bulge = smoothstep(0.05, 0.38, tDeep) * (1.0 - smoothstep(0.52, 0.94, tDeep));\n    bulge = pow(max(bulge, 0.0), 0.62) * height * (0.014 + 0.01 * dome);\n    bulge *= smoothstep(0.02, 0.36, tDeep) * dropLens;\n    vec2 bulgeUv = bDir * bulge * u_glassSize / u_resolution;\n    snellOff *= pxNorm * dropLens;\n    bulgeUv *= pxNorm;\n\n    vec2 baseOffset = lensDeltaUv + snellOff + bulgeUv;\n", "    // LiquidDock's Dock glass uses one spatial refraction field. dLens is maximal at\n    // the silhouette and monotonically decays to zero over refractionHeight.\n    vec2 edgeRefractionUv = (dLens * lensDir) / u_resolution;\n    vec2 baseOffset = edgeRefractionUv;\n", "Prismal transmitted-refraction block"), "vec2 dispDir = length(pPx) > 1e-3 ? normalize(pPx) : vec2(0.0, 1.0);", "vec2 dispDir = length(cKy) > 1e-3 ? normalize(cKy) : vec2(0.0, 1.0);", "Prismal chromatic direction"), "vec2 chromaPush = dispDir * chromaBase * pxNorm;", "vec2 chromaPush = (dispDir * chromaBase * pxNorm * minDim) / u_resolution;", "Prismal chromatic scale");
    }

    public static String replaceExactlyOnce(String source, String oldText, String newText, String label) {
        int first = source.indexOf(oldText);
        if (first < 0 || source.indexOf(oldText, oldText.length() + first) >= 0) {
            throw new IllegalStateException(label + " upstream contract changed");
        }
        return source.substring(0, first) + newText + source.substring(oldText.length() + first);
    }
}
