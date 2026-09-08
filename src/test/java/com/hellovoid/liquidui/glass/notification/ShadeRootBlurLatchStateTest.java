package com.hellovoid.liquidui.glass.notification;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ShadeRootBlurLatchStateTest {
    @Test
    public void activeGlassDemandPrewarmsRootBlurBeforeVendorRatioCrossesZero() {
        ShadeRootBlurLatchState state = new ShadeRootBlurLatchState();

        state.observeVendorRatio(0f);
        assertFalse(state.effectiveActive());
        assertEquals(0f, state.effectiveRatio(), 0f);

        state.observeGlassDemand(true);
        assertTrue(state.effectiveActive());
        assertEquals(1f, state.effectiveRatio(), 0f);

        state.observeVendorRatio(0.35f);
        assertTrue(state.effectiveActive());
        assertEquals(1f, state.effectiveRatio(), 0f);

        // Once HyperOS itself owns the visible backdrop, dropping bounded glass demand must not
        // turn the root blur off underneath the still-open Shade.
        state.observeGlassDemand(false);
        assertTrue(state.effectiveActive());
        assertEquals(1f, state.effectiveRatio(), 0f);

        state.observeVendorRatio(0f);
        assertFalse(state.effectiveActive());
        assertEquals(0f, state.effectiveRatio(), 0f);
    }

    @Test
    public void glassDemandCanReopenAndCloseTheLatchWithoutVendorMotion() {
        ShadeRootBlurLatchState state = new ShadeRootBlurLatchState();
        state.observeVendorRatio(0f);

        state.observeGlassDemand(true);
        assertTrue(state.effectiveActive());
        state.observeGlassDemand(false);
        assertFalse(state.effectiveActive());
    }
}
