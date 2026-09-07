package com.hellovoid.liquidui.config;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Modifier;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashSet;
import java.util.Set;

import static org.junit.Assert.*;

public class ConfigSchemaContractTest {
    @Test
    public void bootstrapSchemaContainsOnlyDeclaredKeysWithStableDefaults() {
        assertEquals("enabled", ConfigSchema.ENABLED.name());
        assertTrue(ConfigSchema.ENABLED.defaultValue());
        assertEquals("diagnostics_enabled", ConfigSchema.DIAGNOSTICS_ENABLED.name());
        assertFalse(ConfigSchema.DIAGNOSTICS_ENABLED.defaultValue());
        assertEquals("notification_glass_enabled", ConfigSchema.NOTIFICATION_GLASS_ENABLED.name());
        assertTrue(ConfigSchema.NOTIFICATION_GLASS_ENABLED.defaultValue());
        ConfigKey<?> redDebug = reflectedSchemaKey("NOTIFICATION_DEBUG_FORCE_RED_BACKGROUND");
        assertEquals("notification_debug_force_red_background", redDebug.name());
        assertFalse((Boolean) redDebug.defaultValue());
        assertEquals(4L, ConfigSchema.all().size());

        Set<String> names = new HashSet<>();
        for (ConfigKey<?> key : ConfigSchema.all()) {
            assertTrue(names.add(key.name()));
        }
    }

    @Test
    public void configKeyConstructorIsNotPublicOrProtected() {
        Constructor<?> constructor = ConfigKey.class.getDeclaredConstructors()[0];
        int modifiers = constructor.getModifiers();
        assertFalse(Modifier.isPublic(modifiers));
        assertFalse(Modifier.isProtected(modifiers));
    }

    @Test
    public void productionConfigKeysAreCreatedOnlyBySchemaAuthority() throws Exception {
        Path root = Path.of("src/main/java/com/hellovoid/liquidui");
        StringBuilder violations = new StringBuilder();
        try (var paths = Files.walk(root)) {
            paths.filter(path -> path.toString().endsWith(".java"))
                    .filter(path -> !path.endsWith("config/ConfigSchema.java"))
                    .forEach(path -> {
                        try {
                            String source = Files.readString(path);
                            if (source.contains("new ConfigKey")) violations.append(path).append('\n');
                        } catch (Exception error) {
                            throw new RuntimeException(error);
                        }
                    });
        }
        assertEquals("", violations.toString());
    }

    @Test
    public void immutableSnapshotReadsSchemaDefaultsAndOverrides() {
        ConfigReader defaults = new ConfigReader((name, fallback) -> fallback);
        LiquidUiConfig defaultConfig = LiquidUiConfig.from(defaults);
        assertTrue(defaultConfig.enabled());
        assertFalse(defaultConfig.diagnosticsEnabled());
        assertTrue(defaultConfig.notificationGlassEnabled());
        assertFalse(reflectedBooleanGetter(defaultConfig, "notificationDebugForceRedBackground"));

        ConfigReader overrides = new ConfigReader((name, fallback) -> {
            if (name.equals("enabled")) return false;
            if (name.equals("diagnostics_enabled")) return true;
            if (name.equals("notification_glass_enabled")) return false;
            if (name.equals("notification_debug_force_red_background")) return true;
            return fallback;
        });
        LiquidUiConfig config = LiquidUiConfig.from(overrides);
        assertFalse(config.enabled());
        assertTrue(config.diagnosticsEnabled());
        assertFalse(config.notificationGlassEnabled());
        assertTrue(reflectedBooleanGetter(config, "notificationDebugForceRedBackground"));
    }

    private static ConfigKey<?> reflectedSchemaKey(String fieldName) {
        try {
            return (ConfigKey<?>) ConfigSchema.class.getField(fieldName).get(null);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("missing schema key " + fieldName, error);
        }
    }

    private static boolean reflectedBooleanGetter(Object receiver, String methodName) {
        try {
            return (Boolean) receiver.getClass().getMethod(methodName).invoke(receiver);
        } catch (ReflectiveOperationException error) {
            throw new AssertionError("missing config getter " + methodName, error);
        }
    }
}
