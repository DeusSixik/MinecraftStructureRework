package dev.sixik.mcsr;

import dev.sixik.mcsr.debug_structs.Block;
import dev.sixik.mcsr.debug_structs.BlocksRegister;
import dev.sixik.mcsr.debug_structs.Position;
import dev.sixik.mcsr.rework.paletted_container.PalettedContainer;
import net.minecraft.core.IdMapper;
import org.checkerframework.checker.nullness.qual.Nullable;

import java.util.Random;
import java.util.concurrent.CompletableFuture;
import java.util.function.Function;

//TIP To <b>Run</b> code, press <shortcut actionId="Run"/> or
// click the <icon src="AllIcons.Actions.Execute"/> icon in the gutter.
public class Main {

    private static final IdMapper<Block> BLOCKS = new IdMapper<>();

    public static void main(String[] args) {
        BlocksRegister.init();

        PalettedContainer<Block> palettedContainer = new PalettedContainer<>(BLOCKS, new Block(0), PalettedContainer.Strategy.SECTION_STATES);

        final int paralel = Runtime.getRuntime().availableProcessors();

        int taskRepeat = 5000000;

        for (int f = 0; f < taskRepeat; f++) {
            CompletableFuture<Void>[] tasks = new CompletableFuture[paralel];

            for (int i = 0; i < paralel; i++) {
                final int offset = 1 + i;
                tasks[i] = CompletableFuture.runAsync(() -> {
                            fillToPaletted(palettedContainer, 15, s -> {
                                if(s.x % offset != 0 && s.y % offset != 0 && s.z % offset != 0)
                                    return BlocksRegister.getRandomBlock();
                                return null;
                            });
                        }
                );
            }

            CompletableFuture.allOf(tasks).join();
        }
        printPaletted(palettedContainer, 15);
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
                    // Выводим символ или ID блока
                    System.out.print(block);
                }
                System.out.println();
            }
        }
    }
}