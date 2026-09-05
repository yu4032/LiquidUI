package com.hellovoid.liquidui.reflect;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public class TargetClassResolverContractTest {
    @Test
    public void requireUsesSuppliedTargetClassLoader() throws Exception {
        RecordingClassLoader loader = new RecordingClassLoader(getClass().getClassLoader());
        Class<?> resolved = TargetClassResolver.require(loader, "java.lang.String");
        assertEquals(String.class, resolved);
        assertTrue(loader.requested.contains("java.lang.String"));
    }

    @Test
    public void findReturnsNullOnlyForClassNotFound() {
        RecordingClassLoader loader = new RecordingClassLoader(getClass().getClassLoader());
        assertNull(TargetClassResolver.find(loader, "vendor.missing.Type"));
        assertTrue(loader.requested.contains("vendor.missing.Type"));
    }

    @Test
    public void requirePropagatesClassNotFound() {
        boolean threw = false;
        try {
            TargetClassResolver.require(new RecordingClassLoader(getClass().getClassLoader()),
                    "vendor.missing.Type");
        } catch (ClassNotFoundException expected) {
            threw = true;
        }
        assertTrue(threw);
    }

    @Test
    public void linkageFailureIsNotCollapsedToMissingClass() {
        ClassLoader loader = new ClassLoader(getClass().getClassLoader()) {
            @Override
            public Class<?> loadClass(String name) throws ClassNotFoundException {
                if (name.equals("vendor.broken.Type")) throw new NoClassDefFoundError("dependency");
                return super.loadClass(name);
            }
        };
        boolean threw = false;
        try {
            TargetClassResolver.find(loader, "vendor.broken.Type");
        } catch (NoClassDefFoundError expected) {
            threw = true;
        }
        assertTrue(threw);
    }

    private static final class RecordingClassLoader extends ClassLoader {
        private final List<String> requested = new ArrayList<>();

        RecordingClassLoader(ClassLoader parent) { super(parent); }

        @Override
        public Class<?> loadClass(String name) throws ClassNotFoundException {
            requested.add(name);
            if (name.startsWith("vendor.missing.")) throw new ClassNotFoundException(name);
            return super.loadClass(name);
        }
    }
}
