package dev.sixik.mcsr.rework.paletted_container;

import com.mojang.serialization.Codec;
import com.mojang.serialization.DataResult;
import com.mojang.serialization.codecs.RecordCodecBuilder;
import dev.sixik.mcsr.debug_structs.Position;
import it.unimi.dsi.fastutil.ints.Int2IntOpenHashMap;
import it.unimi.dsi.fastutil.ints.IntArraySet;
import it.unimi.dsi.fastutil.ints.IntSet;
import net.minecraft.core.IdMap;
import net.minecraft.network.FriendlyByteBuf;
import net.minecraft.network.VarInt;
import net.minecraft.util.ExtraCodecs;
import net.minecraft.util.Mth;
import net.minecraft.util.ThreadingDetector;

import javax.annotation.Nullable;
import java.util.Arrays;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.function.Consumer;
import java.util.function.IntUnaryOperator;
import java.util.function.Predicate;
import java.util.stream.LongStream;

public class PalettedContainer<T> implements PaletteResize<T>, PalettedContainerRO<T> {
    private static final int MIN_PALETTE_BITS = 0;
    private final PaletteResize<T> dummyPaletteResize = (i, objectx) -> 0;
    private final IdMap<T> registry;
    private volatile Data<T> data;
    private final Strategy strategy;
    private final ThreadingDetector threadingDetector = new ThreadingDetector("PalettedContainer");

    public void acquire() {
//        this.threadingDetector.checkAndLock();
    }

    public void release() {
//        this.threadingDetector.checkAndUnlock();
    }

    public PalettedContainer(IdMap<T> idMap, Strategy strategy, Configuration<T> configuration, BitStorage bitStorage, List<T> list) {
        this.registry = idMap;
        this.strategy = strategy;
        this.data = new Data<T>(configuration, bitStorage, configuration.factory().create(configuration.bits(), idMap, this, list));
    }

    private PalettedContainer(IdMap<T> idMap, Strategy strategy, Data<T> data) {
        this.registry = idMap;
        this.strategy = strategy;
        this.data = data;
    }

    public PalettedContainer(IdMap<T> idMap, T object, Strategy strategy) {
        this.strategy = strategy;
        this.registry = idMap;
        this.data = this.createOrReuseData(null, 0);
        this.data.palette.idFor(object);
    }

    private Data<T> createOrReuseData(@Nullable Data<T> data, int i) {
        Configuration<T> configuration = this.strategy.<T>getConfiguration(this.registry, i);
        return data != null && configuration.equals(data.configuration()) ? data : configuration.createData(this.registry, this, this.strategy.size());
    }

    public int onResize(int i, T object) {
        Data<T> data = this.data;
        Data<T> data2 = this.createOrReuseData(data, i);
        data2.copyFrom(data.palette, data.storage);
        this.data = data2;
        return data2.palette.idFor(object);
    }

    public T getAndSet(int i, int j, int k, T object) {
        this.acquire();

        Object var5;
        try {
            var5 = this.getAndSet(this.strategy.getIndex(i, j, k), object);
        } finally {
            this.release();
        }

        return (T)var5;
    }

    public T getAndSetUnchecked(int i, int j, int k, T object) {
        return (T)this.getAndSet(this.strategy.getIndex(i, j, k), object);
    }

    private T getAndSet(int i, T object) {
        int j = this.data.palette.idFor(object);
        int k = this.data.storage.getAndSet(i, j);
        return (T)this.data.palette.valueFor(k);
    }

    public void set(Position position, T object) {
        this.set(position.toSectionX(), position.toSectionY(), position.toSectionZ(), object);
    }

    public void set(int i, int j, int k, T object) {
        this.acquire();

        try {
            this.set(this.strategy.getIndex(i, j, k), object);
        } finally {
            this.release();
        }

    }

    private void set(int i, T object) {
        int j = this.data.palette.idFor(object);
        this.data.storage.set(i, j);
    }

    public T get(Position position) {
        return get(position.toSectionX(), position.toSectionY(), position.toSectionZ());
    }

    public T get(int i, int j, int k) {
        return this.get(this.strategy.getIndex(i, j, k));
    }

    protected T get(int i) {
        Data<T> data = this.data;
        return (T)data.palette.valueFor(data.storage.get(i));
    }

    public void getAll(Consumer<T> consumer) {
        Palette<T> palette = this.data.palette();
        IntSet intSet = new IntArraySet();
        BitStorage var10000 = this.data.storage;
        Objects.requireNonNull(intSet);
        var10000.getAll(intSet::add);
        intSet.forEach((i) -> consumer.accept(palette.valueFor(i)));
    }

