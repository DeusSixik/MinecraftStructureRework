import dev.sixik.mcsr.debug_structs.Block;
import dev.sixik.mcsr.debug_structs.BlocksRegister;
import dev.sixik.mcsr.debug_structs.Position;
import dev.sixik.mcsr.rework.paletted_container.PalettedContainer;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import static dev.sixik.mcsr.debug_structs.BlocksRegister.BLOCKS;

public class PalettedContainerTest {

    @Test
    public void testHighContentionOneReaderOneWriter() {
        testHighContention(1);
    }

    @Test
    public void testHighContentionReadsOneFourthOfTheProcessorOneWriter() {
        testHighContention(Runtime.getRuntime().availableProcessors() / 4);
    }

    @Test
    public void testHighContentionReadsHalfOfTheProcessorOneWriter() {
        testHighContention(Runtime.getRuntime().availableProcessors() / 2);
    }

    @Test
    public void testHighContentionReadsAllOfTheProcessorOneWriter() {
        testHighContention(Runtime.getRuntime().availableProcessors());
    }

    private static void testHighContention(int readers) {
        PalettedContainer<Block> container = new PalettedContainer<>(BLOCKS, BlocksRegister.AIR, PalettedContainer.Strategy.SECTION_STATES);
        ExecutorService executor = Executors.newFixedThreadPool(readers + 1);

        AtomicLong readOps = new AtomicLong(0);
        AtomicLong writeOps = new AtomicLong(0);
        AtomicBoolean running = new AtomicBoolean(true);

        Position targetPos = new Position(8, 8, 8);
        Block blockA = BlocksRegister.AIR;
        Block blockB = BlocksRegister.STONE;

        System.out.println("----Test High Contention | 1 writer | " + readers + " readers----");

        executor.submit(() -> {
            while (running.get()) {
                container.set(targetPos, (writeOps.get() % 2 == 0) ? blockA : blockB);
                writeOps.incrementAndGet();
                Thread.onSpinWait();
            }
        });

        for (int i = 0; i < readers; i++) {
            executor.submit(() -> {
                while (running.get()) {
                    Block b = container.get(targetPos);
                    if (b == null) System.err.println("Error: block null!");
                    readOps.incrementAndGet();
                }
            });
        }

        try {
            for (int i = 0; i < 5; i++) {
                Thread.sleep(1000);
                long r = readOps.getAndSet(0);
                long w = writeOps.getAndSet(0);
                System.out.printf("Second %d: Reads: %.2f million/sec | Write: %.2f thousand/sec%n",
                        i + 1, r / 1e6, w / 1e3);
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        running.set(false);
        executor.shutdown();
        System.out.println("----Test High Contention End----");
    }

    @Test
    public void testPerformance() {
        PalettedContainer<Block> container = new PalettedContainer<>(BLOCKS, BlocksRegister.AIR, PalettedContainer.Strategy.SECTION_STATES);
        runPerformanceTest(container);
    }

    @Test
    public void testChaosOperation() {
        PalettedContainer<Block> container = new PalettedContainer<>(BLOCKS, BlocksRegister.AIR, PalettedContainer.Strategy.SECTION_STATES);
        runChaosTest(container);
    }

    @Test
    public void testBitInterference() {

        System.out.println("----Test Bit Interference Start----");

        PalettedContainer<Block> container = new PalettedContainer<>(BLOCKS, BlocksRegister.AIR, PalettedContainer.Strategy.SECTION_STATES);
        Block red = new Block(1);
        Block blue = new Block(2);
        int iterations = 100_000;

        CompletableFuture<Void> t1 = CompletableFuture.runAsync(() -> {
            for (int i = 0; i < iterations; i++) container.set(new Position(0, 0, 0), red);
        });
        CompletableFuture<Void> t2 = CompletableFuture.runAsync(() -> {
            for (int i = 0; i < iterations; i++) container.set(new Position(1, 0, 0), blue);
        });

        CompletableFuture.allOf(t1, t2).join();

        if (container.get(new Position(0, 0, 0)) != red || container.get(new Position(1, 0, 0)) != blue) {
            throw new RuntimeException("Race Condition в BitStorage! Neighboring blocks damaged each other.");
        } else System.out.println("----Test Bit Interference End----");
    }

    @Test
    public void testReadDuringResize() {
        PalettedContainer<Block> container = new PalettedContainer<>(BLOCKS, BlocksRegister.AIR, PalettedContainer.Strategy.SECTION_STATES);
        Block stone = new Block(5);
        container.set(new Position(0, 0, 0), stone);

        AtomicBoolean running = new AtomicBoolean(true);

        System.out.println("----Test Read During Resize Start----");

        CompletableFuture<Void> reader = CompletableFuture.runAsync(() -> {
            while (running.get()) {
                Block b = container.get(new Position(0, 0, 0));
                if (b != stone) {
                    throw new RuntimeException("Consistency is broken! Block received: " + b);
                }
            }
        });

        for (int i = 10; i < 300; i++) {
            container.set(new Position(1, 1, 1), new Block(i));
            try { Thread.sleep(1); } catch (InterruptedException e) {}
        }

        running.set(false);
        reader.join();
        System.out.println("----Test Read During Resize End----");
    }

    @Test
    public void testPaletteRace() {
        PalettedContainer<Block> container = new PalettedContainer<>(BLOCKS, BlocksRegister.AIR, PalettedContainer.Strategy.SECTION_STATES);
        int threads = 16;
        CompletableFuture<?>[] tasks = new CompletableFuture[threads];

        System.out.println("----Test Palette Race Start----");

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            tasks[t] = CompletableFuture.runAsync(() -> {
                for (int i = 0; i < 10; i++) {
                    Block uniqueBlock = new Block(1000 + (threadId * 10) + i);
                    container.set(new Position(threadId, i, 0), uniqueBlock);
                }
            });
        }
        CompletableFuture.allOf(tasks).join();

        for (int t = 0; t < threads; t++) {
            for (int i = 0; i < 10; i++) {
                Block b = container.get(new Position(t, i, 0));
                if (b.getId() != 1000 + (t * 10) + i) {
                    throw new RuntimeException("Error! The block is in position " + t + "," + i + " ruined: " + b.getId());
                }
            }
        }

        System.out.println("----Test Palette Race End----");
    }

    @Test
    public void runMinuteStressTest() {
        BlocksRegister.init();
        final PalettedContainer<Block> container = new PalettedContainer<>(BLOCKS, BlocksRegister.AIR, PalettedContainer.Strategy.SECTION_STATES);

        final int totalThreads = Runtime.getRuntime().availableProcessors();
        final int writeThreadsCount = Math.max(1, totalThreads / 2);
        final int readThreadsCount = Math.max(1, totalThreads - writeThreadsCount);

        final ExecutorService executor = Executors.newFixedThreadPool(totalThreads);
        final AtomicLong readOps = new AtomicLong(0);
        final AtomicLong writeOps = new AtomicLong(0);
        final AtomicBoolean running = new AtomicBoolean(true);

        final List<Long> readsHistory = new ArrayList<>();
        final List<Long> writesHistory = new ArrayList<>();

        System.out.println("---- Test Chaos Stress | " + writeThreadsCount + " writers | " + readThreadsCount + " readers ----");
        System.out.println("Time    | Reads/sec     | Writes/sec    | Palette");
        System.out.println("---------------------------------------------------------");

        // writer threads
        for (int i = 0; i < writeThreadsCount; i++) {
            executor.submit(() -> {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                while (running.get()) {
                    container.set(random.nextInt(16), random.nextInt(16), random.nextInt(16), BlocksRegister.getRandomBlock());
                    writeOps.incrementAndGet();
                    Thread.onSpinWait();
                }
            });
        }

        // read threads
        for (int i = 0; i < readThreadsCount; i++) {
            executor.submit(() -> {
                ThreadLocalRandom random = ThreadLocalRandom.current();
                while (running.get()) {
                    container.get(random.nextInt(16), random.nextInt(16), random.nextInt(16));
                    readOps.incrementAndGet();
                }
            });
        }

        long startTime = System.currentTimeMillis();
        try {
            while (System.currentTimeMillis() - startTime < 60_000) {
                Thread.sleep(1000);

                long r = readOps.getAndSet(0);
                long w = writeOps.getAndSet(0);

                readsHistory.add(r);
                writesHistory.add(w);

                long elapsed = (System.currentTimeMillis() - startTime) / 1000;
                System.out.printf("%02ds     | %6.2f M/s   | %6.2f K/s   | %d bits%n",
                        elapsed, r / 1e6, w / 1e3, container.getBits());
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        } finally {
            running.set(false);
            executor.shutdown();
        }

        // avg results
        double avgReads = readsHistory.stream().mapToLong(Long::longValue).average().orElse(0.0);
        double avgWrites = writesHistory.stream().mapToLong(Long::longValue).average().orElse(0.0);

        System.out.println("---------------------------------------------------------");
        System.out.printf("AVERAGE | %6.2f M/s   | %6.2f K/s   | %d bits%n",
                avgReads / 1e6, avgWrites / 1e3, container.getBits());
        System.out.println("---------------------------------------------------------");
        printPaletted(container, 16);
        System.out.println("---- Test Chaos Stress End ----");
    }

    private static void runChaosTest(PalettedContainer<Block> container) {
        int threadCount = Runtime.getRuntime().availableProcessors() * 2;
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicLong operations = new AtomicLong(0);

        System.out.println("----Test Stress Operations | " + threadCount + " threads... | Start----");

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                Random random = new Random();
                while (true) {
                    int x = random.nextInt(16);
                    int y = random.nextInt(16);
                    int z = random.nextInt(16);
                    Position pos = new Position(x, y, z);

                    if (random.nextBoolean()) {
                        Block randomBlock = BlocksRegister.getRandomBlock();
                        container.set(pos, randomBlock);
                    } else {
                        Block b = container.get(pos);
                        if (b == null) throw new IllegalStateException("Null block!");
                    }
                    operations.incrementAndGet();
                }
            });
        }

