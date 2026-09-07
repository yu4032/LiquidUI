package com.hellovoid.liquidui.glass.core;

import org.junit.Test;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;

public class FrameCoordinatorContractTest {
    @Test
    public void sourceAndSceneRequestsShareOneQueuedDrain() {
        QueuePoster poster = new QueuePoster();
        List<String> calls = new ArrayList<>();
        FrameCoordinator coordinator = new FrameCoordinator(
                poster,
                (source, scene) -> calls.add(source + ":" + scene));

        coordinator.requestSourceFrame();
        coordinator.requestScene();

        assertEquals(1L, poster.size());
        poster.runNext();
        assertEquals(List.of("true:true"), calls);
    }

    @Test
    public void requestsDuringDrawQueueOnlyOneFollowUp() {
        QueuePoster poster = new QueuePoster();
        AtomicReference<FrameCoordinator> reference = new AtomicReference<>();
        AtomicInteger calls = new AtomicInteger();
        FrameCoordinator coordinator = new FrameCoordinator(poster, (source, scene) -> {
            if (calls.getAndIncrement() == 0) {
                reference.get().requestScene();
                reference.get().requestScene();
            }
        });
        reference.set(coordinator);

        coordinator.requestSourceFrame();
        poster.runNext();

        assertEquals(1L, poster.size());
        poster.runNext();
        assertEquals(2L, calls.get());
        assertEquals(0L, poster.size());
    }

    @Test
    public void cancellationDropsPendingAndFutureRequests() {
        QueuePoster poster = new QueuePoster();
        AtomicInteger calls = new AtomicInteger();
        FrameCoordinator coordinator = new FrameCoordinator(
                poster,
                (source, scene) -> calls.incrementAndGet());

        coordinator.requestSourceFrame();
        coordinator.cancel();
        poster.runNext();
        coordinator.requestScene();

        assertEquals(0L, calls.get());
        assertEquals(0L, poster.size());
    }

    private static final class QueuePoster implements FrameCoordinator.Poster {
        private final Queue<Runnable> queue = new ArrayDeque<>();

        @Override
        public void post(Runnable command) {
            queue.add(command);
        }

        long size() {
            return queue.size();
        }

        void runNext() {
            Runnable command = queue.poll();
            if (command != null) command.run();
        }
    }
}
