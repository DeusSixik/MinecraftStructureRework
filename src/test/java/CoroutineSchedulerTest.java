import dev.sixik.mcsr.task_system.CoroutineScheduler;
import org.junit.jupiter.api.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

public class CoroutineSchedulerTest {

    private CoroutineScheduler scheduler;

    @BeforeEach
    void setUp() {
        scheduler = new CoroutineScheduler(2, TimeUnit.MILLISECONDS.toNanos(2));
    }

    @AfterEach
    void tearDown() {
        scheduler.shutdown();
    }

    @Test
    void testSimpleTaskCompletion() throws Exception {
        AtomicInteger steps = new AtomicInteger(0);

        CoroutineScheduler.Handle<Void, String> handle = scheduler.submit(ctx -> {
            if (steps.incrementAndGet() < 3) return new CoroutineScheduler.Step.Continue<>();
            return new CoroutineScheduler.Step.Done<>("Success");
        }, 1);

        String result = handle.future().get(1, TimeUnit.SECONDS);
        assertEquals("Success", result);
        assertEquals(3, steps.get());
    }

    @Test
    void testYieldValueUpdate() throws Exception {
        CoroutineScheduler.Handle<Integer, String> handle = scheduler.submit(ctx -> {
            Integer cur = ctx.latest().get();
            int next = (cur == null) ? 1 : cur + 1;

            if (next <= 5) return new CoroutineScheduler.Step.Yield<>(next);
            return new CoroutineScheduler.Step.Done<>("Finished");
        }, 1);

        handle.future().get(1, TimeUnit.SECONDS);
        assertEquals(5, handle.latestYield());
    }

    @Test
    void testTaskCancellation() throws Exception {
        CountDownLatch started = new CountDownLatch(1);

        var handle = scheduler.submit(ctx -> {
            started.countDown();
            // бесконечная задача, пока не отменим
            ctx.checkpoint();
            return new CoroutineScheduler.Step.Continue<>();
        }, 1);

        assertTrue(started.await(1, TimeUnit.SECONDS));

        handle.cancel();

        // CompletableFuture, помеченный как cancelled, кидает CancellationException (а не ExecutionException)
        assertThrows(CancellationException.class, () -> handle.future().get(1, TimeUnit.SECONDS));
        assertTrue(handle.future().isCancelled());
    }

    @Test
    void testPriorityExecutionDeterministic() throws Exception {
        // 2 воркера, чтобы можно было "занять" оба
        CoroutineScheduler sch = new CoroutineScheduler(2, TimeUnit.MILLISECONDS.toNanos(5));
        try {
            CountDownLatch blockersStarted = new CountDownLatch(2);
            CountDownLatch releaseBlocker1 = new CountDownLatch(1);
            CountDownLatch releaseBlocker2 = new CountDownLatch(1);

            // 2 блокера занимают оба воркера на step()
            sch.submit(ctx -> {
                blockersStarted.countDown();
                releaseBlocker1.await(1, TimeUnit.SECONDS);
                return new CoroutineScheduler.Step.Done<>((Void) null);
            }, 0);

            sch.submit(ctx -> {
                blockersStarted.countDown();
                releaseBlocker2.await(1, TimeUnit.SECONDS);
                return new CoroutineScheduler.Step.Done<>((Void) null);
            }, 0);

            assertTrue(blockersStarted.await(1, TimeUnit.SECONDS), "Workers were not blocked in time");

            AtomicReference<String> first = new AtomicReference<>(null);
            CountDownLatch bothDone = new CountDownLatch(2);

            var low = sch.submit(ctx -> {
                first.compareAndSet(null, "low");
                bothDone.countDown();
                return new CoroutineScheduler.Step.Done<>((Void) null);
            }, 1);

            var high = sch.submit(ctx -> {
                first.compareAndSet(null, "high");
                bothDone.countDown();
                return new CoroutineScheduler.Step.Done<>((Void) null);
            }, 100);

            // Освобождаем ОДНОГО воркера: он должен взять high (оба уже в очереди)
            releaseBlocker1.countDown();

            // Ждём пока хотя бы одна из задач (high/low) выполнится
            long end = System.nanoTime() + TimeUnit.SECONDS.toNanos(1);
            while (first.get() == null && System.nanoTime() < end) {
                Thread.onSpinWait();
            }

            assertEquals("high", first.get(), "High priority should run first");

            // Теперь отпускаем второго воркера и ждём завершения обеих задач
            releaseBlocker2.countDown();

            assertTrue(bothDone.await(1, TimeUnit.SECONDS), "Not all tasks finished");
            low.future().get(1, TimeUnit.SECONDS);
            high.future().get(1, TimeUnit.SECONDS);

        } finally {
            sch.shutdown();
        }
    }


    @Test
    void testExceptionHandling() {
        var handle = scheduler.submit(ctx -> {
            throw new RuntimeException("Crash!");
        }, 1);

        assertThrows(ExecutionException.class, () -> handle.future().get(1, TimeUnit.SECONDS));
        assertTrue(handle.future().isCompletedExceptionally());
    }
}
