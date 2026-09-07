package com.hellovoid.liquidui.glass.core;

import static org.junit.Assert.assertEquals;

import org.junit.Test;

public final class BackdropMappingContractTest {
    @Test
    public void fullCoveragePreservesWindowRelativeUv() {
        BackdropMapping.Result result = BackdropMapping.compute(
                100, 200, 400, 300,
                0, 0, 1000, 800);

        assertEquals(BackdropMapping.Coverage.FULL, result.coverage);
        assertEquals(0.1f, result.backdropX, 0.0001f);
        assertEquals(0.375f, result.backdropY, 0.0001f);
        assertEquals(0.4f, result.backdropW, 0.0001f);
        assertEquals(0.375f, result.backdropH, 0.0001f);
        assertEquals(0f, result.validLeft, 0.0001f);
        assertEquals(0f, result.validBottom, 0.0001f);
        assertEquals(1f, result.validRight, 0.0001f);
        assertEquals(1f, result.validTop, 0.0001f);
    }

    @Test
    public void partialCoverageUsesBottomLeftLocalCoordinates() {
        BackdropMapping.Result result = BackdropMapping.compute(
                -50, -20, 200, 100,
                0, 0, 300, 300);

        assertEquals(BackdropMapping.Coverage.PARTIAL, result.coverage);
        assertEquals(0.25f, result.validLeft, 0.0001f);
        assertEquals(0f, result.validBottom, 0.0001f);
        assertEquals(1f, result.validRight, 0.0001f);
        assertEquals(0.8f, result.validTop, 0.0001f);
    }

    @Test
    public void disjointWindowIsOutside() {
        BackdropMapping.Result result = BackdropMapping.compute(
                500, 500, 100, 100,
                0, 0, 300, 300);
        assertEquals(BackdropMapping.Coverage.OUTSIDE, result.coverage);
    }
}
