package com.hellovoid.liquidui.architecture;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.Test;

public final class SystemUiDomainOwnershipArchitectureTest {
    private static final List<String> DOMAINS = List.of(
            "controlcenter", "volume", "media", "statusbar", "keyguard", "misc");
    private static final List<String> FORBIDDEN = List.of(
            "EGL14",
            "GL_TEXTURE_EXTERNAL_OES",
            "SurfaceTexture",
            "SetPassBlurSurface",
            "HandlerThread",
            "PixelCopy",
            "ScreenCapture",
            "glReadPixels");

    @Test
    public void domainAdaptersNeverOwnGpuOrCapturePipelines() throws Exception {
        Path root = Path.of("src/main/java/com/hellovoid/liquidui/glass");
        for (String domain : DOMAINS) {
            Path dir = root.resolve(domain);
            if (!Files.isDirectory(dir)) continue;
            try (var files = Files.walk(dir)) {
                for (Path file : files.filter(path -> path.toString().endsWith(".java")).toList()) {
                    assertNoForbiddenOwnership(file);
                }
            }
        }
    }

    private static void assertNoForbiddenOwnership(Path file) throws IOException {
        String source = Files.readString(file);
        for (String token : FORBIDDEN) {
            if (source.contains(token)) {
                throw new AssertionError(file + " must not own " + token);
            }
        }
    }
}