        for (int i = 0; i < 10; i++) {
            try { Thread.sleep(1000); } catch (InterruptedException e) {}
            System.out.println("Operations completed: " + operations.get());
        }

        executor.shutdownNow();
        System.out.println("----Test Stress Operations End----");

    }

    private static void runPerformanceTest(PalettedContainer<Block> container) {
        int iters = 10_000_000;
        Position pos = new Position(1, 2, 3);
        Block block = BlocksRegister.getRandomBlock();

        System.out.println("----Test Performance | Iterations count " + iters + " | Start----");

        long start = System.nanoTime();
        for (int i = 0; i < iters; i++) {
            container.get(pos);
        }
        long end = System.nanoTime();
        System.out.printf("Read: %.2f million op/sec%n", iters / ((end - start) / 1e9) / 1e6);

        start = System.nanoTime();
        for (int i = 0; i < iters; i++) {
            container.set(pos, block);
        }
        end = System.nanoTime();
        System.out.printf("Write: %.2f million op/sec%n", iters / ((end - start) / 1e9) / 1e6);

        System.out.println("----Test Performance End----");
    }

    private static void printPaletted(PalettedContainer<Block> container, int size) {
        Position position = new Position();

        for (int z = 0; z < size; z++) {
            position.z = z;
            System.out.println("\n=== Z = " + z + " ===");

            for (int y = size - 1; y >= 0; y--) {
                position.y = y;
                for (int x = 0; x < size; x++) {
                    position.x = x;

                    Block block = container.get(position);
                    System.out.print(block);
                }
                System.out.println();
            }
        }
    }
}
