package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class NotificationNativePassBlurSourceExperimentTest {
    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path));
    }

    @Test
    public void producerTargetsNotificationShadeViewRootWhileVendorGateOwnsEnablement() throws Exception {
        String hook = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationLiquidGlassHook.java");
        String session = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationGlassSession.java");
        String renderer = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationPassBlurTextureView.java");
        String bridge = read("src/main/java/com/hellovoid/liquidui/glass/notification/SystemUiPassBlurBridge.java");

        assertTrue(hook.contains("authorityState.observe(requested)"));
        assertFalse(hook.contains("RootTaskDisplayAreaOrganizer"));
        assertFalse(hook.contains("onDisplayAreaAppeared"));
        assertFalse(hook.contains("sourceState.observe"));
        assertFalse(session.contains("NotificationPassBlurSourceState"));
        assertFalse(renderer.contains("sourceState.snapshot"));
        assertFalse(renderer.contains("sourceGeneration"));
        assertTrue(renderer.contains("geometry.rootSurface"));
        assertTrue(bridge.contains("sourceAuthority=NotificationShadeViewRoot-native"));
        assertTrue(bridge.contains("setPassBlurSurface.invoke(transaction, hostRootSurface, producerSurface)"));
        assertFalse(bridge.contains("setMiBlurWinExc"));
    }

    @Test
    public void nativeRootExperimentKeepsRootAndEndpointGenerationBarriers() throws Exception {
        String renderer = read("src/main/java/com/hellovoid/liquidui/glass/notification/NotificationPassBlurTextureView.java");
        String bridge = read("src/main/java/com/hellovoid/liquidui/glass/notification/SystemUiPassBlurBridge.java");
        assertTrue(renderer.contains("inputProducerGeneration = ++nextProducerGeneration"));
        assertTrue(renderer.contains("endpointGeneration != inputProducerGeneration"));
        assertTrue(renderer.contains("isSameSurface(binding.hostRootSurface, geometry.rootSurface)"));
        assertTrue(bridge.contains("surfaceSequenceId"));
        assertTrue(bridge.contains("viewRootIdentity"));
    }
}
