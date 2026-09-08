package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public final class MiuiSystemUiPluginGlassBridgeArchitectureTest {
    @Test
    public void exactPluginLifecycleOwnsDynamicClassLoaderSessions() throws Exception {
        String hook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/plugin/MiuiSystemUiPluginGlassHook.java"));
        String module = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/ModuleMain.java"));

        assertTrue(hook.contains("com.android.systemui.shared.plugins.PluginInstance"));
        assertTrue(hook.contains("\"loadPlugin\""));
        assertTrue(hook.contains("\"unloadPlugin\""));
        assertTrue(hook.contains("\"getPackage\""));
        assertTrue(hook.contains("\"getPlugin\""));
        assertTrue(hook.contains("\"getPluginContext\""));

        assertTrue(hook.contains("miui.systemui.plugin"));
        assertTrue(hook.contains("171047100L"));
        assertTrue(hook.contains("17.1.4.71.0"));
        assertTrue(hook.contains("getClass().getClassLoader()"));
        assertTrue(hook.contains("afterBackend.intercept"));
        assertTrue(hook.contains("beforeBackend.intercept"));
        assertTrue(hook.contains("closePluginSession"));

        assertTrue(module.contains("new MiuiSystemUiPluginGlassHook("));
        assertTrue(module.contains("new Api101BeforeMethodHookBackend"));
        assertTrue(module.contains("new Api101AfterMethodHookBackend"));

        String combined = hook + module;
        assertFalse(hook.contains("VolumePanelViewController"));
        assertFalse(hook.contains("QSTileItemView"));
        assertFalse(hook.contains("ToggleSliderView"));
        assertFalse(combined.contains("new WindowGlassRenderer"));
        assertFalse(combined.contains("SurfaceTexture"));
        assertFalse(combined.contains("SetPassBlurSurface"));
        assertFalse(combined.contains("PixelCopy"));
        assertFalse(combined.contains("ScreenCapture"));
    }

    @Test
    public void multiplePluginInstanceWrappersShareOneSessionPerClassLoader() throws Exception {
        String hook = Files.readString(Path.of(
                "src/main/java/com/hellovoid/liquidui/glass/plugin/MiuiSystemUiPluginGlassHook.java"));

        assertTrue(hook.contains("IdentityHashMap<Object, ClassLoader> pluginOwners"));
        assertTrue(hook.contains("IdentityHashMap<ClassLoader, SharedPluginSession> sharedPluginSessions"));
        assertTrue(hook.contains("acquirePluginSession("));
        assertTrue(hook.contains("releasePluginOwner("));
        assertTrue(hook.contains("owners.isEmpty()"));
        assertFalse(hook.contains("IdentityHashMap<Object, AutoCloseable> pluginSessions"));
    }
}
