package dev.sixik.mcsr.rework.paletted_container;

import javax.annotation.Nullable;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.Arrays;
import java.util.function.IntConsumer;

public class AtomicAlignedBitStorage implements BitStorage {
    private static final VarHandle VOLATILE_ACCESS = MethodHandles.arrayElementVarHandle(long[].class);

    private final long[] data;
    private final int bits;
    private final long mask;
    private final int size;
    private final int valuesPerLong;


    public AtomicAlignedBitStorage(int bits, int size, @Nullable long[] ls) {
        this.bits = bits;
        this.size = size;
        this.mask = (1L << bits) - 1L;
        this.valuesPerLong = 64 / (bits == 0 ? 1 : bits);
        int arrayLength = (size + valuesPerLong - 1) / valuesPerLong;
        if(ls != null) {
            if (ls.length != arrayLength) {
                throw new SimpleBitStorage.InitializationException("Invalid length given for storage, got: " + ls.length + " but expected: " + size);
            }

            this.data = ls;
        } else {
            this.data = new long[arrayLength];
        }

    }

    public AtomicAlignedBitStorage(int bits, int size, int[] is) {
        this(bits, size);
        int k = 0;

        int l;
        for (l = 0; l <= size - this.valuesPerLong; l += this.valuesPerLong) {
            long m = 0L;

            for (int n = this.valuesPerLong - 1; n >= 0; n--) {
                m <<= bits;
                m |= is[l + n] & this.mask;
            }

            this.data[k++] = m;
        }

        int o = size - l;
        if (o > 0) {
            long p = 0L;

            for (int q = o - 1; q >= 0; q--) {
                p <<= bits;
                p |= is[l + q] & this.mask;
            }

            this.data[k] = p;
        }
    }

    public AtomicAlignedBitStorage(int bits, int size) {
        this.bits = bits;
        this.size = size;
        this.mask = (1L << bits) - 1L;
        this.valuesPerLong = 64 / (bits == 0 ? 1 : bits);
        int arrayLength = (size + valuesPerLong - 1) / valuesPerLong;
        this.data = new long[arrayLength];
    }

    @Override
    public int getAndSet(int index, int value) {
        int longIdx = index / valuesPerLong;
        int shift = (index % valuesPerLong) * bits;
        long valueMask = mask << shift;
        long newValueFinal = (value & mask) << shift;

        while (true) {
            long oldLong = (long) VOLATILE_ACCESS.getVolatile(data, longIdx);
            long nextLong = (oldLong & ~valueMask) | newValueFinal;

            if (VOLATILE_ACCESS.compareAndSet(data, longIdx, oldLong, nextLong)) {
                // Возвращаем старое значение, которое было в этих битах
                return (int) ((oldLong >> shift) & mask);
            }
            Thread.onSpinWait();
        }
    }

    @Override
    public void set(int index, int value) {
        getAndSet(index, value);
    }

    @Override
    public int get(int index) {
        int longIdx = index / valuesPerLong;
        int shift = (index % valuesPerLong) * bits;
        long currentLong = (long) VOLATILE_ACCESS.getAcquire(data, longIdx);
        return (int) ((currentLong >> shift) & mask);
    }

    @Override
    public long[] getRaw() {
        long[] copy = new long[this.data.length];
        for (int i = 0; i < this.data.length; i++) {
            copy[i] = (long) VOLATILE_ACCESS.getAcquire(this.data, i);
        }
        return copy;
    }

    @Override
    public int getSize() {
        return this.size;
    }

    @Override
    public int getBits() {
        return this.bits;
    }

    @Override
    public void getAll(IntConsumer consumer) {
        for (int i = 0; i < this.size; i++) {
            consumer.accept(this.get(i));
        }
    }

    @Override
    public void unpack(int[] target) {
        for (int i = 0; i < this.size; i++) {
            target[i] = this.get(i);
        }
    }

    @Override
    public BitStorage copy() {
        // При копировании важно получить "чистый" снимок данных
        AtomicAlignedBitStorage copy = new AtomicAlignedBitStorage(this.bits, this.size);
        for (int i = 0; i < data.length; i++) {
            long val = (long) VOLATILE_ACCESS.getAcquire(data, i);
            copy.data[i] = val;
        }
        return copy;
    }
}
