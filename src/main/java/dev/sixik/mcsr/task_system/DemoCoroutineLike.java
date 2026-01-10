package dev.sixik.mcsr.task_system;

import java.util.concurrent.TimeUnit;
import java.util.SplittableRandom;
import java.util.concurrent.CancellationException;
import java.util.concurrent.CompletionException;

public class DemoCoroutineLike {

    // Что будем "стримить" в main thread
    public record PiProgress(long done, long total, long inside, double piEstimate) {}

    // Финальный результат
    public record PiResult(long total, long inside, double piEstimate) {}

    // Тяжёлая задача, которая делает много вычислений батчами
    public static final class MonteCarloPiTask implements CoroutineScheduler.StepTask<PiProgress, PiResult> {
        private final long totalIterations;
        private final int batchSize;
        private final SplittableRandom rnd;

        private long done = 0;
        private long inside = 0;

        // Можно делать yield не каждый step, а например раз в N step'ов
        private int stepsSinceYield = 0;
        private final int yieldEverySteps;

        public MonteCarloPiTask(long totalIterations, int batchSize, int yieldEverySteps, long seed) {
            this.totalIterations = totalIterations;
            this.batchSize = batchSize;
            this.yieldEverySteps = Math.max(1, yieldEverySteps);
            this.rnd = new SplittableRandom(seed);
        }

        @Override
        public CoroutineScheduler.Step<PiProgress, PiResult> step(CoroutineScheduler.TaskContext<PiProgress> ctx) {
            ctx.checkpoint();

            long remaining = totalIterations - done;
            int iters = (int) Math.min(remaining, batchSize);

            // Тяжёлая часть: много случайных точек
            for (int i = 0; i < iters; i++) {
                // периодически можно проверять cancel, если батч огромный
                // (но не делай это слишком часто, иначе overhead)
                if ((i & 8191) == 0) ctx.checkpoint();

                double x = rnd.nextDouble();
                double y = rnd.nextDouble();
                if (x * x + y * y <= 1.0) inside++;
            }

            done += iters;

            double pi = 4.0 * ((double) inside / (double) done);

            // Если закончили — Done
            if (done >= totalIterations) {
                return new CoroutineScheduler.Step.Done<>(new PiResult(totalIterations, inside, pi));
            }

            // Иногда отдаём прогресс
            stepsSinceYield++;
            if (stepsSinceYield >= yieldEverySteps) {
                stepsSinceYield = 0;
                return new CoroutineScheduler.Step.Yield<>(new PiProgress(done, totalIterations, inside, pi));
            }

            // Иначе продолжаем дальше в рамках кванта/следующего кванта
            return new CoroutineScheduler.Step.Continue<>();
        }
    }

    public static void main(String[] args) throws Exception {
        // Планировщик: 2 потока, квант 0.5ms (пример)
        CoroutineScheduler scheduler = new CoroutineScheduler(
                2,
                TimeUnit.MICROSECONDS.toNanos(500)
        );

        // Запускаем “очень тяжёлую” задачу:
        // totalIterations: 200 млн (подбирай под себя)
        // batchSize: 200_000 (сколько итераций за один step)
        // yieldEverySteps: 1 (yield после каждого step; можно поставить 3/5/10 чтобы реже спамить)
        var handle = scheduler.submit(
                new MonteCarloPiTask(
                        200_000_000_0L,
                        200,
                        1,
                        12345L
                ),
                10
        );

        // "MainThread tick loop" (симуляция 20 TPS)
        long lastPrintedDone = -1;
        for (int tick = 0; tick < 10_000; tick++) {
            // 1) забираем промежуточное (последнее)
            PiProgress p = handle.latestYield();
            if (p != null && p.done() != lastPrintedDone) {
                lastPrintedDone = p.done();
                double percent = (100.0 * p.done()) / p.total();
                System.out.printf(
                        "[tick=%d] progress: %.2f%%  done=%d  pi~%.6f%n",
                        tick, percent, p.done(), p.piEstimate()
                );
            }

            // 2) проверяем финал без блокировки
            if (handle.future().isDone()) {
                try {
                    PiResult r = handle.future().join(); // join не блокирует, т.к. уже done
                    System.out.printf(
                            "DONE! total=%d inside=%d pi=%.9f%n",
                            r.total(), r.inside(), r.piEstimate()
                    );
                } catch (CancellationException ce) {
                    System.out.println("CANCELLED");
                } catch (CompletionException ce) {
                    System.out.println("FAILED: " + ce.getCause());
                }
                break;
            }

            // Симулируем “тик”
            Thread.sleep(50);
        }

        scheduler.shutdown();
    }
}