    public void read(FriendlyByteBuf friendlyByteBuf) {
        this.acquire();

        try {
            int i = friendlyByteBuf.readByte();
            Data<T> data = this.createOrReuseData(this.data, i);
            data.palette.read(friendlyByteBuf);
            friendlyByteBuf.readLongArray(data.storage.getRaw());
            this.data = data;
        } finally {
            this.release();
        }

    }

    public void write(FriendlyByteBuf friendlyByteBuf) {
        this.acquire();

        try {
            this.data.write(friendlyByteBuf);
        } finally {
            this.release();
        }

    }

    private static <T> DataResult<PalettedContainer<T>> unpack(IdMap<T> idMap, Strategy strategy, PalettedContainerRO.PackedData<T> packedData) {
        List<T> list = packedData.paletteEntries();
        int i = strategy.size();
        int j = strategy.calculateBitsForSerialization(idMap, list.size());
        Configuration<T> configuration = strategy.<T>getConfiguration(idMap, j);
        BitStorage bitStorage;
        if (j == 0) {
            bitStorage = new ZeroBitStorage(i);
        } else {
            Optional<LongStream> optional = packedData.storage();
            if (optional.isEmpty()) {
                return DataResult.error(() -> "Missing values for non-zero storage");
            }

            long[] ls = ((LongStream)optional.get()).toArray();

            try {
                if (configuration.factory() == PalettedContainer.Strategy.GLOBAL_PALETTE_FACTORY) {
                    Palette<T> palette = new HashMapPalette(idMap, j, (ix, object) -> 0, list);
                    SimpleBitStorage simpleBitStorage = new SimpleBitStorage(j, i, ls);
                    int[] is = new int[i];
                    simpleBitStorage.unpack(is);
                    swapPalette(is, (ix) -> idMap.getId(palette.valueFor(ix)));
                    bitStorage = new SimpleBitStorage(configuration.bits(), i, is);
                } else {
                    bitStorage = new SimpleBitStorage(configuration.bits(), i, ls);
                }
            } catch (SimpleBitStorage.InitializationException initializationException) {
                return DataResult.error(() -> "Failed to read PalettedContainer: " + initializationException.getMessage());
            }
        }

        return DataResult.success(new PalettedContainer(idMap, strategy, configuration, bitStorage, list));
    }

    public PalettedContainerRO.PackedData<T> pack(IdMap<T> idMap, Strategy strategy) {
        this.acquire();

        PalettedContainerRO.PackedData var12;
        try {
            HashMapPalette<T> hashMapPalette = new HashMapPalette(idMap, this.data.storage.getBits(), this.dummyPaletteResize);
            int i = strategy.size();
            int[] is = new int[i];
            this.data.storage.unpack(is);
            swapPalette(is, (ix) -> hashMapPalette.idFor(this.data.palette.valueFor(ix)));
            int j = strategy.calculateBitsForSerialization(idMap, hashMapPalette.getSize());
            Optional<LongStream> optional;
            if (j != 0) {
                SimpleBitStorage simpleBitStorage = new SimpleBitStorage(j, i, is);
                optional = Optional.of(Arrays.stream(simpleBitStorage.getRaw()));
            } else {
                optional = Optional.empty();
            }

            var12 = new PalettedContainerRO.PackedData(hashMapPalette.getEntries(), optional);
        } finally {
            this.release();
        }

        return var12;
    }

    private static <T> void swapPalette(int[] is, IntUnaryOperator intUnaryOperator) {
        int i = -1;
        int j = -1;

        for(int k = 0; k < is.length; ++k) {
            int l = is[k];
            if (l != i) {
                i = l;
                j = intUnaryOperator.applyAsInt(l);
            }

            is[k] = j;
        }

    }

    public int getSerializedSize() {
        return this.data.getSerializedSize();
    }

    public boolean maybeHas(Predicate<T> predicate) {
        return this.data.palette.maybeHas(predicate);
    }

    public PalettedContainer<T> copy() {
        return new PalettedContainer<T>(this.registry, this.strategy, this.data.copy());
    }

    public PalettedContainer<T> recreate() {
        return new PalettedContainer<T>(this.registry, this.data.palette.valueFor(0), this.strategy);
    }

    public void count(CountConsumer<T> countConsumer) {
        if (this.data.palette.getSize() == 1) {
            countConsumer.accept(this.data.palette.valueFor(0), this.data.storage.getSize());
        } else {
            Int2IntOpenHashMap int2IntOpenHashMap = new Int2IntOpenHashMap();
            this.data.storage.getAll((i) -> int2IntOpenHashMap.addTo(i, 1));
            int2IntOpenHashMap.int2IntEntrySet().forEach((entry) -> countConsumer.accept(this.data.palette.valueFor(entry.getIntKey()), entry.getIntValue()));
        }
    }

