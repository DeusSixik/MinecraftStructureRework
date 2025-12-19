package dev.sixik.mcsr.debug_structs;

public class Position {

    public static final int SECTION_BITS = 15;

    public int x;
    public int y;
    public int z;

    public Position() {
        this(0, 0, 0);
    }

    public Position(int x, int y, int z) {
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public int toSectionX() {
        return x & SECTION_BITS;
    }

    public int toSectionY() {
        return y & SECTION_BITS;
    }

    public int toSectionZ() {
        return z & SECTION_BITS;
    }

    public static int toSection(int value) {
        return value & SECTION_BITS;
    }

    @Override
    public String toString() {
        return "Position{" +
                "x=" + toSectionX() +
                ", y=" + toSectionY() +
                ", z=" + toSectionZ() +
                '}';
    }
}
