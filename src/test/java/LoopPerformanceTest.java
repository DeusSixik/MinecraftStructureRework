import it.unimi.dsi.fastutil.objects.ObjectArrayList;
import org.junit.jupiter.api.*;

import java.util.List;
import java.util.Random;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LoopPerformanceTest {

    private static final int SIZE = 10_000_000;
    private int[] array;
    private List<Integer> list;

    @BeforeAll
    void setup() {
        System.out.println("Generating data (" + SIZE + " elements)...");
        array = new int[SIZE];
        list = new ObjectArrayList<>(SIZE);
        Random rand = new Random();

        for (int i = 0; i < SIZE; i++) {
            int val = rand.nextInt(100);
            array[i] = val;
            list.add(val);
        }
        System.out.println("Data generated.\n");

        // Warmup (прогрев JIT)
        System.out.println("Warming up...");
        for (int i = 0; i < 5; i++) {
            testArrayForLoop();
            testArrayForEach();
            testListForLoop();
            testListForEach();
            testWhileLoop();
            testReverseWhile();
        }
        System.out.println("Warmup done. Running tests...\n");
    }

    // --- ARRAY TESTS ---

    @Test
    @Order(1)
    void testArrayForLoop() {
        long start = System.nanoTime();

        long sum = 0;
        for (int i = 0; i < array.length; i++) {
            sum += array[i];
        }

        printResult("Array For(i)", start, sum);
    }

    @Test
    @Order(2)
    void testArrayForEach() {
        long start = System.nanoTime();

        long sum = 0;

        for (int val : array) {
            sum += val;
        }

        printResult("Array Foreach", start, sum);
    }

    // --- LIST TESTS ---

    @Test
    @Order(3)
    void testListForLoop() {
        long start = System.nanoTime();

        long sum = 0;
        int size = list.size(); // Выносим size из условия цикла для чистоты
        for (int i = 0; i < size; i++) {
            sum += list.get(i); // Autounboxing Integer -> int
        }

        printResult("List For(i)", start, sum);
    }

    @Test
    @Order(4)
    void testListForEach() {
        long start = System.nanoTime();

        long sum = 0;

        for (Integer val : list) { // Iterator + Autounboxing
            sum += val;
        }

        printResult("List Foreach", start, sum);
    }

    @Test
    @Order(5)
    void testWhileLoop() {
        long start = System.nanoTime();
        long sum = 0;
        int i = 0;
        int size = list.size();

        // Стандартный while
        while (i < size) {
            sum += list.get(i);
            i++;
        }
        printResult("List While", start, sum);
    }

    @Test
    @Order(6)
    void testReverseWhile() {
        long start = System.nanoTime();
        long sum = 0;
        int i = list.size() - 1;

        // Обратный while (сравнение с 0 процессору "нравится" чуть больше)
        while (i >= 0) {
            sum += list.get(i);
            i--;
        }
        printResult("List While(Rev)", start, sum);
    }

    private void printResult(String name, long startNano, long sum) {
        long durationNs = System.nanoTime() - startNano;
        System.out.printf("%-15s: %6d µs (Sum: %d)%n", name, durationNs / 1000, sum);
    }
}
