package com.hellovoid.liquidui.glass.core;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Regression contract: unchanged source + blur profile must not rebuild Gaussian blur on scene-only frames. */
public class PrismalBlurReuseStateContractTest {
    private static Object newState() throws Exception {
        Class<?> type = null;
        try {
            type = Class.forName("com.hellovoid.liquidui.glass.core.PrismalBlurReuseState");
        } catch (ClassNotFoundException ignored) {}
        assertNotNull(type);
        Constructor<?> constructor = type.getDeclaredConstructor();
        constructor.setAccessible(true);
        return constructor.newInstance();
    }

    private static boolean needsRebuild(Object state, long sourceFrame, float blurRadius)
            throws Exception {
        Method method = state.getClass().getDeclaredMethod("needsRebuild", long.class, float.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(state, sourceFrame, blurRadius);
    }

    private static void markPrepared(Object state, long sourceFrame, float blurRadius)
            throws Exception {
        Method method = state.getClass().getDeclaredMethod("markPrepared", long.class, float.class);
        method.setAccessible(true);
        method.invoke(state, sourceFrame, blurRadius);
    }

    @Test
    public void geometryOnlyFramesReuseExistingBlurTexture() throws Exception {
        Object state = newState();

        assertTrue(needsRebuild(state, 7L, 24f));
        markPrepared(state, 7L, 24f);

        // Scene geometry may change every UI frame, but source/profile did not.
        assertFalse(needsRebuild(state, 7L, 24f));
        assertFalse(needsRebuild(state, 7L, 24f));
    }

    @Test
    public void sourceOrBlurProfileChangeInvalidatesReuse() throws Exception {
        Object state = newState();
        markPrepared(state, 7L, 24f);

        assertTrue(needsRebuild(state, 8L, 24f));
        assertTrue(needsRebuild(state, 7L, 18f));
    }
}
