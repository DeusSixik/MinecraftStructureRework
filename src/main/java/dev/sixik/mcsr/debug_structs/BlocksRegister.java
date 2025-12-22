package dev.sixik.mcsr.debug_structs;

import net.minecraft.core.IdMapper;

import java.util.*;

public class BlocksRegister {

    public static IdMapper<Block> BLOCKS = new IdMapper<>(2000);
    public static Map<Long, String> NAMES = new HashMap<>();

    public static final Block AIR = register("air", 0);
    public static final Block DIRT = register("dirt", 1);
    public static final Block STONE = register("stone", 2);
    public static final Block BEDROCK = register("bedrock", 3);
    public static final Block WOOD = register("wood", 4);

    public static void init() {
        randomBlockGenerator(500);
    }

    public static Block getRandomBlock() {
        return BLOCKS.byId(new Random().nextInt(BLOCKS.size()));
    }

    private static int next = 0;

    public static Block getNext() {
        if (next >= BLOCKS.size()) next = 1;
        return BLOCKS.byId(next++);
    }

    public static void randomBlockGenerator(int count) {
        int size = BLOCKS.size();
        for (int i = 0; i < count; i++) {
            register(BlockNameGenerator.generateMinecraftStyleName(), size + i);
        }
    }

    private static Block register(String name, long id) {
        NAMES.put(id, name);
        final Block block = new Block(id);
        BLOCKS.add(block);
        return block;
    }

    public static class BlockNameGenerator {
        private static final String[] MATERIALS = {
                "stone", "dirt", "grass", "sand", "gravel", "clay", "snow", "ice",
                "wood", "log", "planks", "leaves", "wool", "glass", "brick", "concrete",
                "ore", "cobblestone", "mossy", "cracked", "polished", "chiseled", "smooth"
        };

        private static final String[] TYPES = {
                "block", "slab", "stairs", "wall", "fence", "gate", "door", "trapdoor",
                "pane", "pressure_plate", "button", "lantern", "torch", "candle",
                "pillar", "column", "bricks", "tiles", "shingles", "panel"
        };

        private static final String[] COLORS = {
                "white", "orange", "magenta", "light_blue", "yellow", "lime", "pink",
                "gray", "light_gray", "cyan", "purple", "blue", "brown", "green",
                "red", "black", "terracotta", "prismarine", "quartz"
        };

        private static final String[] MODIFIERS = {
                "dark", "light", "mossy", "cracked", "weathered", "oxidized", "waxed",
                "stripped", "carved", "engraved", "glowing", "shimmering", "ancient",
                "enchanted", "reinforced", "crystal", "void", "nether", "end"
        };

        private static final Random random = new Random();

        public static String generateMinecraftStyleName() {
            List<String> parts = new ArrayList<>();

            if (random.nextDouble() < 0.3) {
                parts.add(MODIFIERS[random.nextInt(MODIFIERS.length)]);
            }

            if (random.nextDouble() < 0.6) {
                parts.add(COLORS[random.nextInt(COLORS.length)]);
            }

            parts.add(MATERIALS[random.nextInt(MATERIALS.length)]);

            parts.add(TYPES[random.nextInt(TYPES.length)]);

            return String.join("_", parts);
        }
    }
}
