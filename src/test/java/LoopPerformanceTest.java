import org.junit.jupiter.api.*;

import java.util.*;
import java.util.concurrent.atomic.AtomicLong;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
public class LoopPerformanceTest {

    private static final int SIZE = 9_000_000; // Поправил опечатку в числе (было 90_000_00)
    private int[] array;
    private List<Integer> list;

    @BeforeAll
    void setup() {
        System.out.println("Generating data (" + SIZE + " elements)...");
        array = new int[SIZE];
        list = new ArrayList<>(SIZE);
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

            // Новые тесты в прогреве
            testArrayStreamForEach();
            testArrayStreamFilterForEach();
            testListStreamForEach();
            testListStreamFilterForEach();
        }
        System.out.println("Warmup done. Running tests...\n");
    }

    // --- EXISTING ARRAY TESTS ---

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

    // --- NEW STREAM TESTS (ARRAY) ---

    @Test
    @Order(3)
    void testArrayStreamForEach() {
        long start = System.nanoTime();

        // Используем массив из 1 элемента как эффективный mutable wrapper
        // Это быстрее AtomicLong, так как нет overhead на volatile/CAS операции
        long[] sum = new long[1];

        Arrays.stream(array).forEach(val -> sum[0] += val);

        printResult("Arr Stream.each", start, sum[0]);
    }

    @Test
    @Order(4)
    void testArrayStreamFilterForEach() {
        long start = System.nanoTime();
        long[] sum = new long[1];

        // Фильтруем четные числа
        Arrays.stream(array)
                .filter(val -> val % 2 == 0)
                .forEach(val -> sum[0] += val);

        printResult("Arr Strm Fltr", start, sum[0]);
    }

    // --- EXISTING LIST TESTS ---

    @Test
    @Order(5)
    void testListForLoop() {
        long start = System.nanoTime();
        long sum = 0;
        int size = list.size();
        for (int i = 0; i < size; i++) {
            sum += list.get(i);
        }
        printResult("List For(i)", start, sum);
    }

    @Test
    @Order(6)
    void testListForEach() {
        long start = System.nanoTime();
        // Примечание: AtomicLong значительно медленнее простого аккумулятора
        // из-за гарантий потокобезопасности, но оставлено как в оригинале.
        AtomicLong sum = new AtomicLong();
        list.forEach(sum::addAndGet);
        printResult("List Foreach", start, sum.get());
    }

    @Test
    @Order(7)
    void testWhileLoop() {
        long start = System.nanoTime();
        long sum = 0;
        int i = 0;
        int size = list.size();
        while (i < size) {
            sum += list.get(i);
            i++;
        }
        printResult("List While", start, sum);
    }

    @Test
    @Order(8)
    void testReverseWhile() {
        long start = System.nanoTime();
        long sum = 0;
        int i = list.size() - 1;
        while (i >= 0) {
            sum += list.get(i);
            i--;
        }
        printResult("List While(Rev)", start, sum);
    }

    // --- NEW STREAM TESTS (LIST) ---

    @Test
    @Order(9)
    void testListStreamForEach() {
        long start = System.nanoTime();
        long[] sum = new long[1];

        list.forEach(val -> sum[0] += val);

        printResult("List Stream.each", start, sum[0]);
    }

    @Test
    @Order(10)
    void testListStreamFilterForEach() {
        long start = System.nanoTime();
        long[] sum = new long[1];

        list.stream()
                .filter(val -> val % 2 == 0)
                .forEach(val -> sum[0] += val);

        printResult("List Strm Fltr", start, sum[0]);
    }

    private void printResult(String name, long startNano, long sum) {
        long durationNs = System.nanoTime() - startNano;
        System.out.printf("%-15s: %6d µs (Sum: %d)%n", name, durationNs / 1000, sum);
    }
}