    static record Configuration<T>(Palette.Factory factory, int bits) {
        public Data<T> createData(IdMap<T> idMap, PaletteResize<T> paletteResize, int i) {
            BitStorage bitStorage = (BitStorage)(this.bits == 0 ? new ZeroBitStorage(i) : new SimpleBitStorage(this.bits, i));
            Palette<T> palette = this.factory.create(this.bits, idMap, paletteResize, List.of());
            return new Data<T>(this, bitStorage, palette);
        }
    }

    record Data<T>(PalettedContainer.Configuration<T> configuration, BitStorage storage, Palette<T> palette) {

        public void copyFrom(Palette<T> palette, BitStorage bitStorage) {
            for (int i = 0; i < bitStorage.getSize(); i++) {
                T object = palette.valueFor(bitStorage.get(i));
                this.storage.set(i, this.palette.idFor(object));
            }
        }

        public int getSerializedSize() {
            return 1 + this.palette.getSerializedSize() + VarInt.getByteSize(this.storage.getRaw().length) + this.storage.getRaw().length * 8;
        }

        public void write(FriendlyByteBuf friendlyByteBuf) {
            friendlyByteBuf.writeByte(this.storage.getBits());
            this.palette.write(friendlyByteBuf);
            friendlyByteBuf.writeLongArray(this.storage.getRaw());
        }

        public PalettedContainer.Data<T> copy() {
            return new PalettedContainer.Data<>(this.configuration, this.storage.copy(), this.palette.copy());
        }
    }

    public abstract static class Strategy {
        public static final Palette.Factory SINGLE_VALUE_PALETTE_FACTORY = SingleValuePalette::create;
        public static final Palette.Factory LINEAR_PALETTE_FACTORY = LinearPalette::create;
        public static final Palette.Factory HASHMAP_PALETTE_FACTORY = HashMapPalette::create;
        static final Palette.Factory GLOBAL_PALETTE_FACTORY = GlobalPalette::create;
        public static final Strategy SECTION_STATES = new Strategy(4) {
            public <A> Configuration<A> getConfiguration(IdMap<A> idMap, int i) {
                Configuration var10000;
                switch (i) {
                    case 0:
                        var10000 = new Configuration(SINGLE_VALUE_PALETTE_FACTORY, i);
                        break;
                    case 1:
                    case 2:
                    case 3:
                    case 4:
                        var10000 = new Configuration(LINEAR_PALETTE_FACTORY, 4);
                        break;
                    case 5:
                    case 6:
                    case 7:
                    case 8:
                        var10000 = new Configuration(HASHMAP_PALETTE_FACTORY, i);
                        break;
                    default:
                        var10000 = new Configuration(PalettedContainer.Strategy.GLOBAL_PALETTE_FACTORY, Mth.ceillog2(idMap.size()));
                }

                return var10000;
            }
        };
        public static final Strategy SECTION_BIOMES = new Strategy(2) {
            public <A> Configuration<A> getConfiguration(IdMap<A> idMap, int i) {
                Configuration var10000;
                switch (i) {
                    case 0:
                        var10000 = new Configuration(SINGLE_VALUE_PALETTE_FACTORY, i);
                        break;
                    case 1:
                    case 2:
                    case 3:
                        var10000 = new Configuration(LINEAR_PALETTE_FACTORY, i);
                        break;
                    default:
                        var10000 = new Configuration(PalettedContainer.Strategy.GLOBAL_PALETTE_FACTORY, Mth.ceillog2(idMap.size()));
                }

                return var10000;
            }
        };
        private final int sizeBits;

        Strategy(int i) {
            this.sizeBits = i;
        }

        public int size() {
            return 1 << this.sizeBits * 3;
        }

        public int getIndex(int i, int j, int k) {
            return (j << this.sizeBits | k) << this.sizeBits | i;
        }

        public abstract <A> Configuration<A> getConfiguration(IdMap<A> idMap, int i);

        <A> int calculateBitsForSerialization(IdMap<A> idMap, int i) {
            int j = Mth.ceillog2(i);
            Configuration<A> configuration = this.<A>getConfiguration(idMap, j);
            return configuration.factory() == GLOBAL_PALETTE_FACTORY ? j : configuration.bits();
        }
    }

    @FunctionalInterface
    public interface CountConsumer<T> {
        void accept(T object, int i);
    }
}
