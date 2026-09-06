package com.hellovoid.prismal;

/* JADX INFO: loaded from: classes.dex */
public abstract class PrismalOpticalEdgeShader {
    public static String apply(String source) {
        requireSingle(source, "precision highp float;");
        requireSingle(source, "float edgeDist = -distMask;");
        requireSingle(source, "float opacity = 1.0 - smoothstep(-inset * 0.5, 0.0, distMask);");
        return source.replace("precision highp float;", "#extension GL_OES_standard_derivatives : enable\n\nprecision highp float;").replace("float edgeDist = -distMask;", "float edgeDist = -distMask;\n    float opticalEdgeScale = clamp(u_highlightWidth, 0.5, 3.0);\n    float edgeAa = max(fwidth(distMask), 0.75);").replace("float opacity = 1.0 - smoothstep(-inset * 0.5, 0.0, distMask);", "float opacity = 1.0 - smoothstep(-edgeAa, edgeAa, distMask);").replace("minDim * 0.09", "minDim * 0.09 * opticalEdgeScale").replace("tw * 0.42", "tw * 0.42 * opticalEdgeScale").replace("minDim * 0.12", "minDim * 0.12 * opticalEdgeScale").replace("bandFracR * rimBandTight", "bandFracR * opticalEdgeScale * rimBandTight");
    }

    public static void requireSingle(String source, String anchor) {
        int first = source.indexOf(anchor);
        if (first < 0 || source.indexOf(anchor, anchor.length() + first) >= 0) {
            throw new IllegalArgumentException("Expected exactly one Prismal optical-edge anchor: " + anchor);
        }
    }
}
