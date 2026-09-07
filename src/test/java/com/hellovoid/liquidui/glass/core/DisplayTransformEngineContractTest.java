package com.hellovoid.liquidui.glass.core;

import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class DisplayTransformEngineContractTest {
    @Test
    public void inverseBufferRotationPrecedesSurfaceTextureCrop() {
        float[] crop = affine(0.25f, 0f, 0.05f, 0f, -0.25f, 0.75f);

        // configRotation is ViewRoot's buffer transform hint: it describes how the buffer is
        // rotated to appear in the Window. Sampling travels Window -> buffer, so use its inverse.
        assertPoint(0, 0f, 0f, crop, 0.05f, 0.75f);
        assertPoint(1, 0f, 0f, crop, 0.30f, 0.75f);
        assertPoint(2, 0f, 0f, crop, 0.30f, 0.50f);
        assertPoint(3, 0f, 0f, crop, 0.05f, 0.50f);

        for (int rotation = 0; rotation < 4; rotation++) {
            DisplayTransform transform = DisplayTransformEngine.compose(
                    snapshot(rotation, crop, 7L, 11L));
            float[] center = transform.map(0.5f, 0.5f);
            assertEquals(0.175f, center[0], 0.0001f);
            assertEquals(0.625f, center[1], 0.0001f);
        }
    }

    @Test
    public void landscapeTransformHintsReverseVerticalSamplingDirection() {
        DisplayTransform clockwiseHint = DisplayTransformEngine.compose(
                snapshot(1, identity(), 7L, 11L));
        DisplayTransform counterClockwiseHint = DisplayTransformEngine.compose(
                snapshot(3, identity(), 7L, 11L));

        // ROTATE_90 displays buffer bottom-right at Window bottom-left, so reverse sampling maps
        // Window bottom-left back to buffer bottom-right. ROTATE_270 is the opposite mapping.
        assertMapped(clockwiseHint, 0f, 0f, 1f, 0f);
        assertMapped(counterClockwiseHint, 0f, 0f, 0f, 1f);
    }

    @Test
    public void hostGeometryIsComposedInWindowCoordinates() {
        DisplayTransformSnapshot snapshot = new DisplayTransformSnapshot(
                0, 0, 0,
                0, 0, 200, 100,
                50, 25, 100, 50,
                200, 100,
                identity(), 3L, 5L);

        DisplayTransform transform = DisplayTransformEngine.compose(snapshot);

        assertMapped(transform, 0f, 0f, 0.25f, 0.25f);
        assertMapped(transform, 1f, 1f, 0.75f, 0.75f);
    }

    @Test
    public void generationsAndMatrixOwnershipArePartOfValidity() {
        float[] matrix = identity();
        DisplayTransformSnapshot snapshot = snapshot(3, matrix, 7L, 11L);
        matrix[0] = 99f;

        DisplayTransform transform = DisplayTransformEngine.compose(snapshot);
        float[] returned = transform.windowUvToOes();
        returned[0] = 99f;

        assertTrue(transform.matches(7L, 11L));
        assertFalse(transform.matches(8L, 11L));
        assertFalse(transform.matches(7L, 12L));
        assertMapped(transform, 0f, 0f, 0f, 1f);
    }

    private static void assertPoint(
            int rotation, float x, float y, float[] matrix,
            float expectedX, float expectedY) {
        DisplayTransform transform = DisplayTransformEngine.compose(
                snapshot(rotation, matrix, 7L, 11L));
        assertMapped(transform, x, y, expectedX, expectedY);
        assertMapped(transform, 1f - x, 1f - y,
                0.35f - expectedX, 1.25f - expectedY);
    }

    private static void assertMapped(
            DisplayTransform transform, float x, float y,
            float expectedX, float expectedY) {
        float[] mapped = transform.map(x, y);
        assertEquals(expectedX, mapped[0], 0.0001f);
        assertEquals(expectedY, mapped[1], 0.0001f);
    }

    private static DisplayTransformSnapshot snapshot(
            int rotation, float[] matrix, long rootGeneration, long producerGeneration) {
        return new DisplayTransformSnapshot(
                0, rotation, rotation,
                0, 0, 100, 100,
                0, 0, 100, 100,
                100, 100,
                matrix, rootGeneration, producerGeneration);
    }

    private static float[] identity() {
        return affine(1f, 0f, 0f, 0f, 1f, 0f);
    }

    private static float[] affine(
            float xx, float xy, float tx,
            float yx, float yy, float ty) {
        return new float[]{
                xx, yx, 0f, 0f,
                xy, yy, 0f, 0f,
                0f, 0f, 1f, 0f,
                tx, ty, 0f, 1f
        };
    }
}
