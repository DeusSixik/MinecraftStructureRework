package dev.sixik.mcsr.rework.paletted_container;

public class MissingPaletteEntryException extends RuntimeException {
    public MissingPaletteEntryException(int i) {
        super("Missing Palette entry for index " + i + ".");
    }
}
