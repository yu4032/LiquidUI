package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class NotificationGlassVisualCorrectnessArchitectureTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    public void prismalOpacityMaskUsesActualPerCornerRadius() throws Exception {
        String gate = read("prismal/src/main/java/com/hellovoid/prismal/PrismalComponentGateShader.java");

        assertTrue(gate.contains(
                "float distMask = sdRoundBox(pPx, halfSz, crMask, u_sminSmoothing);"));
        assertTrue(gate.contains("float distMask = sdKy;"));
        assertTrue(gate.contains("Prismal per-corner opacity mask"));
    }

    @Test
    public void notificationOffsetProbeDisablesParallaxOnly() throws Exception {
        String material = read(
                "src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassMaterial.java");

        assertTrue(material.contains("b.parallaxScale = 0f"));
        assertTrue(material.contains("b.displacementScale = 1.70f"));
        assertTrue(material.contains("b.lensRefractionScale = 2.20f"));
        assertTrue(material.contains("b.chromaticAberration = 42f"));
    }
}
