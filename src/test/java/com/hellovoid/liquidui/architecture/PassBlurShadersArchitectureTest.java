package com.hellovoid.liquidui.architecture;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public final class PassBlurShadersArchitectureTest {
    @Test
    public void genericShaderUsesOnlyComposedWindowToOesTransform() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/core/PassBlurShaders.java"));

        assertTrue(source.contains("uniform mat4 uWindowUvToOes"));
        assertTrue(source.contains("samplerExternalOES"));
        assertFalse(source.contains("uConfigRot"));
        assertFalse(source.contains("orientRootUv"));
        assertFalse(source.contains("uBackdropRect"));
        assertFalse(source.contains("pixelOffset"));
        assertFalse(source.contains("Notification"));
    }
}
