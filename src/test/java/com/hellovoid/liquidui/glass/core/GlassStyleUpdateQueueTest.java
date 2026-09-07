package com.hellovoid.liquidui.glass.core;

import com.hellovoid.liquidui.config.ConfigReader;
import com.hellovoid.liquidui.config.GlassParameter;
import com.hellovoid.liquidui.config.GlassStyleConfig;

import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.Assert.*;

public final class GlassStyleUpdateQueueTest {
    @Test
    public void burstUpdatesAreLastWriteWinsBehindOneScheduledDrain() {
        List<Runnable> scheduled = new ArrayList<>();
        List<GlassStyleUpdateQueue.Snapshot> delivered = new ArrayList<>();
        GlassStyleUpdateQueue queue = new GlassStyleUpdateQueue(
                scheduled::add,
                delivered::add);

        assertEquals(1L, queue.current().version());
        assertEquals(1.0f,
                queue.current().style().global().value(GlassParameter.BRIGHTNESS), 0.0001f);

        assertEquals(2L, queue.submit(styleWithBrightness(110)));
        assertEquals(3L, queue.submit(styleWithBrightness(125)));
        assertEquals(4L, queue.submit(styleWithBrightness(140)));

        assertEquals(1L, scheduled.size());
        assertTrue(delivered.isEmpty());
        assertEquals(4L, queue.current().version());
        assertEquals(1.40f,
                queue.current().style().global().value(GlassParameter.BRIGHTNESS), 0.0001f);

        scheduled.remove(0).run();

        assertEquals(1L, delivered.size());
        assertEquals(4L, delivered.get(0).version());
        assertEquals(1.40f,
                delivered.get(0).style().global().value(GlassParameter.BRIGHTNESS), 0.0001f);
    }

    @Test
    public void updatesArrivingAfterDrainScheduleExactlyOneFollowUp() {
        List<Runnable> scheduled = new ArrayList<>();
        List<GlassStyleUpdateQueue.Snapshot> delivered = new ArrayList<>();
        GlassStyleUpdateQueue queue = new GlassStyleUpdateQueue(
                scheduled::add,
                delivered::add);

        queue.submit(styleWithBrightness(110));
        scheduled.remove(0).run();
        assertEquals(1L, delivered.size());

        queue.submit(styleWithBrightness(120));
        queue.submit(styleWithBrightness(130));
        assertEquals(1L, scheduled.size());
        scheduled.remove(0).run();

        assertEquals(2L, delivered.size());
        assertEquals(4L, delivered.get(1).version());
        assertEquals(1.30f,
                delivered.get(1).style().global().value(GlassParameter.BRIGHTNESS), 0.0001f);
    }

    private static GlassStyleConfig styleWithBrightness(int rawBrightness) {
        return GlassStyleConfig.read(new ConfigReader(
                (name, fallback) -> fallback,
                (name, fallback) -> name.equals("glass_global_brightness")
                        ? rawBrightness : fallback));
    }
}
