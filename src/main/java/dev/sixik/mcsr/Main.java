package dev.sixik.mcsr;

import dev.sixik.mcsr.debug_structs.Block;
import dev.sixik.mcsr.debug_structs.BlocksRegister;
import dev.sixik.mcsr.debug_structs.Position;
import dev.sixik.mcsr.rework.paletted_container.PalettedContainer;
import net.minecraft.core.IdMapper;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

public class Main {

    private static final IdMapper<Block> BLOCKS = new IdMapper<>();

    public static void main(String[] args) {
        BlocksRegister.init();

        PalettedContainer<Block> palettedContainer = new PalettedContainer<>(BLOCKS, new Block(0), PalettedContainer.Strategy.SECTION_STATES);

        final int paralel = 20;

        final int taskRepeat = 1;

        for (int f = 0; f < taskRepeat; f++) {
            CompletableFuture<Void>[] tasks = new CompletableFuture[paralel];

            for (int i = 0; i < paralel; i++) {
                final int offset = 1 + i;
                tasks[i] = CompletableFuture.runAsync(() -> {
                            fillToPaletted(palettedContainer, 15, s -> {
                                return BlocksRegister.getRandomBlock();
                            });
                        }
                );
            }

            CompletableFuture.allOf(tasks).join();
        }

        printPaletted(palettedContainer, 15);
        System.out.println(palettedContainer);
    }

    private static void fillToPaletted(PalettedContainer<Block> container, int size, Function<Position, @Nullable Block> setBlock) {
        Position position = new Position();

        for (int x = 0; x < size; x++) {
            position.x = x;
            for (int y = 0; y < size; y++) {
                position.y = y;
                for (int z = 0; z < size; z++) {
                    position.z = z;

                    final Block block = setBlock.apply(position);
                    if (block == null) continue;
                    container.set(position, block);
                }
            }
        }
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

    public static void testPaletteRace() {
        PalettedContainer<Block> container = new PalettedContainer<>(BLOCKS, BlocksRegister.AIR, PalettedContainer.Strategy.SECTION_STATES);
        int threads = 16;
        CompletableFuture<?>[] tasks = new CompletableFuture[threads];

        for (int t = 0; t < threads; t++) {
            final int threadId = t;
            tasks[t] = CompletableFuture.runAsync(() -> {
                // Каждый поток ставит блоки, которых ГАРАНТИРОВАННО нет в палитре
                // Это спровоцирует конкурентный onResize
                for (int i = 0; i < 10; i++) {
                    Block uniqueBlock = new Block(1000 + (threadId * 10) + i);
                    container.set(new Position(threadId, i, 0), uniqueBlock);
                }
            });
        }
        CompletableFuture.allOf(tasks).join();

        // Проверка: каждый блок должен быть на своем месте
        for (int t = 0; t < threads; t++) {
            for (int i = 0; i < 10; i++) {
                Block b = container.get(new Position(t, i, 0));
                if (b.getId() != 1000 + (t * 10) + i) {
                    System.err.println("Ошибка! Блок в позиции " + t + "," + i + " испорчен: " + b.getId());
                }
            }
        }
    }
}