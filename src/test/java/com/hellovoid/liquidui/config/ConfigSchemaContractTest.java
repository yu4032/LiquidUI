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
        assertEquals(3L, ConfigSchema.all().size());

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

        ConfigReader overrides = new ConfigReader((name, fallback) -> {
            if (name.equals("enabled")) return false;
            if (name.equals("diagnostics_enabled")) return true;
            if (name.equals("notification_glass_enabled")) return false;
            return fallback;
        });
        LiquidUiConfig config = LiquidUiConfig.from(overrides);
        assertFalse(config.enabled());
        assertTrue(config.diagnosticsEnabled());
        assertFalse(config.notificationGlassEnabled());
    }
}
