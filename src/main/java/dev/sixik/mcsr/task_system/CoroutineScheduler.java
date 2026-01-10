package dev.sixik.mcsr.task_system;

import java.util.concurrent.*;
import java.util.concurrent.atomic.*;
import java.util.*;

public final class CoroutineScheduler {

    public record Handle<Y, R>(
            long id,
            AtomicReference<Y> latest,
            CompletableFuture<R> done,
            CancellationToken token,
            AtomicInteger priority
    ) {
        public void cancel() {
            token.cancel();
        }

        public boolean isDone() {
            return done.isDone();
        }

        public boolean isCancelled() {
            return done.isCancelled();
        }

        public Y latestYield() {
            return latest.get();
        }

        public CompletableFuture<R> future() {
            return done;
        }

        public void setPriority(int p) {
            priority.set(p);
        }
    }

    public static final class CancellationToken {
        private final AtomicBoolean cancelled = new AtomicBoolean(false);

        public boolean isCancelled() {
            return cancelled.get();
        }

        public void cancel() {
            cancelled.set(true);
        }
    }

    public interface StepTask<Y, R> {
        Step<Y, R> step(TaskContext<Y> ctx) throws Exception;

        default void onYeld(TaskContext<Y> ctx) {}

        default void onDone(TaskContext<Y> ctx) {}
    }

    public sealed interface Step<Y, R> permits Step.Yield, Step.Done, Step.Continue {
        record Yield<Y, R>(Y value) implements Step<Y, R> {
        }

        record Done<Y, R>(R result) implements Step<Y, R> {
        }

        record Continue<Y, R>() implements Step<Y, R> {
        }
    }

    public static final class TaskContext<Y> {
        private final CancellationToken token;
        private final AtomicReference<Y> latest;

        TaskContext(CancellationToken token, AtomicReference<Y> latest) {
            this.token = token;
            this.latest = latest;
        }

        public void checkpoint() {
            if (token.isCancelled()) throw new CancellationException();
        }

        public void publish(Y value) {
            latest.set(value);
        }

        public AtomicReference<Y> latest() {
            return latest;
        }

        public boolean cancelled() {
            return token.isCancelled();
        }
    }

    private record Entry<Y, R>(long id, StepTask<Y, R> task, TaskContext<Y> ctx, CompletableFuture<R> done,
                               AtomicInteger priority) {
    }

    // очередь по приоритету (больше = важнее)
    private final PriorityBlockingQueue<Entry<?, ?>> queue = new PriorityBlockingQueue<>(
            64,
            (a, b) -> Integer.compare(b.priority.get(), a.priority.get())
    );

    private final ExecutorService workers;
    private final AtomicLong ids = new AtomicLong(1);
    private final long sliceNanos;

    public CoroutineScheduler(int threads, long sliceNanos) {
        if (threads <= 0) throw new IllegalArgumentException("threads <= 0");
        if (sliceNanos <= 0) throw new IllegalArgumentException("sliceNanos <= 0");

        this.sliceNanos = sliceNanos;
        this.workers = Executors.newFixedThreadPool(threads, r -> {
            Thread t = new Thread(r, "CoroutineWorker");
            t.setDaemon(true);
            return t;
        });

        for (int i = 0; i < threads; i++) workers.submit(this::workerLoop);
    }

    public ExecutorService getWorkers() {
        return workers;
    }

    public <Y, R> Handle<Y, R> submit(StepTask<Y, R> task, int priority) {
        Objects.requireNonNull(task, "task");

        long id = ids.getAndIncrement();
        var latest = new AtomicReference<Y>(null);
        var done = new CompletableFuture<R>();
        var token = new CancellationToken();
        var pr = new AtomicInteger(priority);
        var ctx = new TaskContext<>(token, latest);

        queue.add(new Entry<>(id, task, ctx, done, pr));
        return new Handle<>(id, latest, done, token, pr);
    }

    @SuppressWarnings("unchecked")
    private void workerLoop() {
        while (!Thread.currentThread().isInterrupted()) {
            Entry<Object, Object> e;
            try {
                e = (Entry<Object, Object>) queue.take();
            } catch (InterruptedException ie) {
                Thread.currentThread().interrupt();
                break; // shutdownNow() -> выходим без спама
            }

            if (e.done.isDone()) continue;

            // если отменили до начала кванта — сразу cancel future
            if (e.ctx.cancelled()) {
                e.done.cancel(false);
                continue;
            }

            boolean requeued = false;

            try {
                long deadline = System.nanoTime() + sliceNanos;

                final TaskContext<Object> ctx = e.ctx;

                while (System.nanoTime() < deadline) {
                    Step<Object, Object> s = e.task.step(ctx);

                    if (s instanceof Step.Done<Object, Object>(Object result)) {
                        e.done.complete(result);
                        e.task.onDone(ctx);
                        break;
                    }
                    if (s instanceof Step.Yield<Object, Object>(Object value)) {
                        ctx.publish(value);
                        queue.add(e);
                        requeued = true;
                        e.task.onYeld(ctx);
                        break;
                    }

                    // Continue
                    ctx.checkpoint();
                }
            } catch (CancellationException ce) {
                // кооперативная отмена — корректно завершаем future
                e.done.cancel(false);
                continue;
            } catch (Throwable t) {
                // любая ошибка задачи должна завершать future, иначе она "повиснет"
                e.done.completeExceptionally(t);
                continue;
            }

            // если квант закончился, задача не завершилась и не requeue-нулась в Yield — вернём в очередь
            if (!e.done.isDone() && !e.ctx.cancelled() && !requeued) {
                queue.add(e);
            } else if (!e.done.isDone() && e.ctx.cancelled()) {
                e.done.cancel(false);
            }
        }
    }

    public void shutdown() {
        try {
            if (!workers.awaitTermination(10, TimeUnit.SECONDS)) {
                workers.shutdownNow();
            }
        } catch (InterruptedException e) {
            throw new RuntimeException(e);
        }
    }
}
