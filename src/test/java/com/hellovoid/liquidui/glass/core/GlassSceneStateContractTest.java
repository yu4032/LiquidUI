package com.hellovoid.liquidui.glass.core;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class GlassSceneStateContractTest {
    @Test
    public void publicationIsImmutableSortedAndMonotonic() {
        GlassSceneState state = new GlassSceneState();
        ArrayList<GlassNode> input = new ArrayList<>(List.of(
                node("front", 9),
                node("rear", 2)));

        GlassSceneSnapshot first = state.publish(input);
        input.clear();
        GlassSceneSnapshot second = state.publish(List.of(node("front", 9)));

        assertEquals(List.of("rear", "front"),
                first.nodes().stream().map(GlassNode::id).toList());
        assertEquals(1L, first.generation());
        assertEquals(2L, second.generation());
    }

    @Test
    public void invalidGeometryIsClampedAndNotDrawable() {
        GlassNode node = new GlassNode(
                "n", 4L,
                0f, 0f, -3f, 4f,
                -1f, -1f, -1f, -1f,
                4f, 0, GlassMaterialProfile.CARD);

        assertEquals(0f, node.width(), 0f);
        assertEquals(1f, node.opacity(), 0f);
        assertFalse(node.drawable());
    }

    private static GlassNode node(String id, int zOrder) {
        return new GlassNode(
                id, 1L,
                0f, 0f, 100f, 50f,
                12f, 12f, 12f, 12f,
                1f, zOrder, GlassMaterialProfile.CARD);
    }
}
