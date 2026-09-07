package com.hellovoid.liquidui.glass.notification;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class NotificationOesMappingContractTest {
    private static String source(String relative) throws Exception {
        return Files.readString(Path.of(relative));
    }

    @Test
    public void rotationCorrectionPrecedesSurfaceTextureMatrixWithoutCropInversion() throws Exception {
        String shader = source("src/main/java/com/hellovoid/liquidui/glass/notification/Miuix307PassBlurShaders.java");

        assertTrue(shader.contains("vec2 orientedUv = orientRootUv(rootUv);"));
        assertTrue(shader.contains("uTexMatrix * vec4(orientedUv, 0.0, 1.0)"));
        assertFalse(shader.contains("compensateSurfaceTextureCropPreservingOrientation"));
    }

    @Test
    public void diagnosticProbeIsRemovedAfterMappingDecision() throws Exception {
        String shader = source("src/main/java/com/hellovoid/liquidui/glass/notification/Miuix307PassBlurShaders.java");
        String material = source("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassMaterial.java");

        assertFalse(shader.contains("MAPPING_PROBE_ENABLED = true"));
        assertFalse(shader.contains("panelUv"));
        assertFalse(material.contains("MAPPING_PROBE_IDENTITY"));
        assertTrue(material.contains("b.displacementScale = 1.70f;"));
        assertTrue(material.contains("b.chromaticAberration = 42f;"));
    }
}
