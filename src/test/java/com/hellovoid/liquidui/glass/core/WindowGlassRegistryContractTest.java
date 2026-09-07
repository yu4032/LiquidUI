package com.hellovoid.liquidui.glass.core;

import org.junit.Test;

import java.lang.ref.WeakReference;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class WindowGlassRegistryContractTest {
    @Test
    public void sameRootAndDisplayReuseOneSession() {
        AtomicInteger created = new AtomicInteger();
        WindowGlassRegistry registry = new WindowGlassRegistry(key -> {
            created.incrementAndGet();
            return new WindowGlassSession(key);
        });
        Object root = new Object();

        WindowGlassSession first = registry.sessionFor(new WindowKey(root, 0));
        WindowGlassSession same = registry.sessionFor(new WindowKey(root, 0));
        WindowGlassSession otherDisplay = registry.sessionFor(new WindowKey(root, 1));
        WindowGlassSession otherRoot = registry.sessionFor(new WindowKey(new Object(), 0));

        assertTrue(first == same);
        assertFalse(first == otherDisplay);
        assertFalse(first == otherRoot);
        assertEquals(3L, created.get());
    }

    @Test
    public void exactSessionRemovalDoesNotRemoveReplacement() {
        WindowGlassRegistry registry = new WindowGlassRegistry(WindowGlassSession::new);
        Object root = new Object();
        WindowKey key = new WindowKey(root, 0);
        WindowGlassSession first = registry.sessionFor(key);

        assertTrue(registry.remove(key, first));
        WindowGlassSession replacement = registry.sessionFor(new WindowKey(root, 0));

        assertFalse(first == replacement);
        assertFalse(registry.remove(key, first));
        assertTrue(replacement == registry.sessionFor(new WindowKey(root, 0)));
    }

    @Test
    public void purgeClosesSessionsWhoseRootWasCollected() {
        WindowGlassRegistry registry = new WindowGlassRegistry(WindowGlassSession::new);
        Object root = new Object();
        WeakReference<Object> weakRoot = new WeakReference<>(root);
        WindowGlassSession session = registry.sessionFor(new WindowKey(root, 0));
        root = null;

        for (int attempt = 0; attempt < 40 && weakRoot.get() != null; attempt++) {
            System.gc();
            Thread.yield();
        }
        if (weakRoot.get() == null) {
            assertEquals(1L, registry.purgeCollected());
            assertTrue(session.isClosed());
        }
    }

    @Test
    public void globalCoreDelegatesToOneRegistryAndClosesEverySession() {
        SystemUiGlassCore core = new SystemUiGlassCore(WindowGlassSession::new);
        WindowGlassSession first = core.sessionFor(new Object(), 0);
        WindowGlassSession second = core.sessionFor(new Object(), 1);

        core.close();

        assertTrue(first.isClosed());
        assertTrue(second.isClosed());
        assertTrue(core.isClosed());
    }
}
