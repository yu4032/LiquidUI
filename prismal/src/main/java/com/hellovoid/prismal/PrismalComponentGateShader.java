package com.hellovoid.prismal;

/* JADX INFO: loaded from: classes.dex */
public abstract class PrismalComponentGateShader {
    public static String apply(String fragment) {
        if (fragment == null) {
            throw new IllegalArgumentException("fragment == null");
        }
        // PrismalGeometry exposes four independent radii and the shader already computes sdKy
        // from the radius of the current quadrant. The upstream opacity mask instead collapsed all
        // corners to their minimum radius; one square edge therefore made every corner square.
        // Reuse sdKy as the actual material mask while leaving the remaining optical model intact.
        String cornerCorrected = replaceExactlyOnce(
                fragment,
                "float distMask = sdRoundBox(pPx, halfSz, crMask, u_sminSmoothing);",
                "float distMask = sdKy;",
                "Prismal per-corner opacity mask");
        String corrected = replaceExactlyOnce(cornerCorrected, "uniform float u_glowStrength;", "uniform float u_glowStrength;\nuniform float u_componentSkyHaze;\nuniform float u_componentSpecular;\nuniform float u_componentLitRim;\nuniform float u_componentOppositeRim;\nuniform float u_componentCornerRim;\nuniform float u_componentFaceSheen;\nuniform float u_componentPlainHighlight;\nuniform float u_componentCaustics;\nuniform float u_componentPressGlow;\n", "Prismal component uniform anchor");
        return gateExactlyOnce(gateExactlyOnce(gateExactlyOnce(gateExactlyOnce(gateExactlyOnce(gateExactlyOnce(gateExactlyOnce(gateExactlyOnce(gateExactlyOnce(corrected, "color = mix(color, mix(color, skyHaze, 0.55 + 0.1 * fresCtl), skyW);", "u_componentSkyHaze", "sky haze"), "color += (specP + specS) * vec3(0.99, 0.993, 1.0);", "u_componentSpecular", "specular"), "color += hiSoft * rimLitSide * rimScale;", "u_componentLitRim", "lit rim"), "color += mix(hiVeil, oppTint, 0.42) * rimOpposite * rimScale;", "u_componentOppositeRim", "opposite rim"), "color += hiSoft * rimCorner * rimScale;", "u_componentCornerRim", "corner rim"), "color += hiSoft * faceSheenSoft * (0.48 + 0.52 * height) * rimScale;", "u_componentFaceSheen", "face sheen"), "color += plusHL * vec3(0.99, 0.995, 1.0);", "u_componentPlainHighlight", "plain highlight"), "color += caust * vec3(1.0, 0.96, 0.90);", "u_componentCaustics", "caustics"), "color += vec3(1.0) * pressGlow * (0.08 + spot * 0.15);", "u_componentPressGlow", "press glow");
    }

    public static String gateExactlyOnce(String source, String statement, String uniform, String label) {
        return replaceExactlyOnce(source, statement, "if (" + uniform + " > 0.5) { " + statement + " }", "Prismal " + label + " component");
    }

    public static String replaceExactlyOnce(String source, String oldText, String newText, String label) {
        int first = source.indexOf(oldText);
        if (first < 0 || source.indexOf(oldText, oldText.length() + first) >= 0) {
            throw new IllegalStateException(label + " upstream contract changed");
        }
        return source.substring(0, first) + newText + source.substring(oldText.length() + first);
    }
}
