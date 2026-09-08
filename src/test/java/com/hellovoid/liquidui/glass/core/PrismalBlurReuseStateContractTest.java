package com.hellovoid.liquidui.glass.core;

import org.junit.Test;

import java.lang.reflect.Constructor;
import java.lang.reflect.Method;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/** Regression contract: scene-only geometry updates reuse the already prepared blur texture. */
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

    private static void onBackdropPrepared(Object state, float blurRadius) throws Exception {
        Method method = state.getClass().getDeclaredMethod("onBackdropPrepared", float.class);
        method.setAccessible(true);
        method.invoke(state, blurRadius);
    }

    private static boolean needsRebuild(Object state, float blurRadius) throws Exception {
        Method method = state.getClass().getDeclaredMethod("needsRebuild", float.class);
        method.setAccessible(true);
        return (Boolean) method.invoke(state, blurRadius);
    }

    private static void markPrepared(Object state, float blurRadius) throws Exception {
        Method method = state.getClass().getDeclaredMethod("markPrepared", float.class);
        method.setAccessible(true);
        method.invoke(state, blurRadius);
    }

    @Test
    public void freshlyPreparedBaseBlurIsImmediatelyReusable() throws Exception {
        Object state = newState();
        onBackdropPrepared(state, 24f);

        assertFalse(needsRebuild(state, 24f));
        assertFalse(needsRebuild(state, 24f));
    }

    @Test
    public void geometryOnlyFramesReuseLastPreparedRadius() throws Exception {
        Object state = newState();
        onBackdropPrepared(state, 24f);
        markPrepared(state, 18f);

        assertFalse(needsRebuild(state, 18f));
        assertTrue(needsRebuild(state, 24f));
    }

    @Test
    public void newBackdropPreparationResetsActiveRadiusToBase() throws Exception {
        Object state = newState();
        onBackdropPrepared(state, 24f);
        markPrepared(state, 18f);
        onBackdropPrepared(state, 24f);

        assertFalse(needsRebuild(state, 24f));
        assertTrue(needsRebuild(state, 18f));
    }
}
