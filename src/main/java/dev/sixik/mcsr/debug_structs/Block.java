package dev.sixik.mcsr.debug_structs;

public class Block {

    public final long id;

    public Block(long id) {
        this.id = id;
    }

    @Override
    public String toString() {
        return "[" + BlocksRegister.NAMES.get(id) + ", " + id + "]";
    }
}
