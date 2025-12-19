package dev.sixik.mcsr.rework.paletted_container;

import com.mojang.serialization.DataResult;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;

import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.stream.LongStream;

public interface PalettedContainerRO<T> {
    T get(int i, int j, int k);

    void getAll(Consumer<T> consumer);

    void write(FriendlyByteBuf friendlyByteBuf);

    int getSerializedSize();

    boolean maybeHas(Predicate<T> predicate);

    void count(PalettedContainer.CountConsumer<T> countConsumer);

    PalettedContainer<T> recreate();

    PalettedContainerRO.PackedData<T> pack(IdMap<T> idMap, PalettedContainer.Strategy strategy);

    public record PackedData<T>(List<T> paletteEntries, Optional<LongStream> storage) {
    }

    public interface Unpacker<T, C extends PalettedContainerRO<T>> {
        DataResult<C> read(IdMap<T> idMap, PalettedContainer.Strategy strategy, PalettedContainerRO.PackedData<T> packedData);
    }
}
