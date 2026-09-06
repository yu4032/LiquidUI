package com.hellovoid.prismal;

/* JADX INFO: loaded from: classes.dex */
public abstract class PrismalComponentGateShader {
    public static String apply(String fragment) {
        if (fragment == null) {
            throw new IllegalArgumentException("fragment == null");
        }
        String corrected = replaceExactlyOnce(fragment, "uniform float u_glowStrength;", "uniform float u_glowStrength;\nuniform float u_componentSkyHaze;\nuniform float u_componentSpecular;\nuniform float u_componentLitRim;\nuniform float u_componentOppositeRim;\nuniform float u_componentCornerRim;\nuniform float u_componentFaceSheen;\nuniform float u_componentPlainHighlight;\nuniform float u_componentCaustics;\nuniform float u_componentPressGlow;\n", "Prismal component uniform anchor");
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
