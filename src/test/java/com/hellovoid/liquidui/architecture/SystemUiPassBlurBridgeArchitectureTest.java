package com.hellovoid.liquidui.architecture;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.Test;

public final class SystemUiPassBlurBridgeArchitectureTest {
    private static final Path CORE_BRIDGE = Path.of(
            "src/main/java/com/hellovoid/liquidui/glass/core/SystemUiPassBlurBridge.java");

    @Test
    public void passBlurAuthorityLivesInGenericCoreAtNativeScale() throws Exception {
        String source = Files.readString(CORE_BRIDGE);
        assertTrue(source.contains("package com.hellovoid.liquidui.glass.core;"));
        assertTrue(source.contains("SCALE = 1.0f"));
        assertTrue(source.contains("SetPassBlurSurface"));
        assertTrue(source.contains("setUpdateTextureFlag"));
        assertFalse(source.contains("NotificationShade"));
        assertFalse(source.contains("glReadPixels"));
        assertFalse(source.contains("PixelCopy"));
    }
}
