package dev.sixik.mcsr;

import dev.sixik.mcsr.task_system.CoroutineScheduler;
import dev.sixik.mcsr.task_system.CoroutineTasks;

import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.concurrent.TimeUnit;

public class Main {

    public static void main(String[] args) throws InterruptedException {
        CoroutineScheduler scheduler = new CoroutineScheduler(
                2,
                TimeUnit.MICROSECONDS.toNanos(500)
        );

        final int[] genes = new int[]{-150, -200, -180, -250, -100};
        final Random random = new Random();

        var handle = scheduler.submit(
            CoroutineTasks.<int[], int[], String>builder(genes)
                .next(state -> {
                    boolean anyReachedGoal = false;

                    for (int i = 0; i < state.length; i++) {
                        state[i] += random.nextInt(3);

                        if ((state[i] / 2.65f) >= 0.0) {
                            anyReachedGoal = true;
                        }
                    }

                    if (anyReachedGoal) {
                        return new CoroutineScheduler.Step.Done<>("Evolution Complete!");
                    }

                    return new CoroutineScheduler.Step.Yield<>(state.clone());
                })
                .build(),
            10
        );
        handle.cancel();

        int i = 0;
        while (true) {
            final boolean hanlder_done = handle.isDone();

            if (hanlder_done) {
                break;
            }

            if (i % 100 != 0) {
                System.out.println(Arrays.toString(handle.latestYield()));
            }
            i++;
        }

        System.out.println(Arrays.toString(handle.latestYield()) + " | " + handle.done().join());

//        System.out.println(handle.done().join());
    }
}