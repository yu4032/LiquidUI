package com.hellovoid.liquidui.architecture;

import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.assertTrue;

public class LockScreenClockGlassArchitectureTest {
    private static final Path HOOK = Path.of(
            "src/main/java/com/hellovoid/liquidui/glass/lockscreen/LockScreenClockGlassHook.java");
    private static final Path MODULE = Path.of(
            "src/main/java/com/hellovoid/liquidui/ModuleMain.java");

    @Test
    public void clockGlassBridgeStaysNarrowAndFailClosed() throws Exception {
        String source = Files.readString(HOOK);
        assertTrue(source.contains("com.miui.clock.MiuiClockController"));
        assertTrue(source.contains("addClockView"));
        assertTrue(source.contains("setClockEffect"));
        assertTrue(source.contains("GLASS_EFFECT = 5"));
        assertTrue(source.contains("SystemUiGlassDomain.KEYGUARD"));
        assertTrue(source.contains("KeepNativeClockMaterial"));
        assertTrue(source.contains("never hide clock glyphs"));
        assertTrue(!source.contains("setMiGlass("));
        assertTrue(!source.contains("setMiGlassBlurRadius("));
        assertTrue(!source.contains("setMiViewMaterialType("));
    }

    @Test
    public void moduleRegistersClockGlassHookWithSharedCore() throws Exception {
        String source = Files.readString(MODULE);
        assertTrue(source.contains("LockScreenClockGlassHook"));
        assertTrue(source.contains("processGlassCore"));
    }
}
